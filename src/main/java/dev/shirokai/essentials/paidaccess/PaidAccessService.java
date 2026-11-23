package dev.shirokai.essentials.paidaccess;

import dev.shirokai.essentials.ShirokaiEssentials;
import dev.shirokai.essentials.persistence.Position;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.impactdev.impactor.api.economy.EconomyService;
import net.kyori.adventure.key.Key;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Paid timed access. Provider failures remain journaled and are never retried automatically. */
public final class PaidAccessService {
    private final ShirokaiEssentials app;
    private final Set<UUID> inFlight=new HashSet<>();
    private final Map<UUID,UUID> guardTokens=new HashMap<>();
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final GuardRegionAccess region=new GuardRegionAccess();
    private record Result(boolean successful,String reason){}
    private static final class Session {long remaining,last,checkpoint,retry;Session(long r,long now){remaining=r;last=now;checkpoint=now;}}

    public PaidAccessService(ShirokaiEssentials app){this.app=app;}
    public int status(ServerPlayerEntity player)throws IOException{var c=app.settings().paidAccess;long left=remaining(player.getUuid(),System.currentTimeMillis());return app.messages.send(player.getCommandSource(),"paid_access_status","price",c.price,"currency",c.currency,"warp",c.entranceWarp,"time",format(left));}

    public int enter(ServerPlayerEntity player)throws IOException{
        var source=player.getCommandSource();var cfg=app.settings().paidAccess;
        if(!cfg.enabled)return app.messages.error(source,"paid_access_disabled");
        if(!region.available())return app.messages.error(source,"paid_access_guard");
        var warp=app.data().warp(cfg.entranceWarp);if(warp==null)return app.messages.error(source,"missing");
        if(!region.contains(cfg.dimension,cfg.region,warp.position.x(),warp.position.y(),warp.position.z()))return app.messages.error(source,"paid_access_region");
        var data=app.data().player(player.getUuid());
        if(!data.pendingPaidAccess.isBlank())return app.messages.error(source,"paid_access_pending","id",data.pendingPaidAccess);
        if(inFlight.contains(player.getUuid()))return app.messages.error(source,"pending");
        long saved=remaining(player.getUuid(),System.currentTimeMillis());if(saved>0)return enterWithTime(player,warp.position,saved);
        BigDecimal price=new BigDecimal(cfg.price);if(price.signum()==0)return grantFree(player,warp.position);
        if(!FabricLoader.getInstance().isModLoaded("impactor"))return app.messages.error(source,"paid_access_denied","reason","Impactor indisponível");
        EconomyService economy;try{economy=EconomyService.instance();}catch(RuntimeException|LinkageError e){return app.messages.error(source,"paid_access_denied","reason","economia indisponível");}
        var currency=economy.currencies().currency(Key.key(cfg.currency));if(currency.isEmpty())return app.messages.error(source,"paid_access_denied","reason","moeda não encontrada");
        String payment=UUID.randomUUID().toString();UUID token=app.teleports.capturePaidEntry(player,warp.position);
        app.data().update(player.getUuid(),p->{p.pendingPaidAccess=payment;p.pendingPaidAccessAmount=price.toPlainString();p.pendingPaidAccessCurrency=cfg.currency;});
        inFlight.add(player.getUuid());guardTokens.put(player.getUuid(),token);app.messages.send(source,"paid_access_processing","price",price.toPlainString(),"currency",cfg.currency);
        economy.account(currency.get(),player.getUuid()).thenCompose(account->account.withdrawAsync(price).thenCompose(report->{if(!report.successful())return java.util.concurrent.CompletableFuture.completedFuture(new Result(false,report.result().name()));return economy.save(account).thenApply(ignored->new Result(true,"SUCCESS"));})).whenComplete((result,error)->{var server=app.server();if(server==null){ShirokaiEssentials.LOGGER.error("Servidor encerrou durante o pagamento {}; journal preservado",payment,error);return;}server.execute(()->finish(player.getUuid(),payment,warp.position,price,cfg.currency,result,error));});
        return 1;
    }

