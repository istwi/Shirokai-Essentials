package dev.shirokai.essentials.cosmetics;

import dev.zorykai.cosmetics.protocol.WingsPayload;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import java.util.function.Function;

/** Optional S2C-only protocol. Clients cannot request grants, models or inventory changes. */
public final class CosmeticModels {
    private final Map<UUID,Map<UUID,String>> sent=new HashMap<>();
    public void initialize() {PayloadTypeRegistry.playS2C().register(WingsPayload.ID,WingsPayload.CODEC);}
    public boolean supports(ServerPlayerEntity viewer) {return ServerPlayNetworking.canSend(viewer,WingsPayload.ID);}
    public void clear() {sent.clear();}
    public void disconnect(UUID viewer) {sent.remove(viewer);}
    public void sync(MinecraftServer server,Function<ServerPlayerEntity,String> state,java.util.function.Predicate<ServerPlayerEntity> hidden) {
        var online=server.getPlayerManager().getPlayerList();
        sent.keySet().removeIf(id->server.getPlayerManager().getPlayer(id)==null);
        for(var viewer:online) {
            if(!supports(viewer)){sent.remove(viewer.getUuid());continue;}
            Map<UUID,String> desired=new HashMap<>();
            if(!hidden.test(viewer))for(var subject:online) {
                if(subject!=viewer&&!PlayerLookup.tracking(subject).contains(viewer))continue;
                String style=state.apply(subject);if(!style.isEmpty())desired.put(subject.getUuid(),style);
            }
            var before=sent.computeIfAbsent(viewer.getUuid(),id->new HashMap<>());
            for(var entry:desired.entrySet())if(!entry.getValue().equals(before.get(entry.getKey())))ServerPlayNetworking.send(viewer,new WingsPayload(entry.getKey(),entry.getValue()));
            for(var id:before.keySet())if(!desired.containsKey(id))ServerPlayNetworking.send(viewer,new WingsPayload(id,""));
            sent.put(viewer.getUuid(),desired);
        }
    }
}
