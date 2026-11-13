package dev.shirokai.essentials.kits;

import java.util.*;
/** Inventory capacity planning over copies, independent of game registry bootstrap. */
public final class SlotPlanner {
    public interface Stacks<T> {
        T copy(T item); boolean empty(T item); boolean compatible(T a,T b); int count(T item);int maximum(T item);void add(T item,int count);T withCount(T item,int count);
    }
    public static <T> Optional<List<T>> create(List<T> current,List<T> rewards,Stacks<T> stacks){
        List<T> next=new ArrayList<>();for(T item:current)next.add(stacks.copy(item));
        for(T original:rewards){
            T remaining=stacks.copy(original);
            for(T slot:next){
                if(stacks.empty(remaining))break;
                if(!stacks.empty(slot)&&stacks.compatible(slot,remaining)){
                    int n=Math.min(stacks.count(remaining),Math.max(0,stacks.maximum(slot)-stacks.count(slot)));
                    stacks.add(slot,n);stacks.add(remaining,-n);
                }
            }
            for(int i=0;i<next.size()&&!stacks.empty(remaining);i++)if(stacks.empty(next.get(i))){
                int n=Math.min(stacks.count(remaining),stacks.maximum(remaining));next.set(i,stacks.withCount(remaining,n));stacks.add(remaining,-n);
            }
            if(!stacks.empty(remaining))return Optional.empty();
        }
        return Optional.of(next);
    }
    private SlotPlanner(){}
}
