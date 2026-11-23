package dev.shirokai.essentials.paidaccess;

import dev.shirokai.essentials.ShirokaiEssentials;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.impactdev.impactor.api.economy.EconomyService;
import net.kyori.adventure.key.Key;
import net.minecraft.server.network.ServerPlayerEntity;

/** One paid entry at a time. Ambiguous provider results are never retried automatically. */
public final class PaidAccessService {
    private final ShirokaiEssentials app;
    private final Set<UUID> inFlight = new HashSet<>();
    private final Map<UUID,UUID> guardTokens = new HashMap<>();
    private record Result(boolean successful,String reason) {}

    public PaidAccessService(ShirokaiEssentials app){this.app=app;}

    public int status(ServerPlayerEntity player){
        var c=app.settings().paidAccess;
        return app.messages.send(player.getCommandSource(),"paid_access_status","price",c.price,"currency",c.currency,"warp",c.entranceWarp);
    }
    public int exit(ServerPlayerEntity player)throws IOException{
        var warp=app.data().warp(app.settings().paidAccess.exitWarp);
        return warp==null?app.messages.error(player.getCommandSource(),"missing")
            :app.teleports.teleport(player,warp.position,"xp",ignored->app.settings().paidAccess.enabled);
    }

    public int enter(ServerPlayerEntity player) throws IOException {
        var source=player.getCommandSource();var cfg=app.settings().paidAccess;
        if(!cfg.enabled)return app.messages.error(source,"paid_access_disabled");
        var warp=app.data().warp(cfg.entranceWarp);
        if(warp==null)return app.messages.error(source,"missing");
        var data=app.data().player(player.getUuid());
        if(!data.pendingPaidAccess.isBlank())return app.messages.error(source,"paid_access_pending","id",data.pendingPaidAccess);
        if(inFlight.contains(player.getUuid()))return app.messages.error(source,"pending");
        BigDecimal price=new BigDecimal(cfg.price);
        if(price.signum()==0)return app.teleports.teleport(player,warp.position,"xp",ignored->app.settings().paidAccess.enabled);
        if(!FabricLoader.getInstance().isModLoaded("impactor"))return app.messages.error(source,"paid_access_denied","reason","Impactor indisponível");
        EconomyService economy;
        try { economy=EconomyService.instance(); }
        catch(RuntimeException|LinkageError e){return app.messages.error(source,"paid_access_denied","reason","economia indisponível");}
        var currency=economy.currencies().currency(Key.key(cfg.currency));
        if(currency.isEmpty())return app.messages.error(source,"paid_access_denied","reason","moeda não encontrada");
        String payment=UUID.randomUUID().toString();
        UUID token=app.teleports.capturePaidEntry(player,warp.position);
        app.data().update(player.getUuid(),p->{p.pendingPaidAccess=payment;p.pendingPaidAccessAmount=price.toPlainString();p.pendingPaidAccessCurrency=cfg.currency;});
        inFlight.add(player.getUuid());guardTokens.put(player.getUuid(),token);
        app.messages.send(source,"paid_access_processing","price",price.toPlainString(),"currency",cfg.currency);
        economy.account(currency.get(),player.getUuid()).thenCompose(account->account.withdrawAsync(price).thenCompose(report->{
            if(!report.successful())return java.util.concurrent.CompletableFuture.completedFuture(new Result(false,report.result().name()));
            return economy.save(account).thenApply(ignored->new Result(true,"SUCCESS"));
        })).whenComplete((result,error)->{
            var server=app.server();
            if(server==null){ShirokaiEssentials.LOGGER.error("Servidor encerrou durante o pagamento {}; journal preservado",payment,error);return;}
            server.execute(()->finish(player.getUuid(),payment,warp.position,price,cfg.currency,result,error));
        });
        return 1;
    }

    private void finish(UUID playerId,String payment,dev.shirokai.essentials.persistence.Position destination,BigDecimal price,String currencyId,Result result,Throwable error){
        inFlight.remove(playerId);UUID token=guardTokens.remove(playerId);
        try {
            var stored=app.data().player(playerId);
            if(!payment.equals(stored.pendingPaidAccess)){app.teleports.revokePaidEntry(token);ShirokaiEssentials.LOGGER.error("Pagamento {} finalizou após alteração administrativa",payment);return;}
            var player=app.server().getPlayerManager().getPlayer(playerId);
            if(error!=null||result==null){app.teleports.revokePaidEntry(token);notifyUncertain(player,payment,error);return;}
            if(!result.successful()){
                app.teleports.revokePaidEntry(token);clear(playerId);
                if(player!=null)app.messages.error(player.getCommandSource(),"paid_access_denied","reason",result.reason());
                return;
            }
            if(player!=null&&app.teleports.paidEntryTeleport(player,destination,token)==1){
                clear(playerId);app.messages.send(player.getCommandSource(),"paid_access_success");return;
            }
            refund(playerId,payment,price,currencyId,player);
        } catch(Exception e){app.teleports.revokePaidEntry(token);notifyUncertain(app.server().getPlayerManager().getPlayer(playerId),payment,e);}
    }

    private void refund(UUID playerId,String payment,BigDecimal amount,String currencyId,ServerPlayerEntity player){
        try {
            var economy=EconomyService.instance();var currency=economy.currencies().currency(Key.key(currencyId)).orElseThrow();
            economy.account(currency,playerId).thenCompose(account->account.depositAsync(amount).thenCompose(report->{
                if(!report.successful())return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(report.result().name()));
                return economy.save(account);
            })).whenComplete((ignored,error)->{
                var server=app.server();if(server==null){ShirokaiEssentials.LOGGER.error("Servidor encerrou durante o reembolso {}; journal preservado",payment,error);return;}
                server.execute(()->{
                if(error!=null){notifyUncertain(player,payment,error);return;}
                try{clear(playerId);if(player!=null)app.messages.send(player.getCommandSource(),"paid_access_refunded");}
                catch(IOException e){notifyUncertain(player,payment,e);}
                });
            });
        }catch(RuntimeException|LinkageError e){notifyUncertain(player,payment,e);}
    }

    private void notifyUncertain(ServerPlayerEntity player,String payment,Throwable error){
        if(player!=null)app.messages.error(player.getCommandSource(),"paid_access_uncertain","id",payment);
        ShirokaiEssentials.LOGGER.error("Acesso pago {} exige conciliação manual",payment,error);
    }
    private void clear(UUID id)throws IOException{app.data().update(id,p->{p.pendingPaidAccess="";p.pendingPaidAccessAmount="";p.pendingPaidAccessCurrency="";});}
    public int clearPending(ServerPlayerEntity admin,ServerPlayerEntity target)throws IOException{
        var p=app.data().player(target.getUuid());if(p.pendingPaidAccess.isBlank())return app.messages.error(admin.getCommandSource(),"missing");
        String id=p.pendingPaidAccess;clear(target.getUuid());return app.messages.send(admin.getCommandSource(),"paid_access_cleared","id",id);
    }
    public void disconnect(UUID id){UUID token=guardTokens.remove(id);app.teleports.revokePaidEntry(token);}
}
