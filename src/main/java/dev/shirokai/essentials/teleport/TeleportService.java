package dev.shirokai.essentials.teleport;

import dev.shirokai.essentials.*;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.permissions.PermissionService;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public final class TeleportService {
    public static Position position(ServerPlayerEntity p) { return new Position(p.getServerWorld().getRegistryKey().getValue().toString(), p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), System.currentTimeMillis()); }
    private static final net.minecraft.server.world.ChunkTicketType<UUID> TICKET = net.minecraft.server.world.ChunkTicketType.create("shirokai_essentials", Comparator.comparing(UUID::toString), 600);
    private record Loading(UUID token, UUID actor, UUID player, ServerWorld world, net.minecraft.util.math.ChunkPos chunk, Position origin, float health, long expires) {}
    private final Map<UUID,Loading> loading = new HashMap<>(), held = new HashMap<>();
    private record Pending(UUID actor, UUID player, Position destination, Position origin, float health, long due, String permission, java.util.function.Predicate<ServerPlayerEntity> authorization) {}
    private final Map<UUID, Pending> pending = new HashMap<>();
    public final Requests requests = new Requests();
    private final Supplier<Settings> settings;
    private final Supplier<DataStore> data;
    private final Messages messages;
    private final PermissionService permissions;
    public TeleportService(Supplier<Settings> settings, Supplier<DataStore> data, Messages messages, PermissionService permissions) {
        this.settings = settings; this.data = data; this.messages = messages; this.permissions = permissions;
    }
    private java.util.function.Predicate<ServerPlayerEntity> restriction = p -> false, battleRestriction = p -> false;
    public void setBattleRestriction(java.util.function.Predicate<ServerPlayerEntity> predicate){battleRestriction=predicate;}
    public void setRestriction(java.util.function.Predicate<ServerPlayerEntity> predicate) { restriction=predicate; }
    public int systemTeleport(ServerPlayerEntity player, Position destination) throws IOException { return perform(player, destination, true); }
    public boolean requestsEnabled() { return settings.get().module("teleport") && settings.get().feature("tpa_requests"); }
    public ServerWorld world(MinecraftServer server, Position p) { return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(p.dimension()))); }
    public boolean safe(MinecraftServer server, Position p) {
        var cfg = settings.get();
        if (cfg.teleport.blockedWorlds.contains(p.dimension())) return false;
        var w = world(server, p);
        var pos = BlockPos.ofFloored(p.x(), p.y(), p.z());
        if (w == null || !w.getWorldBorder().contains(pos) || p.y() < w.getBottomY() || p.y() + 2 >= w.getTopY()) return false;
        // No synchronous generation: refuse unloaded chunks by default.
        if (cfg.teleport.rejectUnloadedChunks || cfg.teleport.asyncChunkLoading) {
            for (int dx : new int[]{-1, 0, 1}) for (int dz : new int[]{-1, 0, 1})
                if (!w.isChunkLoaded(pos.add(dx, 0, dz))) return false;
        }
        if (!cfg.feature("teleport_safety")) return true;
        if (!w.isSpaceEmpty(null, new Box(p.x() - .3, p.y(), p.z() - .3, p.x() + .3, p.y() + 1.8, p.z() + .3))) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
            var state = w.getBlockState(pos.add(dx, dy, dz));
            if (!state.getFluidState().isEmpty() || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL)) return false;
        }
        var floor = w.getBlockState(pos.down());
        return floor.isFullCube(w, pos.down());
    }
    public int teleport(ServerPlayerEntity player, Position destination, String permission) throws IOException {
        return teleport(player, player, destination, permission);
    }
    public int teleport(ServerPlayerEntity actor, ServerPlayerEntity player, Position destination, String permission) throws IOException {
        return teleport(actor, player, destination, permission, true, ignored -> true);
    }
    public int teleport(ServerPlayerEntity player, Position destination, String permission, java.util.function.Predicate<ServerPlayerEntity> authorization) throws IOException {
        return teleport(player,player,destination,permission,true,authorization);
    }
    private int teleport(ServerPlayerEntity actor, ServerPlayerEntity player, Position destination, String permission, boolean mayLoad, java.util.function.Predicate<ServerPlayerEntity> authorization) throws IOException {
        var source = player.getCommandSource();
        if (!authorization.test(actor)) return messages.error(source,"denied");
        if (battleRestriction.test(player) || restriction.test(player)) return messages.error(source, "unsafe");
        var cfg = settings.get();
        if (cfg.teleport.blockedWorlds.contains(player.getServerWorld().getRegistryKey().getValue().toString())) return messages.error(source, "unsafe");
        if (player.isRemoved() || !player.isAlive() || player.hasVehicle() || player.hasPassengers() || player.isSleeping()) return messages.error(source, "unsafe");
        if (pending.containsKey(player.getUuid()) || loading.containsKey(player.getUuid())) return messages.error(source, "pending");
        long now = System.currentTimeMillis();
        long cooldown = data.get().player(player.getUuid()).nextTeleportAt;
        if (cfg.feature("teleport_cooldown") && cooldown > now && !permissions.has(source, "bypass.cooldown")) return messages.error(source, "cooldown", "time", (cooldown - now + 999) / 1000);
        if (!safe(player.getServer(), destination)) {
            var targetWorld=world(player.getServer(),destination);
            var block=BlockPos.ofFloored(destination.x(),destination.y(),destination.z());
            if(mayLoad && cfg.teleport.asyncChunkLoading && targetWorld!=null && targetWorld.getWorldBorder().contains(block) && destination.y()>=targetWorld.getBottomY() && destination.y()+2<targetWorld.getTopY() && !cfg.teleport.blockedWorlds.contains(destination.dimension()) && !chunksLoaded(targetWorld,block))return load(actor,player,destination,permission,targetWorld,false,authorization);
            return messages.error(source,"unsafe");
        }
        int warmup = cfg.feature("teleport_warmup") && !permissions.has(source, "bypass.warmup") ? cfg.teleport.warmupSeconds : 0;
        if (warmup == 0) return perform(player, destination);
        if (pending.size() + loading.size() >= cfg.teleport.maxPendingTeleports) return messages.error(source, "busy");
        pending.put(player.getUuid(), new Pending(actor.getUuid(), player.getUuid(), destination, position(player), player.getHealth() + player.getAbsorptionAmount(), now + warmup * 1000L, permission, authorization));
        return messages.send(source, "warmup", "time", warmup);
    }
    private boolean chunksLoaded(ServerWorld world, BlockPos pos) {
        for(int x:new int[]{-1,0,1})for(int z:new int[]{-1,0,1})if(!world.isChunkLoaded(pos.add(x,0,z)))return false;
        return true;
    }
    private int load(ServerPlayerEntity actor,ServerPlayerEntity p,Position destination,String permission,ServerWorld world) { return load(actor,p,destination,permission,world,false,ignored -> true); }
    private int load(ServerPlayerEntity actor,ServerPlayerEntity p,Position destination,String permission,ServerWorld world,boolean surface,java.util.function.Predicate<ServerPlayerEntity> authorization) {
        if(pending.size()+loading.size()>=settings.get().teleport.maxPendingTeleports)return messages.error(p.getCommandSource(),"busy");
        var chunk=new net.minecraft.util.math.ChunkPos(BlockPos.ofFloored(destination.x(),destination.y(),destination.z()));
        var job=new Loading(UUID.randomUUID(),actor.getUuid(),p.getUuid(),world,chunk,position(p),p.getHealth()+p.getAbsorptionAmount(),System.currentTimeMillis()+settings.get().teleport.asyncTimeoutSeconds*1000L);
        loading.put(p.getUuid(),job);held.put(p.getUuid(),job);
        world.getChunkManager().addTicket(TICKET,chunk,2,p.getUuid());
        List<java.util.concurrent.CompletableFuture<net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk>>> futures=new ArrayList<>();
        // Schedule full chunks on the server thread. Do not wait/join/runTasks on their futures.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)futures.add(world.getChunkManager().getChunkFutureSyncOnMainThread(chunk.x+x,chunk.z+z,net.minecraft.world.chunk.ChunkStatus.FULL,true));
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ignored,error)->{
            if(p.getServer().isStopped())return;
            p.getServer().execute(()->{
                if(loading.get(p.getUuid())!=job)return;
                loading.remove(p.getUuid());
                var current=p.getServer().getPlayerManager().getPlayer(p.getUuid());var initiator=p.getServer().getPlayerManager().getPlayer(actor.getUuid());
                try {
                    if(current==null||initiator==null||error!=null||futures.stream().anyMatch(f->!f.getNow(net.minecraft.server.world.OptionalChunk.of("not ready")).isPresent())||!settings.get().command(permission)||!permissions.has(initiator.getCommandSource(),permission)){if(current!=null)messages.error(current.getCommandSource(),"unsafe");return;}
                    if(!actor.getUuid().equals(p.getUuid())&&Set.of("tphere","tpall").contains(permission)&&!permissions.has(initiator.getCommandSource(),permission+".others")){messages.error(current.getCommandSource(),"denied");return;}
                    boolean moved=!current.getServerWorld().getRegistryKey().getValue().toString().equals(job.origin.dimension())||current.getPos().squaredDistanceTo(job.origin.x(),job.origin.y(),job.origin.z())>settings.get().teleport.movementTolerance*settings.get().teleport.movementTolerance;
                    if(settings.get().teleport.cancelOnMovement&&moved){messages.send(current.getCommandSource(),"cancelled");return;}
                    Position actual=destination;
                    if(surface){var top=world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,BlockPos.ofFloored(destination.x(),0,destination.z()));actual=new Position(destination.dimension(),destination.x(),top.getY(),destination.z(),destination.yaw(),destination.pitch(),System.currentTimeMillis());}
                    teleport(initiator,current,actual,permission,false,authorization);
                } catch(IOException e){ShirokaiEssentials.LOGGER.error("Falha no teleporte após carga de chunk",e);if(current!=null)messages.error(current.getCommandSource(),"storage_error");}
                finally{if(!pending.containsKey(p.getUuid()))release(p.getUuid());}
            });
        });
        return messages.send(p.getCommandSource(),"chunk_loading");
    }
    public int randomSurface(ServerPlayerEntity p,int x,int z)throws IOException {
        var cfg=settings.get();var source=p.getCommandSource();
        if(!cfg.teleport.asyncChunkLoading||battleRestriction.test(p)||restriction.test(p)||!p.isAlive()||p.hasVehicle()||p.hasPassengers()||p.isSleeping()||cfg.teleport.blockedWorlds.contains(p.getServerWorld().getRegistryKey().getValue().toString())||!p.getServerWorld().getWorldBorder().contains(new BlockPos(x,0,z)))return messages.error(source,"unsafe");
        if(pending.containsKey(p.getUuid())||loading.containsKey(p.getUuid()))return messages.error(source,"pending");
        long cooldown=data.get().player(p.getUuid()).nextTeleportAt-System.currentTimeMillis();
        if(cfg.feature("teleport_cooldown")&&cooldown>0&&!permissions.has(source,"bypass.cooldown"))return messages.error(source,"cooldown","time",(cooldown+999)/1000);
        Position dest=new Position(p.getServerWorld().getRegistryKey().getValue().toString(),x+.5,p.getY(),z+.5,p.getYaw(),p.getPitch(),System.currentTimeMillis());
        return load(p,p,dest,"rtp",p.getServerWorld(),true,ignored -> true);
    }
    private void release(UUID id) {
        var job=held.remove(id);if(job!=null)job.world.getChunkManager().removeTicket(TICKET,job.chunk,2,id);
    }
    private int perform(ServerPlayerEntity p, Position destination) throws IOException { return perform(p,destination,false); }
    private int perform(ServerPlayerEntity p, Position destination, boolean system) throws IOException {
        if (battleRestriction.test(p) || (!system && restriction.test(p))) return messages.error(p.getCommandSource(), "unsafe");
        if (!safe(p.getServer(), destination) || !p.isAlive() || p.hasVehicle() || p.hasPassengers() || p.isSleeping()) return messages.error(p.getCommandSource(), "unsafe");
        var cfg = settings.get();
        Position origin = position(p);
        // Commit history/cooldown before changing worlds. A failed write cancels the teleport.
        data.get().update(p.getUuid(), d -> {
            if (cfg.module("teleport") && cfg.feature("back_history") && safe(p.getServer(), origin)) d.back = origin;
            if (cfg.feature("teleport_cooldown")) d.nextTeleportAt = System.currentTimeMillis() + cfg.teleport.cooldownSeconds * 1000L;
        });
        p.teleport(world(p.getServer(), destination), destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch());
        p.fallDistance = 0;
        return messages.send(p.getCommandSource(), "teleported");
    }
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (requestsEnabled()) for (var r : requests.expire(now)) {
            var sender = server.getPlayerManager().getPlayer(r.sender());
            if (sender != null) messages.send(sender.getCommandSource(), "request_expired");
        }
        for(var item:List.copyOf(loading.values())) {
            var player=server.getPlayerManager().getPlayer(item.player);
            boolean moved=player!=null&&(!player.getServerWorld().getRegistryKey().getValue().toString().equals(item.origin.dimension())||player.getPos().squaredDistanceTo(item.origin.x(),item.origin.y(),item.origin.z())>settings.get().teleport.movementTolerance*settings.get().teleport.movementTolerance);
            if(now>=item.expires||player==null||!player.isAlive()||battleRestriction.test(player)||(settings.get().teleport.cancelOnMovement&&moved)||(settings.get().teleport.cancelOnDamage&&player.getHealth()+player.getAbsorptionAmount()<item.health)){
                loading.remove(item.player);release(item.player);if(player!=null)messages.send(player.getCommandSource(),"cancelled");
            }
        }
        if(server.getTicks()%20==0)for(var job:held.values())job.world.getChunkManager().addTicket(TICKET,job.chunk,2,job.player);
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending item = iterator.next();
            var p = server.getPlayerManager().getPlayer(item.player());
            if (p == null) { iterator.remove(); release(item.player()); continue; }
            var cfg = settings.get();
            boolean moved = !p.getServerWorld().getRegistryKey().getValue().toString().equals(item.origin().dimension()) || p.getPos().squaredDistanceTo(item.origin().x(), item.origin().y(), item.origin().z()) > cfg.teleport.movementTolerance * cfg.teleport.movementTolerance;
            boolean damaged = p.getHealth() + p.getAbsorptionAmount() < item.health();
            var actor = server.getPlayerManager().getPlayer(item.actor());
            boolean needsOthers = !item.actor().equals(item.player()) && Set.of("tphere", "tpall").contains(item.permission());
            if (actor == null || battleRestriction.test(p) || !item.authorization().test(actor) || (needsOthers && !permissions.has(actor.getCommandSource(), item.permission() + ".others")) || !cfg.command(item.permission()) || !permissions.has(actor.getCommandSource(), item.permission()) || !p.isAlive() || (cfg.teleport.cancelOnMovement && moved) || (cfg.teleport.cancelOnDamage && damaged)) {
                iterator.remove(); release(p.getUuid()); messages.send(p.getCommandSource(), "cancelled"); continue;
            }
            if (now >= item.due()) {
                iterator.remove();
                try { perform(p, item.destination()); } catch (IOException e) { ShirokaiEssentials.LOGGER.error("Falha ao persistir teleporte", e); messages.error(p.getCommandSource(), "storage_error"); } finally { release(p.getUuid()); }
            }
        }
    }
    public void damage(ServerPlayerEntity p) {
        if (settings.get().teleport.cancelOnDamage) {
            boolean cancelled=pending.remove(p.getUuid())!=null;
            cancelled=(loading.remove(p.getUuid())!=null)||cancelled;
            if(cancelled){release(p.getUuid());messages.send(p.getCommandSource(),"cancelled");}
        }
    }
    public void disconnect(UUID id) {
        Set<UUID> remove=new HashSet<>();
        pending.values().forEach(p->{if(p.player().equals(id)||p.actor().equals(id))remove.add(p.player());});
        loading.values().forEach(p->{if(p.player.equals(id)||p.actor.equals(id))remove.add(p.player);});
        for(UUID player:remove){pending.remove(player);loading.remove(player);release(player);} requests.disconnect(id);
    }
    public void cancelAll(MinecraftServer server) {
        for (UUID id : pending.keySet()) {
            var player = server.getPlayerManager().getPlayer(id);
            if (player != null) messages.send(player.getCommandSource(), "cancelled");
        }
        clear();
    }
    public void clear() { for(UUID id:List.copyOf(held.keySet()))release(id);loading.clear();pending.clear();requests.clear(); }
}
