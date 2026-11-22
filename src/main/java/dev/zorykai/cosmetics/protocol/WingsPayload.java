package dev.zorykai.cosmetics.protocol;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/** Empty style removes the model; every other value is an allowlisted palette. */
public record WingsPayload(UUID player,String style) implements CustomPayload {
    public static final Id<WingsPayload> ID=new Id<>(Identifier.of("zorykai_cosmetics","wings_v1"));
    public static final PacketCodec<RegistryByteBuf,WingsPayload> CODEC=PacketCodec.tuple(Uuids.PACKET_CODEC,WingsPayload::player,PacketCodecs.string(16),WingsPayload::style,WingsPayload::new);
    public static boolean validStyle(String style) {return java.util.Set.of("","stars","fire","water","nature","electric","soul").contains(style);}
    public WingsPayload {if(player==null||style==null||!validStyle(style))throw new IllegalArgumentException("Invalid wings state");}
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
