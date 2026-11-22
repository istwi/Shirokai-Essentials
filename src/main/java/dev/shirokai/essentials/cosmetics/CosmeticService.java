package dev.shirokai.essentials.cosmetics;

import dev.shirokai.essentials.ShirokaiEssentials;
import dev.shirokai.essentials.persistence.DataStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.particle.*;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import java.io.IOException;
import java.util.*;

/** Server-thread-only visuals: no inventory edits, potion effects, damage or flight. */
public final class CosmeticService {
    private final ShirokaiEssentials app;
    private CosmeticSettings config=new CosmeticSettings();
    private final Map<UUID,DataStore.PlayerData> selected=new HashMap<>();
    private final Map<UUID,Vec3d> previous=new HashMap<>();
    private final Map<UUID,String> worlds=new HashMap<>();
    private final Map<UUID,Team> ownedTeams=new HashMap<>();
    private final Map<UUID,Long> lastCelebration=new HashMap<>();
    private long ticks;
    private long nextErrorLog;
    private int globalBudget, playerBudget;
    private int rotation;
    public CosmeticService(ShirokaiEssentials app) { this.app=app; }
    public CosmeticSettings config() { return config; }
    public void initialize() throws IOException {
        config=CosmeticSettings.load(app.configDir.resolve("cosmetics.json"));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            dispatcher.register(CommandManager.literal("cosmeticos")
                .requires(s->s.isExecutedByPlayer()&&app.permissions.has(s,"cosmetics.use"))
                .executes(ctx->{open(ctx.getSource().getPlayerOrThrow(),"main");return 1;})
                .then(CommandManager.literal("desativar").executes(ctx->{disable(ctx.getSource().getPlayerOrThrow());return 1;})));
            dispatcher.register(CommandManager.literal("cosmeticosadmin")
                .requires(s->app.permissions.has(s,"cosmetics.admin"))
                .then(CommandManager.literal("reload").executes(ctx->{
                    try { var next=CosmeticSettings.load(app.configDir.resolve("cosmetics.json"));config=next;
                        for(var p:ctx.getSource().getServer().getPlayerManager().getPlayerList())releaseTeam(p);
                        ctx.getSource().sendFeedback(()->message("Configuração de cosméticos recarregada."),false);return 1;
                    } catch(IOException e) {error(null,e);ctx.getSource().sendError(message("Configuração inválida; versão anterior mantida."));return 0;}
                }))
                .then(CommandManager.literal("celebrar")
                    .then(CommandManager.argument("jogador",EntityArgumentType.player())
                        .then(CommandManager.literal("vitoria").executes(ctx->celebrate(EntityArgumentType.getPlayer(ctx,"jogador"),"victory")?1:0))
                        .then(CommandManager.literal("raid").executes(ctx->celebrate(EntityArgumentType.getPlayer(ctx,"jogador"),"raid")?1:0)))));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            // Scoreboard teams survive a crash. Only clean our signed, reserved names.
            var board=server.getScoreboard();
            for(var team:List.copyOf(board.getTeams())) {
                String marker=team.getDisplayName().getString();
                if(!marker.startsWith("shirokai-essentials:cosmetics/"))continue;
                try {
                    var id=UUID.fromString(marker.substring("shirokai-essentials:cosmetics/".length()));
                    if(team.getName().equals(teamName(id))&&team.getPlayerList().size()<=1)board.removeTeam(team);
                } catch(IllegalArgumentException ignored) { /* Another owner's team. */ }
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(entity instanceof ServerPlayerEntity && source.getAttacker() instanceof ServerPlayerEntity winner && winner!=entity)celebrate(winner,"victory");
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            var p=handler.player;
            try {selected.put(p.getUuid(),app.data().player(p.getUuid()));}
            catch(IOException e) {error(p,e);return;}
            remember(p);
            // A single entrance, after a short delay; cancellation on disconnect is implicit.
            entrances.put(p.getUuid(),ticks+40);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;releaseTeam(p);selected.remove(p.getUuid());previous.remove(p.getUuid());worlds.remove(p.getUuid());entrances.remove(p.getUuid());lastCelebration.remove(p.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var p:server.getPlayerManager().getPlayerList())releaseTeam(p);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{selected.clear();previous.clear();worlds.clear();entrances.clear();ownedTeams.clear();lastCelebration.clear();});
    }
    private final Map<UUID,Long> entrances=new HashMap<>();
    public static Text message(String text) {return Text.literal("✦ ").formatted(Formatting.AQUA).append(Text.literal("SHIROKAI ").formatted(Formatting.WHITE,Formatting.BOLD)).append(Text.literal("» "+text).formatted(Formatting.GRAY));}
    void error(ServerPlayerEntity p,Exception error) {
        ShirokaiEssentials.LOGGER.error("Falha em cosméticos; seleção anterior preservada",error);
        if(p!=null)p.sendMessage(message("Não foi possível salvar. A seleção anterior foi preservada."),false);
    }
    public boolean canUse(ServerPlayerEntity p) {return config.enabled&&app.permissions.has(p.getCommandSource(),"cosmetics.use");}
    public boolean canCustomize(ServerPlayerEntity p) {return canUse(p)&&app.permissions.has(p.getCommandSource(),"cosmetics.customize");}
    public boolean unlocked(ServerPlayerEntity p,CosmeticCatalog.Entry e) {
        return canUse(p)&&!config.disabled.contains(e.id())&&app.permissions.has(p.getCommandSource(),"cosmetics."+e.id())&&(!e.category().equals("nick")||config.nickEnabled);
    }
    public DataStore.PlayerData state(ServerPlayerEntity p) throws IOException {
        var state=selected.get(p.getUuid());if(state==null){state=app.data().player(p.getUuid());selected.put(p.getUuid(),state);}return state;
    }
    public void open(ServerPlayerEntity p,String page) {
        if(!canUse(p)){p.sendMessage(message("Cosméticos indisponíveis ou sem permissão."),false);return;}
        p.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory((id,inventory,player)->new CosmeticScreen(id,inventory,p,this,page),Text.literal("✦ Cosméticos · "+CosmeticScreen.title(page)).formatted(Formatting.DARK_AQUA)));
    }
    public void equip(ServerPlayerEntity p,CosmeticCatalog.Entry e) {
        if(!unlocked(p,e)){p.sendMessage(message("Esta opção está bloqueada."),false);return;}
        save(p,d->{d.cosmetics.put(e.category(),e.id());d.cosmeticsHidden=false;});
    }
    public void remove(ServerPlayerEntity p,String category) {save(p,d->d.cosmetics.remove(category));}
    public void disable(ServerPlayerEntity p) {save(p,d->d.cosmeticsHidden=true);}
    public void resume(ServerPlayerEntity p) {save(p,d->d.cosmeticsHidden=false);}
    public void preferences(ServerPlayerEntity p,DataStore.CosmeticPreferences value) {
        if(!canCustomize(p)){p.sendMessage(message("Você não tem permissão para personalizar."),false);return;}
        if(value.speed<1||value.speed>config.maxSpeed||value.size<1||value.size>config.maxSize||value.density<1||value.density>config.maxDensity||Math.abs(value.direction)!=1){p.sendMessage(message("Personalização fora dos limites do servidor."),false);return;}
        save(p,d->{d.cosmeticPreferences.speed=value.speed;d.cosmeticPreferences.size=value.size;d.cosmeticPreferences.density=value.density;d.cosmeticPreferences.direction=value.direction;});
    }
    public void preview(ServerPlayerEntity p,DataStore.CosmeticPreferences value) {
        if(!canCustomize(p)||!visible(p))return;
        if(globalBudget<=0)return;playerBudget=Math.min(config.maxParticlesPerPlayer,12);
        value=bounded(value);
        double radius=.25+value.size*.2, phase=ticks*(.04*value.speed)*value.direction;
        for(int i=0;i<Math.min(12,value.density*4);i++){double progress=i/(double)Math.max(1,value.density*4-1),a=phase+progress*Math.PI*4*value.direction;emit(p,"stars",p.getPos().add(Math.cos(a)*radius,2-progress*1.9,Math.sin(a)*radius));}
    }
    private void save(ServerPlayerEntity p,java.util.function.Consumer<DataStore.PlayerData> change) {
        if(!canUse(p))return;
        try {app.data().update(p.getUuid(),change);selected.put(p.getUuid(),app.data().player(p.getUuid()));releaseTeam(p);p.sendMessage(message("Seleção de cosméticos atualizada."),false);}
        catch(IOException e) {error(p,e);}
    }
    private boolean visible(ServerPlayerEntity p) {
        return canUse(p)&&p.isAlive()&&!p.isSpectator()&&!p.isInvisible()&&!app.moderation.restricted(p)&&(!config.suppressInBattle||!app.battles.blocked(p));
    }
    private CosmeticCatalog.Entry equipped(ServerPlayerEntity p,String category) {
        var state=selected.get(p.getUuid());if(state==null||state.cosmeticsHidden)return null;
        var e=CosmeticCatalog.find(state.cosmetics.get(category));return e!=null&&e.category().equals(category)&&unlocked(p,e)?e:null;
    }
    private void remember(ServerPlayerEntity p) {previous.put(p.getUuid(),p.getPos());worlds.put(p.getUuid(),p.getServerWorld().getRegistryKey().getValue().toString());}
    private void tick(MinecraftServer server) {
        ticks++;globalBudget=config.maxParticlesPerTick;
        var players=server.getPlayerManager().getPlayerList();
        if(players.isEmpty())return;
        // Rotate priority so a busy server cannot permanently starve the last players.
        for(int i=0;i<players.size();i++) {
            var p=players.get((i+rotation)%players.size());
            try {
                if(!visible(p)||selected.get(p.getUuid())==null||selected.get(p.getUuid()).cosmeticsHidden) {releaseTeam(p);remember(p);entrances.remove(p.getUuid());continue;}
                playerBudget=config.maxParticlesPerPlayer;
                if(ticks%20==0)updateNick(p);
                if(entrances.getOrDefault(p.getUuid(),Long.MAX_VALUE)<=ticks) {entrances.remove(p.getUuid());burst(p,"join",p.getPos());}
                Vec3d old=previous.get(p.getUuid());String oldWorld=worlds.get(p.getUuid());
                boolean same=p.getServerWorld().getRegistryKey().getValue().toString().equals(oldWorld);
                double distance=old==null?0:p.getPos().squaredDistanceTo(old);
                boolean moved=same&&distance>.0004&&distance<64;
                if(moved&&p.isOnGround()&&ticks%config.trailIntervalTicks==0)shape(p,"trail",p.getPos());
                if(ticks%config.intervalTicks==0)shape(p,"aura",p.getPos());
                remember(p);
            } catch(RuntimeException e) {if(ticks>=nextErrorLog){error(null,e);nextErrorLog=ticks+1200;}releaseTeam(p);remember(p);}
        }
        rotation=(rotation+1)%players.size();
    }
    public boolean celebrate(ServerPlayerEntity p,String category) {
        if(!Set.of("raid","victory").contains(category)||!visible(p)||equipped(p,category)==null)return false;
        long next=lastCelebration.getOrDefault(p.getUuid(),0L);if(ticks<next)return false;
        lastCelebration.put(p.getUuid(),ticks+config.celebrationCooldownSeconds*20L);
        playerBudget=config.maxParticlesPerPlayer;
        // Event calls share the tick budget; they never grant items or rewards.
        burst(p,category,p.getPos());return true;
    }
    private void burst(ServerPlayerEntity p,String category,Vec3d origin) {
        var e=equipped(p,category);if(e==null)return;
        var pref=preferences(p);int count=pref.density*12;double radius=.4+pref.size*.2;
        for(int i=0;i<count;i++){double a=i*Math.PI*2/count;emit(p,e.style(),origin.add(Math.cos(a)*radius,.15+i*(1.45/count),Math.sin(a)*radius));}
    }
    private void shape(ServerPlayerEntity p,String category,Vec3d origin) {
        var e=equipped(p,category);if(e==null)return;
        var pref=preferences(p);double phase=ticks*(.04*pref.speed)*pref.direction,radius=.25+pref.size*.2;
        switch(category) {
            case "trail" -> {
                double yaw=Math.toRadians(p.getBodyYaw()),rightX=Math.cos(yaw),rightZ=Math.sin(yaw),backX=Math.sin(yaw),backZ=-Math.cos(yaw);
                int count=Math.min(config.maxParticlesPerPlayer,config.trailBaseDensity*pref.density*trailMultiplier(e.style()));
                for(int i=0;i<count;i++){double side=((i%3)-1)*.13*pref.size,back=(i/3)*.055;emit(p,e.style(),origin.add(rightX*side+backX*back,.035,rightZ*side+backZ*back));}
            }
            case "aura" -> {int count=pref.density*2;for(int i=0;i<count;i++){double progress=i/(double)Math.max(1,count-1),a=phase+progress*Math.PI*4*pref.direction;emit(p,e.style(),origin.add(Math.cos(a)*radius,2-progress*1.9,Math.sin(a)*radius));}}
        }
    }
    private DataStore.CosmeticPreferences preferences(ServerPlayerEntity p) {
        var state=selected.get(p.getUuid());return bounded(state==null||state.cosmeticPreferences==null?new DataStore.CosmeticPreferences():state.cosmeticPreferences);
    }
    private DataStore.CosmeticPreferences bounded(DataStore.CosmeticPreferences value) {
        var safe=new DataStore.CosmeticPreferences();safe.speed=Math.max(1,Math.min(config.maxSpeed,value.speed));safe.size=Math.max(1,Math.min(config.maxSize,value.size));safe.density=Math.max(1,Math.min(config.maxDensity,value.density));safe.direction=value.direction<0?-1:1;return safe;
    }
    private static SimpleParticleType particle(String style) {
        return switch(style) {case "fire"->ParticleTypes.FLAME;case "water"->ParticleTypes.SPLASH;case "nature"->ParticleTypes.HAPPY_VILLAGER;case "electric"->ParticleTypes.ELECTRIC_SPARK;case "soul"->ParticleTypes.SOUL;case "snow"->ParticleTypes.SNOWFLAKE;case "heart"->ParticleTypes.HEART;case "magic"->ParticleTypes.WITCH;case "enchant"->ParticleTypes.ENCHANT;case "cloud"->ParticleTypes.CLOUD;case "cherry"->ParticleTypes.CHERRY_LEAVES;default->ParticleTypes.END_ROD;};
    }
    private static int trailMultiplier(String style) {return switch(style){case "water"->3;case "electric","soul","snow","magic","enchant"->2;default->1;};}
    private void emit(ServerPlayerEntity p,String style,Vec3d pos) {
        if(globalBudget<=0||playerBudget<=0)return;
        globalBudget--;playerBudget--;
        for(var viewer:p.getServerWorld().getPlayers()) {
            var state=selected.get(viewer.getUuid());
            if(state!=null&&state.cosmeticsHidden)continue; // personal opt-out also hides other players' effects
            if(viewer.getPos().squaredDistanceTo(pos)<=config.viewDistance*config.viewDistance)
                p.getServerWorld().spawnParticles(viewer,particle(style),false,pos.x,pos.y,pos.z,1,0,0,0,0);
        }
    }
    private static final Formatting[] RAINBOW={Formatting.AQUA,Formatting.BLUE,Formatting.LIGHT_PURPLE,Formatting.RED,Formatting.GOLD,Formatting.YELLOW,Formatting.GREEN};
    private void updateNick(ServerPlayerEntity p) {
        var e=equipped(p,"nick");if(e==null){releaseTeam(p);return;}
        var board=p.getServer().getScoreboard();var owned=ownedTeams.get(p.getUuid());var current=board.getScoreHolderTeam(p.getNameForScoreboard());
        if(current!=null&&current!=owned) {releaseTeam(p);return;}
        if(owned==null) {
            // Never adopt a pre-existing team, even if its name happens to match.
            String name=teamName(p.getUuid());
            if(board.getTeam(name)!=null)return;
            owned=board.addTeam(name);owned.setDisplayName(Text.literal("shirokai-essentials:cosmetics/"+p.getUuid()));ownedTeams.put(p.getUuid(),owned);board.addScoreHolderToTeam(p.getNameForScoreboard(),owned);
        }
        owned.setColor(switch(e.style()){case "gold"->Formatting.GOLD;case "rainbow"->RAINBOW[(int)((ticks/40)%RAINBOW.length)];default->Formatting.AQUA;});
    }
    private static String teamName(UUID id) {return "skc_"+id.toString().replace("-","").substring(0,12);}
    private void releaseTeam(ServerPlayerEntity p) {
        var owned=ownedTeams.remove(p.getUuid());if(owned==null)return;
        var board=p.getServer().getScoreboard();
        if(board.getTeam(owned.getName())!=owned)return;
        if(board.getScoreHolderTeam(p.getNameForScoreboard())==owned)board.removeScoreHolderFromTeam(p.getNameForScoreboard(),owned);
        // If another plugin has put members into the team, preserve it.
        if(owned.getPlayerList().isEmpty())board.removeTeam(owned);
    }
}
