package dev.shirokai.essentials.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.shirokai.essentials.*;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.teleport.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import java.io.IOException;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class Commands {
    private final ShirokaiEssentials app;
    private final Messages m;
    public Commands(ShirokaiEssentials app) { this.app = app; this.m = app.messages; }
    @FunctionalInterface interface Action { int run(CommandContext<ServerCommandSource> c) throws IOException, CommandSyntaxException; }
    Command<ServerCommandSource> action(String name, Action a) {
        return c -> {
            if (!allowed(c.getSource(), name)) return 0;
            try {
                if (app.settings().general.auditCommands) ShirokaiEssentials.LOGGER.info("Comando /{} por {}", name, c.getSource().getName());
                return a.run(c);
            } catch (IOException e) { ShirokaiEssentials.LOGGER.error("Falha de dados no comando /" + name, e); return m.error(c.getSource(), "storage_error"); }
        };
    }
    boolean allowed(ServerCommandSource s, String name) {
        if (!app.settings().command(name) || app.conflicts.moduleFailed(Settings.COMMAND_MODULE.get(name))) return false;
        if (Set.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tpatoggle").contains(name) && !app.teleports.requestsEnabled()) return false;
        if (name.equals("tpall") && !app.permissions.has(s, "tpall.others")) return false;
        if (name.equals("back") && !app.settings().feature("back_history")) return false;
        if (name.equals("shirokaiessentials")) return List.of("status", "reload", "conflicts", "version").stream().anyMatch(n -> app.settings().subcommand(n) && app.permissions.has(s, n));
        return app.permissions.has(s, name);
    }
    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var plan = app.conflicts.plan(app.settings(), dispatcher);
        plan.forEach((command, roots) -> {
            for (String root : roots) {
                var node = dispatcher.register(build(command, root).requires(s -> allowed(s, command)));
                app.conflicts.registered(root, node);
            }
        });
    }
    private String name(CommandContext<ServerCommandSource> c) { return StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT); }
    private boolean valid(ServerCommandSource source, String name) { if (Settings.validName(name)) return true; m.error(source, "invalid_name"); return false; }
    private LiteralArgumentBuilder<ServerCommandSource> build(String cmd, String root) {
        var b = literal(root);
        return switch (cmd) {
            case "xp" -> b.executes(action(cmd,c->app.paidAccess.status(c.getSource().getPlayerOrThrow())))
                .then(literal("entrar").executes(action(cmd,c->app.paidAccess.enter(c.getSource().getPlayerOrThrow()))))
                .then(literal("sair").executes(action(cmd,c->app.paidAccess.exit(c.getSource().getPlayerOrThrow()))))
                .then(literal("admin").requires(s->app.permissions.has(s,"xp.admin"))
                    .then(literal("clear").then(argument("player",EntityArgumentType.player()).executes(action(cmd,c->
                        app.paidAccess.clearPending(c.getSource().getPlayerOrThrow(),EntityArgumentType.getPlayer(c,"player")))))));
            case "home" -> b.executes(action(cmd, c -> home(c, app.settings().homes.defaultHome)))
                .then(argument("name", StringArgumentType.word()).suggests((c,s) -> { try { app.data().player(c.getSource().getPlayerOrThrow().getUuid()).homes.keySet().forEach(s::suggest); } catch (IOException e) { ShirokaiEssentials.LOGGER.error("Falha ao sugerir homes", e); } return s.buildFuture(); }).executes(action(cmd, c -> home(c, name(c)))));
            case "homes" -> b.executes(action(cmd, c -> m.send(c.getSource(), "list", "names", String.join(", ", app.data().player(c.getSource().getPlayerOrThrow().getUuid()).homes.keySet()))));
            case "sethome" -> b.executes(action(cmd, c -> setHome(c, app.settings().homes.defaultHome))).then(argument("name", StringArgumentType.word()).executes(action(cmd, c -> setHome(c, name(c)))));
            case "delhome" -> b.then(argument("name", StringArgumentType.word()).executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); String n = name(c);
                if (!app.data().player(p.getUuid()).homes.containsKey(n)) return m.error(c.getSource(), "missing");
                app.data().update(p.getUuid(), d -> d.homes.remove(n)); return m.send(c.getSource(), "deleted", "name", n);
            })));
            case "renamehome" -> b.then(argument("name", StringArgumentType.word()).then(argument("newName", StringArgumentType.word()).executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); String n = name(c), next = StringArgumentType.getString(c, "newName").toLowerCase(Locale.ROOT);
                if (!valid(c.getSource(), next)) return 0;
                var homes = app.data().player(p.getUuid()).homes;
                if (!homes.containsKey(n) || homes.containsKey(next)) return m.error(c.getSource(), "missing");
                app.data().update(p.getUuid(), d -> d.homes.put(next, d.homes.remove(n))); return m.send(c.getSource(), "saved", "name", next);
            }))));
            case "spawn" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); var pos = app.data().spawn(app.spawnKey(p));
                return pos == null ? m.error(c.getSource(), "missing") : app.teleports.teleport(p, pos, cmd);
            }));
            case "setspawn" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); var pos = TeleportService.position(p);
                app.data().updateWorld(d -> d.spawns.put(app.spawnKey(p), pos)); return m.send(c.getSource(), "saved", "name", "spawn");
            }));
            case "warp" -> b.then(argument("name", StringArgumentType.word()).suggests((c,s) -> { app.data().warps().forEach((n,w) -> { if (canWarp(c.getSource(), n, w)) s.suggest(n); }); return s.buildFuture(); }).executes(action(cmd, c -> {
                String n = name(c); var w = app.data().warp(n);
                if (w == null || !canWarp(c.getSource(), n, w)) return m.error(c.getSource(), "missing");
                return app.teleports.teleport(c.getSource().getPlayerOrThrow(), w.position, cmd, actor -> {
                    var current=app.data().warp(n);
                    return current!=null&&current.position.equals(w.position)&&canWarp(actor.getCommandSource(),n,current);
                });
            })));
            case "warps" -> b.executes(action(cmd, c -> m.send(c.getSource(), "list", "names", String.join(", ", app.data().warps().entrySet().stream().filter(e -> canWarp(c.getSource(), e.getKey(), e.getValue())).map(Map.Entry::getKey).toList()))));
            case "setwarp" -> b.then(argument("name", StringArgumentType.word()).executes(action(cmd, c -> setWarp(c, true))).then(argument("public", BoolArgumentType.bool()).executes(action(cmd, c -> setWarp(c, BoolArgumentType.getBool(c, "public"))))));
            case "delwarp" -> b.then(argument("name", StringArgumentType.word()).executes(action(cmd, c -> {
                String n = name(c); if (!app.data().warps().containsKey(n)) return m.error(c.getSource(), "missing");
                app.data().updateWorld(d -> d.warps.remove(n)); return m.send(c.getSource(), "deleted", "name", n);
            })));
            case "tpa", "tpahere" -> b.then(argument("player", EntityArgumentType.player()).executes(action(cmd, c -> request(c, cmd.equals("tpahere")))));
            case "tpaccept", "tpdeny" -> b.executes(action(cmd, c -> respond(c, null, cmd.equals("tpaccept")))).then(argument("player", EntityArgumentType.player()).executes(action(cmd, c -> respond(c, EntityArgumentType.getPlayer(c, "player").getUuid(), cmd.equals("tpaccept")))));
            case "tpacancel" -> b.executes(action(cmd, c -> {
                app.teleports.requests.remove(c.getSource().getPlayerOrThrow().getUuid()); return m.send(c.getSource(), "cancelled");
            }));
            case "tpatoggle" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); boolean blocked = !app.data().player(p.getUuid()).requestsBlocked;
                app.data().update(p.getUuid(), d -> d.requestsBlocked = blocked);
                if (blocked) app.teleports.requests.disconnect(p.getUuid());
                return m.send(c.getSource(), "toggle", "state", blocked ? "bloqueados" : "ativados");
            }));
            case "back" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); var back = app.data().player(p.getUuid()).back;
                if (back == null || System.currentTimeMillis() - back.savedAt() > app.settings().teleport.backRetentionSeconds * 1000L) return m.error(c.getSource(), "no_back");
                return app.teleports.teleport(p, back, cmd);
            }));
            case "tp" -> b.then(argument("player", EntityArgumentType.player()).executes(action(cmd, c -> app.teleports.teleport(c.getSource().getPlayerOrThrow(), TeleportService.position(EntityArgumentType.getPlayer(c, "player")), cmd))));
            case "tphere" -> b.then(argument("player", EntityArgumentType.player()).requires(s -> app.permissions.has(s, "tphere.others")).executes(action(cmd, c -> app.teleports.teleport(c.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(c, "player"), TeleportService.position(c.getSource().getPlayerOrThrow()), cmd))));
            case "tpall" -> b.requires(s -> app.permissions.has(s, "tpall.others")).executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); int count = 0;
                for (var other : List.copyOf(c.getSource().getServer().getPlayerManager().getPlayerList())) if (other != p) count += app.teleports.teleport(p, other, TeleportService.position(p), cmd);
                return count;
            }));
            case "tppos" -> b.then(argument("x", DoubleArgumentType.doubleArg(-29999999,29999999)).then(argument("y", DoubleArgumentType.doubleArg(-20000000,20000000)).then(argument("z", DoubleArgumentType.doubleArg(-29999999,29999999)).executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); var old = TeleportService.position(p);
                return app.teleports.teleport(p, new Position(old.dimension(), DoubleArgumentType.getDouble(c,"x"), DoubleArgumentType.getDouble(c,"y"), DoubleArgumentType.getDouble(c,"z"), old.yaw(), old.pitch(), System.currentTimeMillis()), cmd);
            })))));
            case "near" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); var names = p.getServerWorld().getPlayers(other -> other != p && other.squaredDistanceTo(p) <= 10000).stream().map(other -> other.getName().getString()).toList();
                return m.send(c.getSource(), "near", "names", String.join(", ", names));
            }));
            case "top" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow(); var pos = p.getServerWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p.getBlockPos());
                return app.teleports.teleport(p, at(p, pos), cmd);
            }));
            case "jump" -> b.executes(action(cmd, c -> {
                var p = c.getSource().getPlayerOrThrow();
                double reach = 100;
                if (app.settings().teleport.rejectUnloadedChunks) {
                    var eye = p.getEyePos(); var direction = p.getRotationVec(1);
                    for (int i = 1; i <= 100; i++) {
                        if (!p.getServerWorld().isChunkLoaded(BlockPos.ofFloored(eye.add(direction.multiply(i))))) { reach = i - 1; break; }
                    }
                }
                var hit = p.raycast(reach, 1, false);
                if (hit.getType() != HitResult.Type.BLOCK) return m.error(c.getSource(), "unsafe");
                return app.teleports.teleport(p, at(p, BlockPos.ofFloored(hit.getPos()).up()), cmd);
            }));
            case "rtp" -> b.executes(action(cmd, this::rtp));
            case "shirokaiessentials" -> admin(b);
            default -> new ExtraCommands(app, this).build(cmd, root);
        };
    }
    private int home(CommandContext<ServerCommandSource> c, String n) throws IOException, CommandSyntaxException {
        var p = c.getSource().getPlayerOrThrow(); var pos = app.data().player(p.getUuid()).homes.get(n);
        if (pos == null) return m.error(c.getSource(), "missing");
        if (app.settings().homes.blockedWorlds.contains(pos.dimension()) || app.settings().homes.blockedWorlds.contains(p.getServerWorld().getRegistryKey().getValue().toString())) return m.error(c.getSource(), "unsafe");
        return app.teleports.teleport(p, pos, "home");
    }
    private int setHome(CommandContext<ServerCommandSource> c, String n) throws IOException, CommandSyntaxException {
        if (!valid(c.getSource(), n)) return 0;
        var p = c.getSource().getPlayerOrThrow(); var homes = app.data().player(p.getUuid()).homes;
        int limit = app.permissions.homeLimit(c.getSource());
        if (app.settings().feature("home_limits") && !homes.containsKey(n) && homes.size() >= limit && !app.permissions.has(c.getSource(), "bypass.homelimit")) return m.error(c.getSource(), "limit", "limit", limit);
        var pos = TeleportService.position(p);
        if (app.settings().homes.blockedWorlds.contains(pos.dimension())) return m.error(c.getSource(), "unsafe");
        app.data().update(p.getUuid(), d -> d.homes.put(n, pos)); return m.send(c.getSource(), "saved", "name", n);
    }
    private int setWarp(CommandContext<ServerCommandSource> c, boolean isPublic) throws IOException, CommandSyntaxException {
        String n = name(c); if (!valid(c.getSource(), n)) return 0;
        var p = c.getSource().getPlayerOrThrow(); var warp = new DataStore.Warp(TeleportService.position(p), p.getUuid(), isPublic);
        app.data().updateWorld(d -> d.warps.put(n, warp)); return m.send(c.getSource(), "saved", "name", n);
    }
    private boolean canWarp(ServerCommandSource s, String n, DataStore.Warp w) {
        boolean owner = s.isExecutedByPlayer() && s.getPlayer().getUuid().equals(w.owner);
        if (!w.isPublic && !owner && !app.permissions.has(s, "warp.private")) return false;
        return !app.settings().warps.individualPermissions || app.permissions.has(s, "warp." + n);
    }
    private int request(CommandContext<ServerCommandSource> c, boolean here) throws CommandSyntaxException, IOException {
        var p = c.getSource().getPlayerOrThrow(); var target = EntityArgumentType.getPlayer(c, "player");
        if (p == target) return m.error(c.getSource(), "self");
        if (app.data().player(target.getUuid()).requestsBlocked || app.data().player(p.getUuid()).requestsBlocked) return m.error(c.getSource(), "requests_off");
        var cfg = app.settings().teleport; long now = System.currentTimeMillis();
        if (!app.teleports.requests.add(new Requests.Request(p.getUuid(), target.getUuid(), here, now + cfg.requestExpirySeconds * 1000L), cfg.maxPendingRequests, cfg.maxPendingTeleports, now)) return m.error(c.getSource(), "busy");
        m.send(target.getCommandSource(), "request_received", "player", p.getName().getString(), "type", here ? "você vai até ele" : "ele vem até você");
        return m.send(c.getSource(), "request_sent", "player", target.getName().getString());
    }
    private int respond(CommandContext<ServerCommandSource> c, UUID sender, boolean accept) throws CommandSyntaxException, IOException {
        var recipient = c.getSource().getPlayerOrThrow();
        var found = app.teleports.requests.find(recipient.getUuid(), sender, System.currentTimeMillis());
        if (found.isEmpty()) return m.error(c.getSource(), "request_missing");
        var r = found.get(); var p = c.getSource().getServer().getPlayerManager().getPlayer(r.sender());
        app.teleports.requests.remove(r.sender());
        if (!accept) { if (p != null) m.send(p.getCommandSource(), "cancelled"); return m.send(c.getSource(), "cancelled"); }
        String cmd = r.here() ? "tpahere" : "tpa";
        if (p == null || !allowed(p.getCommandSource(), cmd)) return m.error(c.getSource(), "request_missing");
        var moving = r.here() ? recipient : p; var destination = r.here() ? p : recipient;
        return app.teleports.teleport(p, moving, TeleportService.position(destination), cmd);
    }
    private Position at(ServerPlayerEntity p, BlockPos pos) { return new Position(p.getServerWorld().getRegistryKey().getValue().toString(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, p.getYaw(), p.getPitch(), System.currentTimeMillis()); }
    private int rtp(CommandContext<ServerCommandSource> c) throws IOException, CommandSyntaxException {
        var p = c.getSource().getPlayerOrThrow(); var cfg = app.settings().teleport;
        // Bounded search over loaded chunks. No chunk generation, futures, or tick-blocking I/O.
        var random = p.getRandom();
        for (int i = 0; i < cfg.rtpAttempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2; int radius = cfg.rtpMinRadius + random.nextInt(cfg.rtpRadius - cfg.rtpMinRadius);
            int x = p.getBlockX() + (int)(Math.cos(angle) * radius), z = p.getBlockZ() + (int)(Math.sin(angle) * radius);
            if (Math.abs((long)x) > 29999999 || Math.abs((long)z) > 29999999) continue;
            var pos = new BlockPos(x, 0, z); if (!p.getServerWorld().isChunkLoaded(pos)) { if (cfg.asyncChunkLoading) return app.teleports.randomSurface(p,x,z); continue; }
            var top = p.getServerWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos); var dest = at(p, top);
            if (app.teleports.safe(p.getServer(), dest)) return app.teleports.teleport(p, dest, "rtp");
        }
        return m.error(c.getSource(), "unsafe");
    }
    private LiteralArgumentBuilder<ServerCommandSource> admin(LiteralArgumentBuilder<ServerCommandSource> b) {
        var cfg = app.settings();
        if (cfg.subcommand("status")) b.then(literal("status").requires(s -> app.permissions.has(s, "status")).executes(action("shirokaiessentials", c -> {
            return m.send(c.getSource(), "status", "version", ShirokaiEssentials.VERSION, "modules", app.activeModules(), "integrations", app.integrations());
        })));
        if (cfg.subcommand("version")) b.then(literal("version").requires(s -> app.permissions.has(s, "version")).executes(action("shirokaiessentials", c -> { return m.send(c.getSource(), "version", "version", ShirokaiEssentials.VERSION); })));
        if (cfg.subcommand("reload")) b.then(literal("reload").requires(s -> app.permissions.has(s, "reload")).executes(action("shirokaiessentials", c -> app.reload(c.getSource()))));
        if (cfg.subcommand("conflicts")) {
            var conflicts = literal("conflicts").requires(s -> app.permissions.has(s, "conflicts")).executes(action("shirokaiessentials", c -> {
                return m.send(c.getSource(), "conflicts", "conflicts", app.conflicts.entries());
            }));
            if (cfg.subcommand("report")) conflicts.then(literal("report").requires(s -> app.permissions.has(s, "conflicts.report")).executes(action("shirokaiessentials", c -> {
                var path = app.configDir.resolve("conflicts-report.json"); app.conflicts.report(path); return m.send(c.getSource(), "report", "path", path.toString());
            })));
            b.then(conflicts);
        }
        return b;
    }
}
