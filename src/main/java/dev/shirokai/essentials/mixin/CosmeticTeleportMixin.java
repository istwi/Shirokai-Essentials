package dev.shirokai.essentials.mixin;

import dev.shirokai.essentials.ShirokaiEssentials;
import net.minecraft.server.network.*;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

/** Observe the vanilla teleport path after its server-side position update. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class CosmeticTeleportMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="requestTeleport(DDDFFLjava/util/Set;)V",at=@At("RETURN"))
    private void shirokai$teleported(double x,double y,double z,float yaw,float pitch,Set<PositionFlag> flags,CallbackInfo ci) {
        var app=ShirokaiEssentials.instance();if(app!=null)app.cosmetics.teleported(player);
    }
}
