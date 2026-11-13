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
            case "speed"->{if(target.getAbilities().flying)target.getAbilities().setFlySpeed(speed/20f);else target.getAbilities().setWalkSpeed(speed/20f);target.sendAbilitiesUpdate();}
            case "repair"->{var item=target.getMainHandStack();if(!item.isDamageable())return app.messages.error(source,"invalid","value","item sem durabilidade");item.setDamage(0);target.currentScreenHandler.sendContentUpdates();}
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
    public int station(ServerPlayerEntity p,String cmd){
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,user)->cmd.equals("workbench")?new CraftingScreenHandler(id,inv,ScreenHandlerContext.EMPTY):new AnvilScreenHandler(id,inv,ScreenHandlerContext.EMPTY),app.messages.text(cmd.equals("workbench")?"workbench_title":"anvil_title")));return 1;
    }
    public void disconnect(UUID id){god.remove(id);flight.remove(id);}
    public void clear(){god.clear();flight.clear();}
}