    private int grantFree(ServerPlayerEntity player,Position destination)throws IOException{long amount=app.settings().paidAccess.sessionSeconds*1000L;UUID token=app.teleports.capturePaidEntry(player,destination);int result=app.teleports.paidEntryTeleport(player,destination,token);if(result==1)activate(player.getUuid(),amount);return result;}
    private int enterWithTime(ServerPlayerEntity player,Position destination,long left)throws IOException{UUID token=app.teleports.capturePaidEntry(player,destination);int result=app.teleports.paidEntryTeleport(player,destination,token);if(result==1){activate(player.getUuid(),left);app.messages.send(player.getCommandSource(),"paid_access_time","time",format(left));}return result;}
    public int exit(ServerPlayerEntity player)throws IOException{var warp=app.data().warp(app.settings().paidAccess.exitWarp);return warp==null?app.messages.error(player.getCommandSource(),"missing"):app.teleports.teleport(player,warp.position,"areaxp",ignored->app.settings().paidAccess.enabled);}

    private void finish(UUID playerId,String payment,Position destination,BigDecimal price,String currencyId,Result result,Throwable error){
        inFlight.remove(playerId);UUID token=guardTokens.remove(playerId);
        try{var stored=app.data().player(playerId);if(!payment.equals(stored.pendingPaidAccess)){app.teleports.revokePaidEntry(token);ShirokaiEssentials.LOGGER.error("Pagamento {} finalizou após alteração administrativa",payment);return;}var player=app.server().getPlayerManager().getPlayer(playerId);if(error!=null||result==null){app.teleports.revokePaidEntry(token);notifyUncertain(player,payment,error);return;}if(!result.successful()){app.teleports.revokePaidEntry(token);clearPending(playerId);if(player!=null)app.messages.error(player.getCommandSource(),"paid_access_denied","reason",result.reason());return;}if(player!=null&&app.teleports.paidEntryTeleport(player,destination,token)==1){long amount=app.settings().paidAccess.sessionSeconds*1000L;grant(playerId,amount);activateMemory(playerId,amount);app.messages.send(player.getCommandSource(),"paid_access_success","time",format(amount));return;}refund(playerId,payment,price,currencyId,player);}catch(Exception e){app.teleports.revokePaidEntry(token);notifyUncertain(app.server().getPlayerManager().getPlayer(playerId),payment,e);}
    }
    private void refund(UUID playerId,String payment,BigDecimal amount,String currencyId,ServerPlayerEntity player){try{var economy=EconomyService.instance();var currency=economy.currencies().currency(Key.key(currencyId)).orElseThrow();economy.account(currency,playerId).thenCompose(account->account.depositAsync(amount).thenCompose(report->{if(!report.successful())return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(report.result().name()));return economy.save(account);})).whenComplete((ignored,error)->{var server=app.server();if(server==null){ShirokaiEssentials.LOGGER.error("Servidor encerrou durante o reembolso {}; journal preservado",payment,error);return;}server.execute(()->{if(error!=null){notifyUncertain(player,payment,error);return;}try{clearPending(playerId);if(player!=null)app.messages.send(player.getCommandSource(),"paid_access_refunded");}catch(IOException e){notifyUncertain(player,payment,e);}});});}catch(RuntimeException|LinkageError e){notifyUncertain(player,payment,e);}}

    public void tick(MinecraftServer server){if(!app.settings().paidAccess.enabled||sessions.isEmpty())return;long now=System.currentTimeMillis();for(var entry:List.copyOf(sessions.entrySet())){UUID id=entry.getKey();Session s=entry.getValue();var player=server.getPlayerManager().getPlayer(id);if(player==null){pauseQuiet(id,now);continue;}boolean inside=region.contains(app.settings().paidAccess.dimension,app.settings().paidAccess.region,player.getX(),player.getY(),player.getZ());if(!inside&&app.settings().paidAccess.pauseOnExit){pauseQuiet(id,now);continue;}long elapsed=Math.max(0,now-s.last);s.last=now;s.remaining=Math.max(0,s.remaining-elapsed);if(s.remaining==0){persistQuiet(id,0);if(now>=s.retry){s.retry=now+1000;expire(player);}continue;}if(now-s.checkpoint>=app.settings().paidAccess.checkpointSeconds*1000L){persistQuiet(id,s.remaining);s.checkpoint=now;}}}
    private void expire(ServerPlayerEntity player){if(!app.settings().paidAccess.exitOnExpire){sessions.remove(player.getUuid());app.messages.send(player.getCommandSource(),"paid_access_expired");return;}var warp=app.data().warp(app.settings().paidAccess.exitWarp);if(warp==null)return;try{if(app.teleports.systemTeleport(player,warp.position)==1){sessions.remove(player.getUuid());app.messages.send(player.getCommandSource(),"paid_access_expired");}}catch(IOException e){ShirokaiEssentials.LOGGER.error("Falha ao retirar jogador da área XP",e);}}
    private void activate(UUID id,long left)throws IOException{persist(id,left);activateMemory(id,left);}private void activateMemory(UUID id,long left){sessions.put(id,new Session(left,System.currentTimeMillis()));}
    private long remaining(UUID id,long now)throws IOException{Session s=sessions.get(id);return s==null?app.data().player(id).paidAccessRemainingMillis:Math.max(0,s.remaining-Math.max(0,now-s.last));}
    private void pause(UUID id,long now)throws IOException{Session s=sessions.remove(id);if(s!=null)persist(id,Math.max(0,s.remaining-Math.max(0,now-s.last)));}
    private void pauseQuiet(UUID id,long now){try{pause(id,now);}catch(IOException e){ShirokaiEssentials.LOGGER.error("Falha ao salvar tempo da área XP",e);}}
    private void persist(UUID id,long left)throws IOException{app.data().update(id,p->p.paidAccessRemainingMillis=left);}private void persistQuiet(UUID id,long left){try{persist(id,left);}catch(IOException e){ShirokaiEssentials.LOGGER.error("Falha no checkpoint da área XP",e);}}
    private void grant(UUID id,long left)throws IOException{app.data().update(id,p->{p.pendingPaidAccess="";p.pendingPaidAccessAmount="";p.pendingPaidAccessCurrency="";p.paidAccessRemainingMillis=left;});}
    private void clearPending(UUID id)throws IOException{app.data().update(id,p->{p.pendingPaidAccess="";p.pendingPaidAccessAmount="";p.pendingPaidAccessCurrency="";});}
    private void notifyUncertain(ServerPlayerEntity player,String payment,Throwable error){if(player!=null)app.messages.error(player.getCommandSource(),"paid_access_uncertain","id",payment);ShirokaiEssentials.LOGGER.error("Acesso pago {} exige conciliação manual",payment,error);}
    public int clearPending(ServerPlayerEntity admin,ServerPlayerEntity target)throws IOException{var p=app.data().player(target.getUuid());if(p.pendingPaidAccess.isBlank())return app.messages.error(admin.getCommandSource(),"missing");String id=p.pendingPaidAccess;clearPending(target.getUuid());return app.messages.send(admin.getCommandSource(),"paid_access_cleared","id",id);}
    public void join(ServerPlayerEntity player){try{var p=app.data().player(player.getUuid());if(!p.pendingPaidAccess.isBlank())app.messages.error(player.getCommandSource(),"paid_access_pending","id",p.pendingPaidAccess);var cfg=app.settings().paidAccess;if(cfg.enabled&&region.contains(cfg.dimension,cfg.region,player.getX(),player.getY(),player.getZ())){if(p.paidAccessRemainingMillis>0)activateMemory(player.getUuid(),p.paidAccessRemainingMillis);else expire(player);}}catch(IOException e){ShirokaiEssentials.LOGGER.error("Falha ao ler acesso pago",e);}}
    public void disconnect(UUID id){UUID token=guardTokens.remove(id);app.teleports.revokePaidEntry(token);pauseQuiet(id,System.currentTimeMillis());}
    public void stop(){long now=System.currentTimeMillis();for(UUID id:List.copyOf(sessions.keySet()))pauseQuiet(id,now);}
    static String format(long millis){long seconds=(millis+999)/1000,h=seconds/3600,m=(seconds%3600)/60,s=seconds%60;return h>0?String.format("%dh %02dmin %02ds",h,m,s):String.format("%dmin %02ds",m,s);}
}
