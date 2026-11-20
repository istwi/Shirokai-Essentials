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
        utilityChecks(server, app, alice, user);
        moreUtilities(server, app, alice, user);
        server.getPlayerManager().getPlayerList().remove(alice);
        server.getPlayerManager().getPlayerList().remove(bob);
    }
    private void utilityChecks(MinecraftServer server, ShirokaiEssentials app, Player player, net.luckperms.api.model.user.User user) throws Exception {
        user.data().clear(); user.getCachedData().invalidate();
        var source=player.getCommandSource();
        check("luz denied without permission", execute(server,source,"luz")==0 && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION));
        for (String name : List.of("workbench","anvil","grindstone","repair","luz","esquentar")) user.data().add(PermissionNode.builder("shirokai.essentials."+name).build());
        user.getCachedData().invalidate();
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED,1200));
        check("luz enables night vision",execute(server,source,"luz")==1 && player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION));
        check("luz disables only night vision",execute(server,source,"luz")==1 && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION) && player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED));
        for (String name : List.of("craft","bancada","bigorna","rebolo")) {
            player.getInventory().clear();execute(server,source,name);
            boolean type = switch(name) {case "craft","bancada" -> player.currentScreenHandler instanceof net.minecraft.screen.CraftingScreenHandler;case "bigorna"->player.currentScreenHandler instanceof net.minecraft.screen.AnvilScreenHandler;default->player.currentScreenHandler instanceof net.minecraft.screen.GrindstoneScreenHandler;};
            check(name+" opens correct virtual station",type && player.currentScreenHandler.canUse(player));
            int input=name.equals("craft")||name.equals("bancada")?1:0;
            player.currentScreenHandler.getSlot(input).setStack(new ItemStack(Items.DIAMOND,3));
            player.closeHandledScreen();
            check(name+" returns inputs on close",player.getInventory().count(Items.DIAMOND)==3);
        }
        player.getInventory().clear();execute(server,source,"craft");
        player.currentScreenHandler.getSlot(1).setStack(new ItemStack(Items.OAK_LOG));
        var output=player.currentScreenHandler.getSlot(0);
        check("craft calculates vanilla recipe",output.getStack().isOf(Items.OAK_PLANKS) && output.getStack().getCount()==4);
        var crafted=output.takeStack(4);output.onTakeItem(player,crafted);
        check("craft consumes input when taking result",player.currentScreenHandler.getSlot(1).getStack().isEmpty() && crafted.getCount()==4);
        player.closeHandledScreen();
        player.getInventory().clear();execute(server,source,"bigorna");player.addExperienceLevels(30-player.experienceLevel);
        var anvil=(net.minecraft.screen.AnvilScreenHandler)player.currentScreenHandler;
        anvil.getSlot(0).setStack(new ItemStack(Items.DIAMOND_PICKAXE));anvil.setNewItemName("Ferramenta teste");
        check("bigorna computes renamed item",!anvil.getSlot(2).getStack().isEmpty() && anvil.getSlot(2).getStack().getName().getString().equals("Ferramenta teste"));
        int cost=anvil.getLevelCost();var renamed=anvil.getSlot(2).takeStack(1);anvil.getSlot(2).onTakeItem(player,renamed);
        check("bigorna consumes input and charges vanilla levels",cost>0 && player.experienceLevel==30-cost && anvil.getSlot(0).getStack().isEmpty());player.closeHandledScreen();
        execute(server,source,"rebolo");var grind=player.currentScreenHandler;
        var worn=new ItemStack(Items.DIAMOND_PICKAXE);worn.setDamage(1000);grind.getSlot(0).setStack(worn.copy());grind.getSlot(1).setStack(worn.copy());
        check("rebolo computes vanilla tool repair",!grind.getSlot(2).getStack().isEmpty() && grind.getSlot(2).getStack().getDamage()<1000);
        var repaired=grind.getSlot(2).takeStack(1);grind.getSlot(2).onTakeItem(player,repaired);
        check("rebolo consumes both input tools",grind.getSlot(0).getStack().isEmpty() && grind.getSlot(1).getStack().isEmpty());player.closeHandledScreen();
        player.getInventory().clear();var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setDamage(100);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,tool);
        check("reparar repairs held tool",execute(server,source,"reparar")==1 && player.getMainHandStack().getDamage()==0);
        player.getInventory().clear();player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.RAW_IRON,64));
        check("esquentar converts complete stack",execute(server,source,"esquentar")==1 && player.getMainHandStack().isOf(Items.IRON_INGOT) && player.getMainHandStack().getCount()==64);
        long until=app.data().player(player.getUuid()).nextSmeltAt;
        check("esquentar sets five minute persistent cooldown",until-System.currentTimeMillis()>298000 && until-System.currentTimeMillis()<=300000);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.RAW_GOLD,5));
        check("cooldown blocks conversion without consuming ore",execute(server,source,"esquentar")==0 && player.getMainHandStack().isOf(Items.RAW_GOLD) && player.getMainHandStack().getCount()==5 && app.data().player(player.getUuid()).nextSmeltAt==until);
        app.data().update(player.getUuid(),d->d.nextSmeltAt=0);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.BEEF,3));
        check("food rejected without consuming cooldown",execute(server,source,"esquentar")==0 && player.getMainHandStack().isOf(Items.BEEF) && app.data().player(player.getUuid()).nextSmeltAt==0);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.DEEPSLATE_GOLD_ORE,8));
        check("deepslate ore follows furnace recipe",execute(server,source,"esquentar")==1 && player.getMainHandStack().isOf(Items.GOLD_INGOT) && player.getMainHandStack().getCount()==8);
        app.data().update(player.getUuid(),d->d.nextSmeltAt=0);
    }

    private void moreUtilities(MinecraftServer server, ShirokaiEssentials app, Player player, net.luckperms.api.model.user.User user) throws Exception {
        var source=player.getCommandSource();player.closeHandledScreen();player.getInventory().clear();
        check("trash denied without permission",execute(server,source,"lixeira")==0 && player.currentScreenHandler==player.playerScreenHandler);
        for(String cmd:List.of("lixeira","compactar","cortador","tear"))user.data().add(PermissionNode.builder("shirokai.essentials."+cmd).build());user.getCachedData().invalidate();
        for(String cmd:List.of("cortador","tear")) {
            execute(server,source,cmd);var screen=player.currentScreenHandler;
            check(cmd+" opens correct vanilla station",(cmd.equals("cortador")?screen instanceof net.minecraft.screen.StonecutterScreenHandler:screen instanceof net.minecraft.screen.LoomScreenHandler)&&screen.canUse(player));
            screen.getSlot(0).setStack(new ItemStack(cmd.equals("cortador")?Items.STONE:Items.WHITE_BANNER,1));player.closeHandledScreen();
            check(cmd+" returns input on close",player.getInventory().count(cmd.equals("cortador")?Items.STONE:Items.WHITE_BANNER)==1);player.getInventory().clear();
        }
        execute(server,source,"cortador");var cutter=(net.minecraft.screen.StonecutterScreenHandler)player.currentScreenHandler;
        cutter.getSlot(0).setStack(new ItemStack(Items.STONE,2));cutter.onButtonClick(player,0);
        check("stonecutter computes real recipe",!cutter.getSlot(1).getStack().isEmpty());
        var cut=cutter.getSlot(1).takeStack(cutter.getSlot(1).getStack().getCount());cutter.getSlot(1).onTakeItem(player,cut);
        check("stonecutter consumes one input",cutter.getSlot(0).getStack().getCount()==1);player.closeHandledScreen();player.getInventory().clear();
        execute(server,source,"tear");var loom=(net.minecraft.screen.LoomScreenHandler)player.currentScreenHandler;
        loom.getBannerSlot().setStack(new ItemStack(Items.WHITE_BANNER));loom.getDyeSlot().setStack(new ItemStack(Items.RED_DYE,2));loom.onButtonClick(player,0);
        check("loom computes real banner pattern",!loom.getOutputSlot().getStack().isEmpty());
        var banner=loom.getOutputSlot().takeStack(1);loom.getOutputSlot().onTakeItem(player,banner);
        check("loom consumes banner and one dye",loom.getBannerSlot().getStack().isEmpty()&&loom.getDyeSlot().getStack().getCount()==1);player.closeHandledScreen();player.getInventory().clear();
        for(var material:List.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT,Items.NETHERITE_INGOT)) {
            player.getInventory().clear();player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(material,64));
            var block=Map.of(Items.IRON_INGOT,Items.IRON_BLOCK,Items.GOLD_INGOT,Items.GOLD_BLOCK,Items.COPPER_INGOT,Items.COPPER_BLOCK,Items.NETHERITE_INGOT,Items.NETHERITE_BLOCK).get(material);
            check("compact 64 "+material+" into 7 blocks and 1 remainder",execute(server,source,"compactar")==1 && player.getMainHandStack().isOf(block)&&player.getMainHandStack().getCount()==7&&player.getInventory().count(material)==1);
        }
        player.getInventory().clear();player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,8));
        check("compact rejects less than 9 without mutation",execute(server,source,"compactar")==0&&player.getMainHandStack().getCount()==8);
        player.getMainHandStack().set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,Text.literal("Moeda especial"));player.getMainHandStack().setCount(18);
        check("compact rejects custom currency bars",execute(server,source,"compactar")==0&&player.getMainHandStack().getName().getString().equals("Moeda especial")&&player.getMainHandStack().getCount()==18);
        for(int n=0;n<36;n++)player.getInventory().main.set(n,new ItemStack(Items.STONE,64));player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,64));
        check("compact full inventory with remainder refuses safely",execute(server,source,"compactar")==0&&player.getMainHandStack().isOf(Items.IRON_INGOT)&&player.getMainHandStack().getCount()==64);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,63));
        check("compact full inventory with no remainder succeeds",execute(server,source,"compactar")==1&&player.getMainHandStack().isOf(Items.IRON_BLOCK)&&player.getMainHandStack().getCount()==7);
        player.getInventory().clear();execute(server,source,"lixeira");var trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        trash.getSlot(0).setStack(new ItemStack(Items.DIAMOND,5));
        trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        check("trash first click only reviews",trash.getSlot(0).getStack().getCount()==5&&player.currentScreenHandler==trash);
        trash.onSlotClick(27,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        check("inventory interaction invalidates trash review",trash.getSlot(0).getStack().getCount()==5&&player.currentScreenHandler==trash);
        player.closeHandledScreen();check("trash close returns reviewed items",player.getInventory().count(Items.DIAMOND)==5);
        player.getInventory().clear();execute(server,source,"lixeira");trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        trash.getSlot(0).setStack(new ItemStack(Items.DIAMOND,5));trash.onSlotClick(26,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        check("trash cancel returns items",player.getInventory().count(Items.DIAMOND)==5&&player.currentScreenHandler==player.playerScreenHandler);
        player.getInventory().clear();player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.EMERALD,6));execute(server,source,"lixeira");trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        trash.onSlotClick(54,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,player);
        check("trash shift click moves player items into input slots",trash.getSlot(0).getStack().isOf(Items.EMERALD)&&trash.getSlot(0).getStack().getCount()==6&&player.getInventory().count(Items.EMERALD)==0);
        trash.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,player);
        check("trash shift click returns escrow without duplication",trash.getSlot(0).getStack().isEmpty()&&player.getInventory().count(Items.EMERALD)==6);player.closeHandledScreen();
        player.getInventory().clear();execute(server,source,"lixeira");trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        trash.getSlot(0).setStack(new ItemStack(Items.DIAMOND,2));trash.setCursorStack(new ItemStack(Items.GOLD_INGOT,3));trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);player.closeHandledScreen();
        check("trash preserves cursor and input on close",player.getInventory().count(Items.DIAMOND)==2&&player.getInventory().count(Items.GOLD_INGOT)==3);
        player.getInventory().clear();execute(server,source,"lixeira");trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        trash.getSlot(0).setStack(new ItemStack(Items.DIAMOND,2));trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        var audit=app.data().root().resolve("audit.jsonl");var backup=audit.resolveSibling("audit-test-backup-"+UUID.randomUUID()+".jsonl");Files.move(audit,backup);
        try {
            Files.createDirectory(audit);trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
            check("trash audit failure preserves items",trash.getSlot(0).getStack().getCount()==2&&player.currentScreenHandler==trash);
        } finally { Files.delete(audit);Files.move(backup,audit); }
        player.closeHandledScreen();check("trash audit failure still allows cancellation",player.getInventory().count(Items.DIAMOND)==2);
        player.getInventory().clear();execute(server,source,"lixeira");trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,7));
        trash.getSlot(0).setStack(new ItemStack(Items.DIAMOND,5));trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        trash.getSlot(1).setStack(new ItemStack(Items.EMERALD,3));trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        check("changed trash contents require a new review",trash.getSlot(0).getStack().getCount()==5&&trash.getSlot(1).getStack().getCount()==3);
        trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        check("trash confirmed deletion removes only escrowed items",player.currentScreenHandler==player.playerScreenHandler&&player.getInventory().count(Items.DIAMOND)==0&&player.getInventory().count(Items.EMERALD)==0&&player.getInventory().count(Items.IRON_INGOT)==7);
        player.getInventory().clear();execute(server,source,"lixeira");trash=(dev.shirokai.essentials.utilities.TrashScreen)player.currentScreenHandler;
        for(var action:net.minecraft.screen.slot.SlotActionType.values())for(int button:List.of(0,1,2,40))trash.onSlotClick(18,button,action,player);
        trash.quickMove(player,22);
        check("trash decorative buttons cannot be extracted",player.getInventory().isEmpty()&&trash.getCursorStack().isEmpty()&&!trash.getSlot(18).getStack().isEmpty());
        trash.getSlot(0).setStack(new ItemStack(Items.DIAMOND,2));user.data().add(PermissionNode.builder("shirokai.essentials.lixeira").value(false).build());user.getCachedData().invalidate();
        trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);trash.onSlotClick(22,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        check("revoked trash permission cannot delete",!trash.canUse(player)&&trash.getSlot(0).getStack().getCount()==2);player.closeHandledScreen();check("revoked trash access returns items",player.getInventory().count(Items.DIAMOND)==2);
    }

}
