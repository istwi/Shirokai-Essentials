package dev.shirokai.essentials.paidaccess;

import java.lang.reflect.InvocationTargetException;
import net.fabricmc.loader.api.FabricLoader;

/** Read-only bridge matching the bounds contract used by Trainers 0.3.5. */
final class GuardRegionAccess {
    boolean available(){return FabricLoader.getInstance().isModLoaded("shirokai_guard");}
    boolean contains(String dimension,String region,double x,double y,double z){
        if(!available())return false;
        try{var guard=Class.forName("dev.shirokai.guard.ShiroGuard");var manager=guard.getField("REGIONS").get(null);var record=manager.getClass().getMethod("get",String.class,String.class).invoke(manager,dimension,region);var bounds=Class.forName("dev.shirokai.guard.core.Region").getMethod("bounds").invoke(record);if(bounds==null)return false;return Boolean.TRUE.equals(Class.forName("dev.shirokai.guard.core.Bounds").getMethod("contains",int.class,int.class,int.class).invoke(bounds,(int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)));}
        catch(InvocationTargetException e){return false;}catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}
    }
}
