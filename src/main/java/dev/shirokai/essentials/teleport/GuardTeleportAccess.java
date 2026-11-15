package dev.shirokai.essentials.teleport;

import dev.shirokai.essentials.ShirokaiEssentials;
import dev.shirokai.essentials.persistence.Position;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

/** Guard preflight before history/cooldown commit; other restrictions remain independent. */
final class GuardTeleportAccess {
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
