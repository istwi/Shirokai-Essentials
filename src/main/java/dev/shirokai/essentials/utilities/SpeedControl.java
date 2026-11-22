package dev.shirokai.essentials.utilities;

import java.util.List;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/** Abilities alone do not update PlayerEntity's runtime movement attribute. */
public final class SpeedControl {
    private SpeedControl() {}
    public static void walk(ServerPlayerEntity player, int level) {
        check(level);
        applyWalk(player, level / 10f);
        player.sendAbilitiesUpdate();
    }
    public static void fly(ServerPlayerEntity player, int level) {
        check(level);
        player.getAbilities().setFlySpeed(level / 20f);
        player.sendAbilitiesUpdate();
    }
    public static void reset(ServerPlayerEntity player) {
        applyWalk(player, .1f);
        player.getAbilities().setFlySpeed(.05f);
        player.sendAbilitiesUpdate();
    }
    private static void applyWalk(ServerPlayerEntity player, float value) {
        player.getAbilities().setWalkSpeed(value);
        var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) throw new IllegalStateException("Atributo de movimento indisponível");
        attribute.setBaseValue(value);
        // Keep potion/equipment/sprint modifiers; only the vanilla walking base is changed.
        player.networkHandler.sendPacket(new EntityAttributesS2CPacket(player.getId(), List.of(attribute)));
    }
    private static void check(int level) {
        if (level < 1 || level > 10) throw new IllegalArgumentException("Velocidade deve ser de 1 a 10");
    }
}
