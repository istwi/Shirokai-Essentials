package dev.zorykai.cosmetics.client;

import dev.zorykai.cosmetics.protocol.WingsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import java.util.*;

public final class CosmeticsClient implements ClientModInitializer {
    private static final Map<UUID,String> WINGS=new HashMap<>();
    public static String wings(UUID player){return WINGS.get(player);}
    @Override public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(WingsPayload.ID,WingsPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(WingsPayload.ID,(payload,context)->context.client().execute(()->{
            if(payload.style().isEmpty())WINGS.remove(payload.player());else WINGS.put(payload.player(),payload.style());
        }));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->WINGS.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->WINGS.clear());
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{
            if(renderer instanceof PlayerEntityRenderer playerRenderer)helper.register(new WingsFeature(playerRenderer));
        });
    }
}
