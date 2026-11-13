package dev.shirokai.essentials.moderation;

import com.mojang.authlib.GameProfile;
import dev.shirokai.essentials.*;
import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.teleport.TeleportService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.util.math.Vec3d;
import java.io.IOException;
import java.util.*;

public final class ModerationService {
    private final ShirokaiEssentials app;
    private Punishments state;
    public ModerationService(ShirokaiEssentials app) { this.app=app; }
    public void start() throws IOException { state=new Punishments(app.data().root().resolve("moderation-data.json")); }
    public void join(ServerPlayerEntity p)throws IOException {
        if(!enabled()||!app.settings().feature("moderation_jail"))return;var e=state.get(p.getUuid());
        if(e.jailed(System.currentTimeMillis())&&e.returnPosition==null)state.update(p.getUuid(),entry->entry.returnPosition=TeleportService.position(p));
    }
    public Punishments state() { return state; }
    public boolean enabled() { return state!=null && app.settings().module("moderation"); }
    public boolean restricted(ServerPlayerEntity p) {
        if(!enabled())return false; var e=state.status(p.getUuid()); return (app.settings().feature("moderation_freeze")&&e.freeze()!=null)||(app.settings().feature("moderation_jail")&&e.jailed(System.currentTimeMillis()));
    }
    public boolean canSpeak(ServerPlayerEntity p) {
        if(!enabled()||!app.settings().feature("moderation_chat"))return true;
        var entry=state.status(p.getUuid()); if(!entry.muted(System.currentTimeMillis()))return true;
        app.messages.error(p.getCommandSource(),"muted","reason",entry.muteReason()); return false;
    }
    public boolean canModerate(ServerCommandSource source, DataStore.Profile target) {
        if(source.getEntity()==null && source.hasPermissionLevel(4))return true;
        if(!source.isExecutedByPlayer()||source.getPlayer().getUuid().equals(target.id()))return false;
        int actor=app.permissions.rank(source.getServer(),source.getPlayer().getUuid());
        int subject=app.permissions.rank(source.getServer(),target.id());
        return subject>=0 && actor>subject;
    }
    public boolean guard(ServerCommandSource source, DataStore.Profile target) {
        if(target==null){app.messages.error(source,"player_missing");return false;}
        if(canModerate(source,target))return true;
        app.messages.error(source,"protected");return false;
    }
    public int punish(ServerCommandSource source, String action, DataStore.Profile target, String reason, long duration) throws IOException {
        if(!guard(source,target))return 0;
        if(reason.isBlank()||reason.length()>app.settings().moderation.maxReasonLength) return app.messages.error(source,"invalid","value","motivo");
        long until=duration==Long.MAX_VALUE?Long.MAX_VALUE:Math.addExact(System.currentTimeMillis(),duration);
        app.data().audit(source.getName(),action,target.id().toString(),reason);
        var online=source.getServer().getPlayerManager().getPlayer(target.id());
        switch(action) {
            case "kick" -> { if(online==null)return app.messages.error(source,"player_missing"); online.networkHandler.disconnect(app.messages.text("kick_reason","reason",reason)); }
            case "ban", "tempban" -> {
                if(!app.settings().moderation.manageVanillaBans)return app.messages.error(source,"denied");
                source.getServer().getPlayerManager().getUserBanList().add(new BannedPlayerEntry(new GameProfile(target.id(),target.name()),new Date(),source.getName(),until==Long.MAX_VALUE?null:new Date(until),reason));
                if(online!=null)online.networkHandler.disconnect(app.messages.text("ban_reason","reason",reason));
            }
            case "unban" -> source.getServer().getPlayerManager().getUserBanList().remove(new GameProfile(target.id(),target.name()));
            case "mute", "tempmute" -> state.update(target.id(),e->{e.muteUntil=until;e.muteReason=reason;});
            case "unmute" -> state.update(target.id(),e->{e.muteUntil=0;e.muteReason="";});
            case "warn" -> {
                if(state.get(target.id()).warnings.size()>=app.settings().moderation.warningLimit)return app.messages.error(source,"busy");
                state.update(target.id(),e->e.warnings.add(new Punishments.Warning(UUID.randomUUID().toString(),source.getName(),reason,System.currentTimeMillis())));
                if(online!=null)app.messages.send(online.getCommandSource(),"warn_notice","reason",reason);
            }
            case "clearwarnings" -> state.update(target.id(),e->e.warnings.clear());
            default -> throw new IllegalArgumentException("Ação desconhecida");
        }
        return app.messages.send(source,"punishment","action",action,"player",target.name(),"reason",reason);
    }
    public int freeze(ServerCommandSource source, DataStore.Profile target) throws IOException {
        if(!guard(source,target))return 0;
        var p=source.getServer().getPlayerManager().getPlayer(target.id()); if(p==null)return app.messages.error(source,"player_missing");
        boolean enable=state.get(target.id()).freeze==null;
        app.data().audit(source.getName(),"freeze",target.id().toString(),Boolean.toString(enable));
        state.update(target.id(),e->e.freeze=enable?TeleportService.position(p):null);
        app.teleports.disconnect(target.id());
        if(enable)app.messages.send(p.getCommandSource(),"frozen");
        return app.messages.send(source,"state","feature","freeze","state",enable?"ativado":"desativado");
    }
    public int jail(ServerCommandSource source, DataStore.Profile target, String jail, long duration) throws IOException {
        if(!guard(source,target))return 0;
        var dest=app.data().jail(jail);
        if(dest==null||!app.teleports.safe(source.getServer(),dest))return app.messages.error(source,"unsafe");
        var p=source.getServer().getPlayerManager().getPlayer(target.id());
        var old=state.get(target.id()); var back=old.jail.isEmpty()&&p!=null?TeleportService.position(p):old.returnPosition;
        app.data().audit(source.getName(),"jail",target.id().toString(),jail);
        state.update(target.id(),e->{e.jail=jail;e.jailUntil=duration==Long.MAX_VALUE?Long.MAX_VALUE:System.currentTimeMillis()+duration;e.returnPosition=back;});
        app.teleports.disconnect(target.id());
        if(p!=null){app.teleports.systemTeleport(p,dest);app.messages.send(p.getCommandSource(),"jailed");}
        return app.messages.send(source,"ok");
    }
    public int release(ServerCommandSource source, DataStore.Profile target) throws IOException {
        if(!guard(source,target))return 0;
        app.data().audit(source.getName(),"unjail",target.id().toString(),""); release(source.getServer(),target.id()); return app.messages.send(source,"ok");
    }
    private void release(MinecraftServer server, UUID id) throws IOException {
        var previous=state.get(id); state.update(id,e->{e.jail="";e.jailUntil=0;});
        var p=server.getPlayerManager().getPlayer(id);
        if(p!=null){if(previous.returnPosition!=null)app.teleports.systemTeleport(p,previous.returnPosition);app.messages.send(p.getCommandSource(),"released");}
    }
    public void tick(MinecraftServer server) {
        if(!enabled())return;
        boolean freeze=app.settings().feature("moderation_freeze"),jail=app.settings().feature("moderation_jail");
        if(!freeze&&!jail)return;
        for(var id:state.active(freeze,jail)){
            var p=server.getPlayerManager().getPlayer(id); if(p==null)continue;
            var e=state.status(id);
            try{
                if(freeze&&e.freeze()!=null)anchor(p,e.freeze(),0.1);
                if(jail&&server.getTicks()%20==0 && !e.jail().isEmpty()){
                    if(!e.jailed(System.currentTimeMillis()))release(server,id);
                    else {var dest=app.data().jail(e.jail());if(dest!=null)anchor(p,dest,app.settings().moderation.jailRadius);}
                }
            }catch(IOException ex){ShirokaiEssentials.LOGGER.error("Falha na expiração de prisão",ex);}
        }
    }
    private void anchor(ServerPlayerEntity p, Position pos, double radius){
        if(p.getServerWorld().getRegistryKey().getValue().toString().equals(pos.dimension()) && p.getPos().squaredDistanceTo(new Vec3d(pos.x(),pos.y(),pos.z()))<=radius*radius)return;
        var world=app.teleports.world(p.getServer(),pos);
        if(world!=null&&world.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(pos.x(),pos.y(),pos.z()))) {p.teleport(world,pos.x(),pos.y(),pos.z(),pos.yaw(),pos.pitch());p.fallDistance=0;}
    }
}
