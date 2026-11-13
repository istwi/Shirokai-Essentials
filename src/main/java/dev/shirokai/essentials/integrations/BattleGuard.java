package dev.shirokai.essentials.integrations;

import dev.shirokai.essentials.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional public Cobblemon API, verified at runtime; UUID lookup avoids mapping-sensitive parameters. */
public final class BattleGuard {
    private final ShirokaiEssentials app;
    private Object registry; private Method lookup;
    private boolean installed;
    private String status="ausente";
    public BattleGuard(ShirokaiEssentials app){this.app=app;}
    public void start(){
        installed=FabricLoader.getInstance().isModLoaded("cobblemon");if(!installed)return;
        try{
            Class<?> type=Class.forName("com.cobblemon.mod.common.battles.BattleRegistry");
            registry=type.getField("INSTANCE").get(null);lookup=type.getMethod("getBattleByParticipatingPlayerId",UUID.class);
            if(!lookup.getReturnType().getName().equals("com.cobblemon.mod.common.api.battles.model.PokemonBattle"))throw new NoSuchMethodException("Retorno incompatível");
            lookup.invoke(registry,new UUID(0,0)); // Read-only ABI probe before enabling teleport integration.
            status="consulta pública por UUID disponível";
        }catch(ReflectiveOperationException|LinkageError e){lookup=null;status="API incompatível; teleportes bloqueados enquanto blockCobblemonBattles=true";ShirokaiEssentials.LOGGER.warn("Cobblemon: {}",status);}
    }
    public boolean blocked(ServerPlayerEntity p){
        if(!installed||!app.settings().integrations.blockCobblemonBattles)return false;
        if(lookup==null)return true;
        try{return lookup.invoke(registry,p.getUuid())!=null;}catch(ReflectiveOperationException|RuntimeException e){status="falha na consulta; bloqueio conservador";return true;}
    }
    public String status(){return status;}
}
