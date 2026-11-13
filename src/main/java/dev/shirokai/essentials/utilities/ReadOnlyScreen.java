package dev.shirokai.essentials.utilities;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;

/** Even forged click/quick-move packets cannot extract snapshot/preview items. */
public final class ReadOnlyScreen extends GenericContainerScreenHandler {
    public ReadOnlyScreen(int id, PlayerInventory player, Inventory inventory) { super(ScreenHandlerType.GENERIC_9X6,id,player,inventory,6); }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player){sendContentUpdates();}
    @Override public ItemStack quickMove(PlayerEntity player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canUse(PlayerEntity player){return true;}
}
