package dev.shirokai.essentials.utilities;

import dev.shirokai.essentials.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.IOException;
import java.util.*;

public final class UtilityService {
    private final ShirokaiEssentials app;
    private final Set<UUID> god = new HashSet<>(), flight = new HashSet<>();
    public UtilityService(ShirokaiEssentials app){this.app=app;}
    public boolean flight(ServerPlayerEntity p){return app.settings().module("utilities")&&app.settings().feature("fly")&&flight.contains(p.getUuid());}
    public boolean god(ServerPlayerEntity p){return app.settings().module("utilities")&&app.settings().feature("god_mode")&&god.contains(p.getUuid());}
    public void join(ServerPlayerEntity p) throws IOException {
        var d=app.data().player(p.getUuid());
        boolean fly=app.settings().module("utilities")&&app.settings().feature("fly")&&app.settings().utilities.persistModes&&d.fly&&app.permissions.has(p.getCommandSource(),"fly");
        if(d.managedFlight){p.getAbilities().allowFlying=fly||p.isCreative()||p.isSpectator();if(!p.getAbilities().allowFlying)p.getAbilities().flying=false;p.sendAbilitiesUpdate();}
        if(fly)flight.add(p.getUuid());
        if(app.settings().module("utilities")&&app.settings().feature("god_mode")&&app.settings().utilities.persistModes&&d.god&&app.permissions.has(p.getCommandSource(),"god"))god.add(p.getUuid());
    }
    public int execute(ServerCommandSource source,String cmd,ServerPlayerEntity target,int speed) throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(source.getEntity()!=target && !app.permissions.has(source,cmd+".others"))return app.messages.error(source,"denied");
        app.data().audit(source.getName(),cmd,target.getUuidAsString(),"");
        switch(cmd){
            case "fly"->{
                boolean enabled=!flight.contains(target.getUuid());
                app.data().update(target.getUuid(),d->{d.managedFlight=true;d.fly=app.settings().utilities.persistModes&&enabled;});
                if(enabled)flight.add(target.getUuid());else flight.remove(target.getUuid());
                target.getAbilities().allowFlying=enabled||target.isCreative()||target.isSpectator();
                if(!target.getAbilities().allowFlying)target.getAbilities().flying=false;target.sendAbilitiesUpdate();
            }
            case "god"->{boolean enabled=!god.contains(target.getUuid());app.data().update(target.getUuid(),d->d.god=app.settings().utilities.persistModes&&enabled);if(enabled)god.add(target.getUuid());else god.remove(target.getUuid());}
            case "heal"->{target.setHealth(target.getMaxHealth());target.clearStatusEffects();}
            case "feed"->{target.getHungerManager().setFoodLevel(20);target.getHungerManager().setSaturationLevel(20);}
            case "speed"->{if(target.getAbilities().flying)SpeedControl.fly(target,speed);else SpeedControl.walk(target,speed);}
            case "repair"->{var item=target.getMainHandStack();if(!item.isDamageable())return app.messages.error(source,"invalid","value","item sem durabilidade");item.setDamage(0);target.currentScreenHandler.sendContentUpdates();return app.messages.send(source,"repair_done","player",target.getName().getString());}
            case "hat"->{ItemStack item=target.getMainHandStack();ItemStack hat=target.getEquippedStack(EquipmentSlot.HEAD);target.equipStack(EquipmentSlot.HEAD,item.copy());target.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,hat.copy());}
            case "clearinventory"->{target.getInventory().clear();target.currentScreenHandler.sendContentUpdates();}
            case "invsee"->{
                var viewer=source.getPlayerOrThrow();var inv=new SimpleInventory(54);
                for(int i=0;i<target.getInventory().size();i++)inv.setStack(i,target.getInventory().getStack(i).copy());
                viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inventory,p)->new ReadOnlyScreen(id,inventory,inv),app.messages.text("inventory_title","player",target.getName().getString())));
            }
            case "enderchest"->{var viewer=source.getPlayerOrThrow();viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,p)->GenericContainerScreenHandler.createGeneric9x3(id,inv,target.getEnderChestInventory()),app.messages.text("enderchest_title")));}
            default->throw new IllegalArgumentException(cmd);
        }
        return app.messages.send(source,"utility","feature",cmd,"player",target.getName().getString());
    }
    public int setSpeed(ServerCommandSource source, ServerPlayerEntity target, int level, boolean flying) throws IOException {
        if(source.getEntity()!=target&&!app.permissions.has(source,"speed.others"))return app.messages.error(source,"denied");
        app.data().audit(source.getName(),"speed-"+(flying?"fly":"walk"),target.getUuidAsString(),Integer.toString(level));
        if(flying)SpeedControl.fly(target,level);else SpeedControl.walk(target,level);
        return app.messages.send(source,"utility","feature","speed "+(flying?"fly":"walk")+" "+level,"player",target.getName().getString());
    }
    public int resetSpeed(ServerCommandSource source,ServerPlayerEntity target) throws IOException {
        if(source.getEntity()!=target&&!app.permissions.has(source,"speed.others"))return app.messages.error(source,"denied");
        app.data().audit(source.getName(),"speed-reset",target.getUuidAsString(),"");
        SpeedControl.reset(target);
        return app.messages.send(source,"utility","feature","speed reset","player",target.getName().getString());
    }
    public int light(ServerPlayerEntity player) {
        var effect = net.minecraft.entity.effect.StatusEffects.NIGHT_VISION;
        boolean enabled = !player.hasStatusEffect(effect);
        if (enabled) player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(effect, -1, 0, false, false, true));
        else player.removeStatusEffect(effect);
        return app.messages.send(player.getCommandSource(), enabled ? "light_on" : "light_off");
    }
    public int smelt(ServerPlayerEntity player) throws IOException {
        var source = player.getCommandSource();
        long now = System.currentTimeMillis();
        long next = app.data().player(player.getUuid()).nextSmeltAt;
        if (next > now) {
            long seconds = (next-now+999)/1000;
            String wait = seconds < 60 ? seconds + "s" : seconds/60 + "min" + (seconds%60 == 0 ? "" : " " + seconds%60 + "s");
            return app.messages.error(source,"smelt_wait","time",wait);
        }
        ItemStack input = player.getMainHandStack();
        String id = net.minecraft.registry.Registries.ITEM.getId(input.getItem()).toString();
        if (input.isEmpty() || !app.settings().utilities.smeltItems.contains(id)) return app.messages.error(source,"smelt_invalid");
        var recipeInput = new net.minecraft.recipe.input.SingleStackRecipeInput(input);
        var recipe = player.getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.SMELTING, recipeInput, player.getServerWorld());
        if (recipe.isEmpty()) return app.messages.error(source,"smelt_invalid");
        ItemStack output = recipe.get().value().craft(recipeInput, player.getRegistryManager());
        if (output.isEmpty()) return app.messages.error(source,"smelt_invalid");
        long count = (long) output.getCount() * input.getCount();
        if (count > output.getMaxCount()) return app.messages.error(source,"smelt_space");
        int consumed = input.getCount();
        output.setCount((int) count);
        app.data().audit(source.getName(),"esquentar",id,"count="+consumed);
        app.data().update(player.getUuid(), data -> data.nextSmeltAt = now + app.settings().utilities.smeltCooldownSeconds * 1000L);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, output);
        player.currentScreenHandler.sendContentUpdates();
        return app.messages.send(source,"smelt_done","count",consumed,"item",output.getName().getString());
    }
    public int trash(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,p)->new TrashScreen(id,inv,player,app), app.messages.text("trash_title")));
        return 1;
    }
    public int compact(ServerPlayerEntity player) throws IOException {
        var input = player.getMainHandStack();
        var blocks = Map.of(net.minecraft.item.Items.IRON_INGOT,net.minecraft.item.Items.IRON_BLOCK,
            net.minecraft.item.Items.GOLD_INGOT,net.minecraft.item.Items.GOLD_BLOCK,
            net.minecraft.item.Items.COPPER_INGOT,net.minecraft.item.Items.COPPER_BLOCK,
            net.minecraft.item.Items.NETHERITE_INGOT,net.minecraft.item.Items.NETHERITE_BLOCK);
        var target = blocks.get(input.getItem());
        if (target == null || input.getCount() < 9 || !input.getComponentChanges().isEmpty()) return app.messages.error(player.getCommandSource(),"compact_invalid");
        var grid = net.minecraft.recipe.input.CraftingRecipeInput.create(3,3,java.util.Collections.nCopies(9,input.copyWithCount(1)));
        var recipe = player.getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,grid,player.getServerWorld());
        if (recipe.isEmpty()) return app.messages.error(player.getCommandSource(),"compact_recipe");
        var output = recipe.get().value().craft(grid,player.getRegistryManager());
        if (!output.isOf(target) || output.getCount()!=1 || recipe.get().value().getRemainder(grid).stream().anyMatch(stack->!stack.isEmpty())) return app.messages.error(player.getCommandSource(),"compact_recipe");
        int count = input.getCount()/9, remainder = input.getCount()%9;
        if (count>output.getMaxCount()) return app.messages.error(player.getCommandSource(),"compact_space");
        output.setCount(count);
        var inventory = player.getInventory();
        var proposed = inventory.main.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        proposed.set(inventory.selectedSlot,output);
        var plan = dev.shirokai.essentials.kits.InventoryPlan.create(proposed,remainder==0 ? List.of() : List.of(input.copyWithCount(remainder)));
        if (plan.isEmpty()) return app.messages.error(player.getCommandSource(),"compact_space");
        app.data().audit(player.getName().getString(),"compactar",net.minecraft.registry.Registries.ITEM.getId(input.getItem()).toString(),"blocks="+count+" remainder="+remainder);
        for (int i=0;i<inventory.main.size();i++) inventory.main.set(i,plan.get().get(i));
        inventory.markDirty();player.currentScreenHandler.sendContentUpdates();
        return app.messages.send(player.getCommandSource(),"compact_done","count",count,"remainder",remainder);
    }
    public int station(ServerPlayerEntity p,String cmd) {
        String title = switch (cmd) { case "workbench" -> "workbench_title"; case "anvil" -> "anvil_title"; case "grindstone" -> "grindstone_title"; case "cortador" -> "stonecutter_title"; case "tear" -> "loom_title"; default -> throw new IllegalArgumentException(cmd); };
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,user)->VirtualStations.create(cmd,id,inv,p),app.messages.text(title)));
        return 1;
    }
    public void disconnect(UUID id){god.remove(id);flight.remove(id);}
    public void clear(){god.clear();flight.clear();}
}
