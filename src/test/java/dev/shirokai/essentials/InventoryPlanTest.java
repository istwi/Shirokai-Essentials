package dev.shirokai.essentials;
import dev.shirokai.essentials.kits.SlotPlanner;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InventoryPlanTest {
    static class Stack {String type,component;int count;Stack(String t,int n,String c){type=t;count=n;component=c;}}
    final SlotPlanner.Stacks<Stack> ops=new SlotPlanner.Stacks<>(){
        public Stack copy(Stack s){return new Stack(s.type,s.count,s.component);}public boolean empty(Stack s){return s.count==0;}
        public boolean compatible(Stack a,Stack b){return a.type.equals(b.type)&&a.component.equals(b.component);}public int count(Stack s){return s.count;}public int maximum(Stack s){return 64;}public void add(Stack s,int n){s.count+=n;}public Stack withCount(Stack s,int n){return new Stack(s.type,n,s.component);}
    };
    @Test void rejectsFullInventoryWithoutMutatingOriginal(){var slot=new Stack("stone",64,"");assertTrue(SlotPlanner.create(List.of(slot),List.of(new Stack("diamond",1,"")),ops).isEmpty());assertEquals(64,slot.count);}
    @Test void fillsStacksAndEmptySlotsWithoutLosingComponents(){
        var current=List.of(new Stack("stone",60,""),new Stack("",0,""),new Stack("",0,""));
        var result=SlotPlanner.create(current,List.of(new Stack("stone",8,""),new Stack("diamond",10,"custom-name")),ops).orElseThrow();
        assertEquals(64,result.get(0).count);assertEquals(4,result.get(1).count);assertEquals("custom-name",result.get(2).component);assertEquals(60,current.get(0).count);
    }
    @Test void distinctComponentsNeverMerge(){assertTrue(SlotPlanner.create(List.of(new Stack("diamond",1,"named")),List.of(new Stack("diamond",1,"")),ops).isEmpty());}
}
