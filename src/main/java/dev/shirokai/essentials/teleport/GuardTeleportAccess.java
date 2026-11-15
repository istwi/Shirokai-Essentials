package dev.shirokai.essentials.teleport;

import dev.shirokai.essentials.ShirokaiEssentials;
import dev.shirokai.essentials.persistence.Position;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

/** Guard preflight before history/cooldown commit; other restrictions remain independent. */
final class GuardTeleportAccess {
    static UUID capture(ServerPlayerEntity player, Position destination) {
        if (!FabricLoader.getInstance().isModLoaded("shirokai_guard")) return null;
        try {
            return (UUID)Class.forName("dev.shirokai.guard.api.GuardEntryApi")
                .getMethod("captureCommand",UUID.class,String.class,double.class,double.class,double.class)
                .invoke(null,player.getUuid(),destination.dimension(),destination.x(),destination.y(),destination.z());
        } catch (NoSuchMethodException oldGuard) { return null; }
        catch (ReflectiveOperationException e) { ShirokaiEssentials.LOGGER.error("Falha na captura de autorização Guard",e); return null; }
    }
    static int run(UUID token, ServerPlayerEntity player, Position destination, java.util.function.IntSupplier operation) {
        if (token == null) return operation.getAsInt();
        try {
            return (int)Class.forName("dev.shirokai.guard.api.GuardEntryApi")
                .getMethod("runCommandTeleport",UUID.class,UUID.class,String.class,double.class,double.class,double.class,java.util.function.IntSupplier.class)
                .invoke(null,token,player.getUuid(),destination.dimension(),destination.x(),destination.y(),destination.z(),operation);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Falha na operação Guard",e.getCause());
        } catch (ReflectiveOperationException e) { ShirokaiEssentials.LOGGER.error("Falha no teleporte autorizado Guard",e); return 0; }
    }
    static void revoke(UUID token) {
        if (token == null) return;
        try { Class.forName("dev.shirokai.guard.api.GuardEntryApi").getMethod("revokeCommand",UUID.class).invoke(null,token); }
        catch (ReflectiveOperationException e) { ShirokaiEssentials.LOGGER.error("Falha na revogação Guard",e); }
    }
    static boolean allowed(ServerPlayerEntity player, Position destination) {
        if (!FabricLoader.getInstance().isModLoaded("shirokai_guard")) return true;
        try {
            return (boolean)Class.forName("dev.shirokai.guard.api.GuardEntryApi")
                .getMethod("canTeleport",UUID.class,String.class,double.class,double.class,double.class)
                .invoke(null,player.getUuid(),destination.dimension(),destination.x(),destination.y(),destination.z());
        } catch (ReflectiveOperationException e) {
            ShirokaiEssentials.LOGGER.error("Guard sem API de pré-teleporte compatível; teleporte recusado",e);
            return false;
        }
    }
}
