package dev.shirokai.essentials.utilities;

import dev.shirokai.essentials.ShirokaiEssentials;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import java.util.*;

/** Only the first 18 slots are escrowed items. Closing/cancelling always returns them. */
public final class TrashScreen extends GenericContainerScreenHandler {
    private final ServerOwner owner;
    private final SimpleInventory contents;
    private List<ItemStack> reviewed;
    private boolean closed;
    private record ServerOwner(net.minecraft.server.network.ServerPlayerEntity player, ShirokaiEssentials app) {}
    public TrashScreen(int id, PlayerInventory inventory, net.minecraft.server.network.ServerPlayerEntity player, ShirokaiEssentials app) {
        this(id,inventory,player,app,new SimpleInventory(27));
    }
    private TrashScreen(int id, PlayerInventory inventory, net.minecraft.server.network.ServerPlayerEntity player, ShirokaiEssentials app, SimpleInventory contents) {
        super(ScreenHandlerType.GENERIC_9X3,id,inventory,contents,3);
        this.contents=contents;this.owner=new ServerOwner(player,app);
        for (int i=18;i<27;i++) {
            var original=slots.get(i);
            var locked=new Slot(contents,i,original.x,original.y) {
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public boolean canTakeItems(PlayerEntity player) { return false; }
            };
            locked.id=original.id;slots.set(i,locked);
        }
        buttons();
    }
    private void buttons() {
        for(int i=18;i<27;i++) {
            var filler=new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            filler.set(DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal(" "));contents.setStack(i,filler);
        }
        var confirm=new ItemStack(reviewed==null ? Items.LIME_DYE : Items.RED_DYE);
        confirm.set(DataComponentTypes.CUSTOM_NAME,owner.app.messages.text(reviewed==null?"trash_review":"trash_confirm","count",count()));contents.setStack(22,confirm);
        var cancel=new ItemStack(Items.ARROW);cancel.set(DataComponentTypes.CUSTOM_NAME,owner.app.messages.text("trash_cancel"));contents.setStack(26,cancel);
        sendContentUpdates();
    }
    private int count() { int total=0;for(int i=0;i<18;i++)total+=contents.getStack(i).getCount();return total; }
    private boolean matches() {
        if(reviewed==null)return false;
        for(int i=0;i<18;i++)if(!ItemStack.areEqual(reviewed.get(i),contents.getStack(i)))return false;
        return true;
    }
    @Override public boolean canUse(PlayerEntity player) {
        return !closed && player==owner.player && player.isAlive() && owner.app.settings().command("lixeira") && owner.app.permissions.has(owner.player.getCommandSource(),"lixeira");
    }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player) {
        if(!canUse(player)||owner.player.currentScreenHandler!=this)return;
        if(slot>=18&&slot<27) {
            if(action!=SlotActionType.PICKUP||button!=0)return;
            if(slot==26) { owner.player.closeHandledScreen();return; }
            if(slot!=22)return;
            if(!getCursorStack().isEmpty()) { owner.app.messages.error(owner.player.getCommandSource(),"trash_cursor");return; }
            if(count()==0) { owner.app.messages.error(owner.player.getCommandSource(),"trash_empty");return; }
            if(!matches()) {
                reviewed=new ArrayList<>();for(int i=0;i<18;i++)reviewed.add(contents.getStack(i).copy());
                buttons();owner.app.messages.send(owner.player.getCommandSource(),"trash_review_message");return;
            }
            int count=count();
            try { owner.app.data().audit(owner.player.getName().getString(),"lixeira",owner.player.getUuidAsString(),"discarded="+count); }
            catch(java.io.IOException failure) { ShirokaiEssentials.LOGGER.error("Falha de auditoria da lixeira; itens preservados",failure);owner.app.messages.error(owner.player.getCommandSource(),"storage_error");return; }
            for(int i=0;i<18;i++)contents.setStack(i,ItemStack.EMPTY);
            reviewed=null;owner.app.messages.send(owner.player.getCommandSource(),"trash_done","count",count);owner.player.closeHandledScreen();return;
        }
        reviewed=null;buttons();super.onSlotClick(slot,button,action,player);
    }
    @Override public ItemStack quickMove(PlayerEntity player,int index) {
        if(!canUse(player)||index<0||index>=slots.size()||index>=18&&index<27)return ItemStack.EMPTY;
        reviewed=null;buttons();var slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;
        var stack=slot.getStack();var before=stack.copy();
        if(index<18 ? !insertItem(stack,27,slots.size(),true) : !insertItem(stack,0,18,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();slot.onTakeItem(player,stack);return before;
    }
    @Override public void onClosed(PlayerEntity player) {
        if(closed)return;closed=true;super.onClosed(player);
        for(int i=0;i<18;i++) { var stack=contents.removeStack(i);if(!stack.isEmpty())owner.player.getInventory().offerOrDrop(stack); }
        reviewed=null;
    }
}
