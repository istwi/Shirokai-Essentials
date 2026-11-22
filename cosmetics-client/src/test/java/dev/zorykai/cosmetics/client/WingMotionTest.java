package dev.zorykai.cosmetics.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WingMotionTest {
    @Test void stationaryWingsDoNotFlap(){var m=new WingMotion();for(int i=0;i<500;i++)assertEquals(0,m.update(i,0));}
    @Test void walkingHasVisibleSlowCycle(){var m=new WingMotion();for(int i=0;i<120;i++)m.update(i,1);float min=1,max=0;for(int i=120;i<190;i++){float a=m.update(i,1);min=Math.min(min,a);max=Math.max(max,a);}assertTrue(max-min>.60);}
    @Test void speedChangesDoNotJumpEvenAfterLongSession(){var m=new WingMotion();float last=m.update(1_000_000,0);for(int i=1;i<500;i++){float v=m.update(1_000_000+i*.25,i%2==0?1:0);assertTrue(Math.abs(v-last)<.05);last=v;}}
    @Test void stopsSmoothly(){var m=new WingMotion();float last=0;for(int i=0;i<120;i++)last=m.update(i,1);for(int i=120;i<200;i++){float next=m.update(i,0);assertTrue(Math.abs(next-last)<.12);last=next;}assertTrue(Math.abs(last)<.001);}
    @Test void repeatedRenderPassCannotAdvanceAnimation(){var m=new WingMotion();m.update(0,1);float a=m.update(1,1);assertEquals(a,m.update(1,1));}
}
