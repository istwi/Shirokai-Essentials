package dev.zorykai.cosmetics.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.screen.slot.SlotActionType;
import dev.zorykai.cosmetics.client.CosmeticsClient;
import java.nio.file.*;
import java.util.*;

/** Isolated loopback client: actual command packets, menu clicks, movement and screenshots. */
public final class VisualChecks implements ClientModInitializer {
    private int ticks;
    private final List<Map<String,Object>> checks=new ArrayList<>();
    private void check(String name,boolean passed) {
        checks.add(Map.of("name",name,"passed",passed));System.out.println("3D VISUAL CHECK "+passed+" "+name);
    }
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!Boolean.getBoolean("zorykai.cosmetics.visualChecks")||client.player==null||client.world==null)return;
            ticks++;
            client.options.setPerspective(ticks>=400&&ticks<420?Perspective.FIRST_PERSON:Perspective.THIRD_PERSON_BACK);
            client.options.hudHidden=true;client.player.setYaw(0);client.player.setPitch(0);
            client.options.forwardKey.setPressed(ticks>=120&&ticks<160);
            client.options.sneakKey.setPressed(ticks>=180&&ticks<210);
            if(ticks==100)check("initial server selection received", "stars".equals(CosmeticsClient.wings(client.player.getUuid())));
            if(ticks==170)check("selection retained while moving", "stars".equals(CosmeticsClient.wings(client.player.getUuid())));
            if(ticks==200)check("model selection retained while crouching", "stars".equals(CosmeticsClient.wings(client.player.getUuid())));
            if(ticks==20||ticks==220||ticks==340)client.player.networkHandler.sendChatCommand("cosmeticos");
            if(ticks==30||ticks==40||ticks==230||ticks==240||ticks==350) {
                int slot=(ticks==30||ticks==230)?14:ticks==40?10:ticks==240?11:49;
                check("menu available at step "+ticks,client.player.currentScreenHandler!=client.player.playerScreenHandler);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,slot,0,SlotActionType.PICKUP,client.player);
            }
            if(ticks==60||ticks==260||ticks==360)client.player.closeHandledScreen();
            if(ticks==280)check("fire selected through separate wings submenu", "fire".equals(CosmeticsClient.wings(client.player.getUuid())));
            if(ticks==300)client.player.networkHandler.sendChatCommand("cosmeticos desativar");
            if(ticks==320)check("disable removes client model",CosmeticsClient.wings(client.player.getUuid())==null);
            if(ticks==380)check("resume restores saved fire model", "fire".equals(CosmeticsClient.wings(client.player.getUuid())));
            if(java.util.Set.of(100,160,200,280,320,380,410).contains(ticks)) {
                ScreenshotRecorder.saveScreenshot(client.runDirectory,"wings-"+ticks+".png",client.getFramebuffer(),message->System.out.println("COSMETICS SCREENSHOT "+message.getString()));
            }
            if(ticks==440) {
                try {Files.writeString(client.runDirectory.toPath().resolve("visual-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks));}
                catch(Exception e){throw new RuntimeException(e);}
                client.scheduleStop();
            }
        });
    }
}
