package dev.shirokai.test;

import com.mojang.authlib.GameProfile;
import dev.shirokai.essentials.utilities.SpeedControl;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.util.Identifier;

public final class SpeedChecks implements DedicatedServerModInitializer {
    private final List<String> checks=new ArrayList<>();private boolean ran;
    public void onInitializeServer(){if(!Boolean.getBoolean("essentials.speedChecks"))throw new IllegalStateException("Disposable server only");ServerTickEvents.END_SERVER_TICK.register(s->{if(ran||s.getTicks()<40)return;ran=true;try{test(s);finish(s,true,null);}catch(Throwable e){e.printStackTrace();finish(s,false,e.toString());}});}
    private static final class Player extends ServerPlayerEntity {
        final List<Packet<?>> packets=new ArrayList<>();
        Player(MinecraftServer s,String name){super(s,s.getOverworld(),new GameProfile(UUID.randomUUID(),name),SyncedClientOptions.createDefault());networkHandler=new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND),this,ConnectedClientData.createDefault(getGameProfile(),false)){@Override public void sendPacket(Packet<?> packet){packets.add(packet);}};}
    }
    private void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks.add(name);}
    private boolean near(double a,double b){return Math.abs(a-b)<.000001;}
    private void test(MinecraftServer server)throws Exception{
        var p=new Player(server,"SpeedAlice");var other=new Player(server,"SpeedBob");server.getPlayerManager().getPlayerList().add(p);server.getPlayerManager().getPlayerList().add(other);
        var source=p.getCommandSource().withLevel(4);var commands=server.getCommandManager().getDispatcher();var attribute=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        // Reproduce a stale movement attribute left behind by an older walk-speed setting.
        attribute.setBaseValue(.01);p.getAbilities().setWalkSpeed(.01f);
        check(commands.execute("speed 1",source)==1,"normal speed command executes for operator");
        check(near(attribute.getBaseValue(),.1)&&near(p.getAbilities().getWalkSpeed(),.1),"speed 1 repairs both stale movement base and abilities to vanilla normal");
        check(p.packets.stream().anyMatch(x->x instanceof EntityAttributesS2CPacket)&&p.packets.stream().anyMatch(x->x instanceof PlayerAbilitiesS2CPacket),"walking update sends actual attribute and ability packets to player");
        commands.execute("speed 4",source);check(near(attribute.getBaseValue(),.4),"ground mode changes the attribute used by walking without reconnect");
        p.getAbilities().flying=true;commands.execute("speed 3",source);check(near(p.getAbilities().getFlySpeed(),.15)&&near(attribute.getBaseValue(),.4),"automatic flight mode changes only flying speed");
        commands.execute("speed walk 2",source);check(near(attribute.getBaseValue(),.2)&&p.getAbilities().flying,"explicit walk mode works while flying without disabling flight");
        p.getAbilities().flying=false;boolean allowed=p.getAbilities().allowFlying;commands.execute("speed fly 4",source);check(near(p.getAbilities().getFlySpeed(),.2)&&near(attribute.getBaseValue(),.2)&&p.getAbilities().allowFlying==allowed,"explicit flight speed works from ground without granting flight");
        var key=Identifier.of("speed_test","external_modifier");attribute.addTemporaryModifier(new EntityAttributeModifier(key,.2,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        check(commands.execute("speed reset",source)==1,"speed reset is registered and executes");
        check(near(attribute.getBaseValue(),.1)&&near(p.getAbilities().getWalkSpeed(),.1)&&near(p.getAbilities().getFlySpeed(),.05),"reset restores walking base and both ability speeds immediately");
        check(attribute.hasModifier(key)&&near(attribute.getValue(),.12),"reset preserves external potion or equipment style modifiers");
        attribute.removeModifier(key);
        commands.execute("speed 8",source);commands.execute("speed 0",source);check(near(attribute.getBaseValue(),.1),"speed zero alias restores normal movement");
        other.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(.01);commands.execute("speed reset SpeedBob",source);check(near(other.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),.1),"operator can reset a named online target");
        commands.execute("speed walk 5 SpeedBob",source);check(near(other.getAbilities().getWalkSpeed(),.5),"explicit target walking mode respects existing command tree");
        boolean denied=false;try{commands.execute("shirokaiessentials:speed reset",p.getCommandSource().withLevel(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){denied=true;}check(denied,"namespaced fallback does not bypass permission checks");
        commands.register(net.minecraft.server.command.CommandManager.literal("speed").then(net.minecraft.server.command.CommandManager.literal("reset").executes(c->77)));
        check(commands.execute("speed reset",source)==77,"fixture can reproduce another mod replacing common reset executor");
        attribute.setBaseValue(.01);check(commands.execute("shirokaiessentials:speed reset",source)==1&&near(attribute.getBaseValue(),.1),"namespaced reset remains functional when common speed command is overridden");
        denied=false;try{SpeedControl.walk(p,11);}catch(IllegalArgumentException e){denied=true;}check(denied,"speed helper enforces level limits");
    }
    private void finish(MinecraftServer server,boolean passed,String error){try{Files.writeString(Path.of("speed-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",passed,"checks",checks,"error",error==null?"":error,"realClients",0)));}catch(Exception ignored){}server.stop(false);}
}
