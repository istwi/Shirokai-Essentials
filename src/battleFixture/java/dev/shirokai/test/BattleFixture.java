package dev.shirokai.test;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Calls the real Cobblemon battle builder. No mock or modification of its registry. */
public final class BattleFixture implements DedicatedServerModInitializer {
    @Override public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(CommandManager.literal("shirokai_test_battle").requires(s -> s.hasPermissionLevel(4))
                .then(CommandManager.literal("start").executes(ctx -> {
                    try {
                        var players = ctx.getSource().getServer().getPlayerManager();
                        var alice = players.getPlayer("BattleAlice");
                        var bob = players.getPlayer("BattleBob");
                        if (alice == null || bob == null) throw new IllegalStateException("Missing battle participants");
                        Class<?> builder = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
                        Object result = builder.getMethod("pvp1v1", ServerPlayerEntity.class, ServerPlayerEntity.class)
                            .invoke(builder.getField("INSTANCE").get(null), alice, bob);
                        ctx.getSource().sendFeedback(() -> Text.literal("BATTLE_FIXTURE_START=" + result), false);
                        if (lookup(alice) == null || lookup(bob) == null) throw new IllegalStateException("Battle not registered");
                        return 1;
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                }))
                .then(CommandManager.literal("status").executes(ctx -> {
                    try {
                        Object battle = lookup(ctx.getSource().getServer().getPlayerManager().getPlayer("BattleAlice"));
                        String status = battle == null ? "absent" : "started=" + battle.getClass().getMethod("getStarted").invoke(battle)
                            + ";ended=" + battle.getClass().getMethod("getEnded").invoke(battle)
                            + ";turn1=" + battle.getClass().getMethod("getShowdownMessages").invoke(battle).toString().contains("|turn|1");
                        ctx.getSource().sendFeedback(() -> Text.literal("BATTLE_FIXTURE_STATUS=" + status), false);
                        return 1;
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                }))
                .then(CommandManager.literal("systemteleport").executes(ctx -> {
                    var player=ctx.getSource().getServer().getPlayerManager().getPlayer("BattleAlice");
                    try {
                        var destination=new dev.shirokai.essentials.persistence.Position("minecraft:overworld",.5,64,.5,0,0,System.currentTimeMillis());
                        int result=dev.shirokai.essentials.ShirokaiEssentials.instance().teleports.systemTeleport(player,destination);
                        ctx.getSource().sendFeedback(() -> Text.literal("BATTLE_FIXTURE_SYSTEM_TELEPORT="+result),false);
                        return 1;
                    } catch(java.io.IOException e){throw new IllegalStateException(e);}
                }))
                .then(CommandManager.literal("stop").executes(ctx -> {
                    try {
                        Object battle = lookup(ctx.getSource().getServer().getPlayerManager().getPlayer("BattleAlice"));
                        if (battle == null) throw new IllegalStateException("No battle to stop");
                        battle.getClass().getMethod("stop").invoke(battle);
                        ctx.getSource().sendFeedback(() -> Text.literal("BATTLE_FIXTURE_STOPPED"), false);
                        return 1;
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                })));
        });
    }
    private static Object lookup(ServerPlayerEntity player) throws ReflectiveOperationException {
        if (player == null) throw new IllegalStateException("Missing player");
        return Class.forName("com.cobblemon.mod.common.battles.BattleRegistry")
            .getMethod("getBattleByParticipatingPlayerId", java.util.UUID.class).invoke(null, player.getUuid());
    }
}
