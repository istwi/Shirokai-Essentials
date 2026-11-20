package dev.shirokai.test;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.shirokai.essentials.ShirokaiEssentials;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import java.nio.file.*;
import java.util.*;

/** Real LuckPerms and command dispatcher, with two simulated server players. */
public final class EnderChestChecks implements DedicatedServerModInitializer {
    private boolean ran;
    private final JsonArray checks = new JsonArray();
    public void onInitializeServer() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran || !Boolean.getBoolean("essentials.permissionChecks") || !server.getServerIp().equals("127.0.0.1") || server.getTicks() < 60) return;
            ran = true;
            try { run(server); }
            catch (Throwable error) { var row = new JsonObject(); row.addProperty("fatal", error.toString()); checks.add(row); error.printStackTrace(); }
            finally { try { Files.writeString(Path.of("enderchest-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(checks)); } catch (Exception error) { throw new RuntimeException(error); } }
        });
    }
    private static final class Player extends ServerPlayerEntity {
        Player(MinecraftServer server, String name) {
            super(server, server.getOverworld(), new GameProfile(UUID.randomUUID(), name), SyncedClientOptions.createDefault());
            networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), this, ConnectedClientData.createDefault(getGameProfile(), false));
        }
        public void sendMessage(Text text) { System.out.println("TEST MESSAGE: " + text.getString()); }
        public void sendMessage(Text text, boolean actionBar) { sendMessage(text); }
    }
    private void check(String name, boolean passed) {
        var row = new JsonObject(); row.addProperty("name", name); row.addProperty("passed", passed); checks.add(row);
        System.out.println("ENDERCHEST CHECK " + (passed ? "PASS " : "FAIL ") + name);
    }
    private int execute(MinecraftServer server, net.minecraft.server.command.ServerCommandSource source, String command) {
        try { return server.getCommandManager().getDispatcher().execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException denied) { return 0; }
    }
    private void run(MinecraftServer server) throws Exception {
        var app = (ShirokaiEssentials) net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypoints("server", DedicatedServerModInitializer.class).stream().filter(x -> x instanceof ShirokaiEssentials).findFirst().orElseThrow();
        var alice = new Player(server, "ChestAlice");
        var bob = new Player(server, "ChestBob");
        server.getPlayerManager().getPlayerList().add(alice);
        server.getPlayerManager().getPlayerList().add(bob);
        var api = LuckPermsProvider.get();
        var user = api.getUserManager().loadUser(alice.getUuid(), "ChestAlice").join();
        user.data().clear();
        var own = PermissionNode.builder("shirokai.essentials.enderchest").build();
        var others = PermissionNode.builder("shirokai.essentials.enderchest.others").build();
        user.data().add(own); user.getCachedData().invalidate();
        alice.getEnderChestInventory().setStack(0, new ItemStack(Items.DIAMOND, 2));
        bob.getEnderChestInventory().setStack(0, new ItemStack(Items.EMERALD, 5));
        var source = alice.getCommandSource();
        check("non operator player", !source.hasPermissionLevel(2));
        var raw = user.getCachedData().getPermissionData().queryPermission("shirokai.essentials.enderchest.others");
        System.out.println("LP RAW OTHERS: " + raw.result() + " origin=" + (raw.node() == null ? "null" : raw.node().getKey()));
        check("own grant allowed", app.permissions.has(source, "enderchest"));
        check("own grant never grants other chest", !app.permissions.has(source, "enderchest.others"));
        int result = execute(server, source, "enderchest");
        check("own command opens own chest", result == 1 && alice.currentScreenHandler.getSlot(0).getStack().isOf(Items.DIAMOND));
        alice.closeHandledScreen();
        result = execute(server, source, "enderchest ChestBob");
        check("targeted command denied with own grant only", result == 0 && alice.currentScreenHandler == alice.playerScreenHandler);
        alice.closeHandledScreen();
        check("alias cannot bypass others permission", execute(server, source, "ec ChestBob") == 0 && alice.currentScreenHandler == alice.playerScreenHandler);
        alice.closeHandledScreen();
        check("selector cannot bypass others permission", execute(server, source, "enderchest @a") == 0 && alice.currentScreenHandler == alice.playerScreenHandler);
        alice.closeHandledScreen();
        // Service authorization must also reject a direct call, outside command parsing.
        result = app.utilities.execute(source, "enderchest", bob, 1);
        check("service denies other chest independently", result == 0 && alice.currentScreenHandler == alice.playerScreenHandler);
        user.data().add(others); user.getCachedData().invalidate();
        result = execute(server, source, "enderchest ChestBob");
        check("explicit others permission opens target chest", result == 1 && alice.currentScreenHandler.getSlot(0).getStack().isOf(Items.EMERALD));
        alice.closeHandledScreen();
        var parsed = server.getCommandManager().getDispatcher().parse("enderchest ChestBob", source);
        user.data().remove(others); user.getCachedData().invalidate();
        int cachedResult;
        try { cachedResult = server.getCommandManager().getDispatcher().execute(parsed); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException denied) { cachedResult = 0; }
        check("parsed command rechecks revoked others permission", cachedResult == 0 && alice.currentScreenHandler == alice.playerScreenHandler);
        alice.closeHandledScreen();
        check("revoked others permission denied", execute(server, source, "enderchest ChestBob") == 0 && alice.currentScreenHandler == alice.playerScreenHandler);
        alice.closeHandledScreen();
        var group = api.getGroupManager().createAndLoadGroup("enderchest-checks").join();
        group.data().add(others);
        var inheritance = net.luckperms.api.node.types.InheritanceNode.builder(group.getName()).build();
        user.data().add(inheritance); user.getCachedData().invalidate();
        check("explicit others inherited from admin group allowed", app.permissions.has(source, "enderchest.others"));
        user.data().remove(inheritance); user.getCachedData().invalidate();
        check("operator fallback retained", app.permissions.has(source.withLevel(4), "enderchest.others"));
        var wildcard = PermissionNode.builder("shirokai.essentials.*").build();
        user.data().add(wildcard); user.getCachedData().invalidate();
        check("administrative wildcard retained", app.permissions.has(source,"enderchest.others"));
        var denial = PermissionNode.builder("shirokai.essentials.enderchest.others").value(false).build();
        user.data().add(denial); user.getCachedData().invalidate();
        check("explicit denial wins wildcard", !app.permissions.has(source,"enderchest.others"));
        check("explicit denial wins operator fallback", !app.permissions.has(source.withLevel(4),"enderchest.others"));
        check("inventories unchanged after rejected requests", alice.getEnderChestInventory().getStack(0).getCount() == 2 && bob.getEnderChestInventory().getStack(0).getCount() == 5);
        server.getPlayerManager().getPlayerList().remove(alice);
        server.getPlayerManager().getPlayerList().remove(bob);
    }
}
