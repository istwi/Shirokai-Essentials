package dev.shirokai.essentials.kits;

import net.minecraft.item.ItemStack;
import java.util.*;
public final class InventoryPlan {
    public static Optional<List<ItemStack>> create(List<ItemStack> current,List<ItemStack> rewards){
        return SlotPlanner.create(current,rewards,new SlotPlanner.Stacks<>(){
            public ItemStack copy(ItemStack item){return item.copy();}
            public boolean empty(ItemStack item){return item.isEmpty();}
            public boolean compatible(ItemStack a,ItemStack b){return ItemStack.areItemsAndComponentsEqual(a,b);}
            public int count(ItemStack item){return item.getCount();}
            public int maximum(ItemStack item){return item.getMaxCount();}
            public void add(ItemStack item,int n){item.increment(n);}
            public ItemStack withCount(ItemStack item,int n){return item.copyWithCount(n);}
        });
    }
    private InventoryPlan(){}
}
