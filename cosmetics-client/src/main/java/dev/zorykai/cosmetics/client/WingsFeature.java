package dev.zorykai.cosmetics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Original cuboid feather model, attached to the torso on each rendered frame. */
public final class WingsFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE=Identifier.of("zorykai_cosmetics","textures/wings.png");
    private final ModelPart root,left,right;
    public WingsFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
        root=AngelGeometry.create();left=root.getChild("left");right=root.getChild("right");
    }
    @Override public void render(MatrixStack matrices,VertexConsumerProvider consumers,int light,AbstractClientPlayerEntity player,float limbAngle,float limbDistance,float tickDelta,float animationProgress,float headYaw,float headPitch) {
        String style=CosmeticsClient.wings(player.getUuid());
        var client=MinecraftClient.getInstance();
        if(style==null||!player.isAlive()||player.isInvisible()||player.isSpectator()||player.hasVehicle()||player.isSwimming()||player.isFallFlying())return;
        if(player==client.player&&client.options.getPerspective().isFirstPerson())return;
        float flap=(float)Math.sin((player.age+tickDelta)*(.12f+Math.min(limbDistance,.5f)*.12f))*.045f;
        left.yaw=-.22f-flap;right.yaw=.22f+flap;
        left.roll=0;right.roll=0;
        matrices.push();
        getContextModel().body.rotate(matrices);
        root.render(matrices,consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,OverlayTexture.DEFAULT_UV,tint(style));
        matrices.pop();
    }
    private static int tint(String style) {
        return switch(style) {case "fire"->0xFFFF914D;case "water"->0xFF62BFFF;case "nature"->0xFF8FFFA8;case "electric"->0xFFFFE879;case "soul"->0xFFC19CFF;default->0xFFFFFFFF;};
    }
}
