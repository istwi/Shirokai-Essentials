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
import org.joml.Vector3f;
import java.time.*;
import java.util.*;

/** Server-thread-only visuals: no inventory edits, potion effects, damage or flight. */
public final class CosmeticService {
    private final ShirokaiEssentials app;
    private final CosmeticModels models=new CosmeticModels();
    private CosmeticSettings config=new CosmeticSettings();
    private final Map<UUID,DataStore.PlayerData> selected=new HashMap<>();
    private final Map<UUID,Vec3d> previous=new HashMap<>();
    private final Map<UUID,String> worlds=new HashMap<>();
    private final Map<UUID,Team> ownedTeams=new HashMap<>();
    private final Map<UUID,Long> lastTrail=new HashMap<>();
    private final Map<UUID,Long> lastCelebration=new HashMap<>();
    private long ticks;
    private long nextErrorLog;
    private int globalBudget, playerBudget;
    private int rotation;
    public CosmeticService(ShirokaiEssentials app) { this.app=app; }
    public CosmeticSettings config() { return config; }
    public LocalDate today() { return LocalDate.now(ZoneId.of(config.timezone)); }
    public void initialize() throws IOException {
        config=CosmeticSettings.load(app.configDir.resolve("cosmetics.json"));
        models.initialize();
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
            var p=handler.player;models.disconnect(p.getUuid());releaseTeam(p);selected.remove(p.getUuid());previous.remove(p.getUuid());worlds.remove(p.getUuid());entrances.remove(p.getUuid());lastCelebration.remove(p.getUuid());lastTrail.remove(p.getUuid());pendingTeleports.remove(p.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var p:server.getPlayerManager().getPlayerList())releaseTeam(p);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{models.clear();selected.clear();previous.clear();worlds.clear();entrances.clear();ownedTeams.clear();lastCelebration.clear();lastTrail.clear();pendingTeleports.clear();});
    }
    private final Map<UUID,Vec3d> pendingTeleports=new HashMap<>();
    public void teleported(ServerPlayerEntity p) {if(selected.containsKey(p.getUuid()))pendingTeleports.put(p.getUuid(),p.getPos());}
    private final Map<UUID,Long> entrances=new HashMap<>();
    public static Text message(String text) {return Text.literal("✦ ").formatted(Formatting.AQUA).append(Text.literal("SHIROKAI ").formatted(Formatting.WHITE,Formatting.BOLD)).append(Text.literal("» "+text).formatted(Formatting.GRAY));}
    void error(ServerPlayerEntity p,Exception error) {
        ShirokaiEssentials.LOGGER.error("Falha em cosméticos; seleção anterior preservada",error);
        if(p!=null)p.sendMessage(message("Não foi possível salvar. A seleção anterior foi preservada."),false);
    }
    public boolean canUse(ServerPlayerEntity p) {return config.enabled&&app.permissions.has(p.getCommandSource(),"cosmetics.use");}
    public boolean unlocked(ServerPlayerEntity p,CosmeticCatalog.Entry e) {
        return canUse(p)&&!config.disabled.contains(e.id())&&e.available(today())&&app.permissions.has(p.getCommandSource(),"cosmetics."+e.id())&&(!e.category().equals("nick")||config.nickEnabled);
    }
    public DataStore.PlayerData state(ServerPlayerEntity p) throws IOException {
        var state=selected.get(p.getUuid());if(state==null){state=app.data().player(p.getUuid());selected.put(p.getUuid(),state);}return state;
    }
    public void open(ServerPlayerEntity p,String page) {
        if(!canUse(p)){p.sendMessage(message("Cosméticos indisponíveis ou sem permissão."),false);return;}
        p.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory((id,inventory,player)->new CosmeticScreen(id,inventory,p,this,page),Text.literal("✦ Cosméticos · "+CosmeticScreen.title(page)).formatted(Formatting.DARK_AQUA)));
    }
    public void equip(ServerPlayerEntity p,CosmeticCatalog.Entry e) {
        if(!unlocked(p,e)){p.sendMessage(message("Esta opção está bloqueada ou fora da temporada."),false);return;}
        save(p,d->{d.cosmetics.put(e.category(),e.id());if(e.category().equals("trail"))d.cosmetics.remove("footprint");d.cosmeticsHidden=false;});
    }
    public void remove(ServerPlayerEntity p,String category) {save(p,d->{d.cosmetics.remove(category);if(category.equals("trail"))d.cosmetics.remove("footprint");});}
    public void disable(ServerPlayerEntity p) {save(p,d->d.cosmeticsHidden=true);}
    public void resume(ServerPlayerEntity p) {save(p,d->d.cosmeticsHidden=false);}
    public void collection(ServerPlayerEntity p,String style) {
        var entries=CosmeticCatalog.collection(style);
        if(entries.values().stream().map(CosmeticCatalog::find).anyMatch(e->!unlocked(p,e))){p.sendMessage(message("Desbloqueie todos os efeitos da coleção para equipá-la."),false);return;}
        save(p,d->{d.cosmetics.putAll(entries);d.cosmetics.remove("footprint");d.cosmeticsHidden=false;});
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
        ticks++;
        if(ticks%5==0)models.sync(server,p->{
            if(!visible(p)||p.hasVehicle()||p.isSwimming()||p.isFallFlying())return "";
            var wing=equipped(p,"wings");return wing==null?"":wing.style();
        },p->{var selected=this.selected.get(p.getUuid());return selected!=null&&selected.cosmeticsHidden;});
        globalBudget=config.maxParticlesPerTick;
        var players=server.getPlayerManager().getPlayerList();
        if(players.isEmpty())return;
        // Rotate priority so a busy server cannot permanently starve the last players.
        for(int i=0;i<players.size();i++) {
            var p=players.get((i+rotation)%players.size());
            try {
                if(!visible(p)||selected.get(p.getUuid())==null||selected.get(p.getUuid()).cosmeticsHidden) {releaseTeam(p);remember(p);entrances.remove(p.getUuid());pendingTeleports.remove(p.getUuid());continue;}
                playerBudget=config.maxParticlesPerPlayer;
                if(ticks%20==0)updateNick(p);
                if(entrances.getOrDefault(p.getUuid(),Long.MAX_VALUE)<=ticks) {entrances.remove(p.getUuid());burst(p,"join",p.getPos());}
                // Attached outlines use their own refresh: short dust expires before a new wing frame.
                if(ticks%config.wingsIntervalTicks==0)shape(p,"wings",p.getPos());
                if(ticks%config.intervalTicks!=0)continue;
                Vec3d old=previous.get(p.getUuid());String oldWorld=worlds.get(p.getUuid());
                boolean same=p.getServerWorld().getRegistryKey().getValue().toString().equals(oldWorld);
                double distance=old==null?0:p.getPos().squaredDistanceTo(old);
                boolean moved=same&&distance>.008&&distance<64;
                // Coalesce repeated network updates into one effect at the final arrival.
                if(pendingTeleports.remove(p.getUuid())!=null)burst(p,"teleport",p.getPos());
                if(moved&&p.isOnGround()&&ticks-lastTrail.getOrDefault(p.getUuid(),-200L)>=config.trailDelayTicks) {shape(p,"trail",p.getPos());lastTrail.put(p.getUuid(),ticks);}
                for(String category:List.of("aura","crown","circle"))shape(p,category,p.getPos());
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
        for(int i=0;i<24;i++){double a=i*Math.PI/12;emit(p,e.style(),origin.add(Math.cos(a)*.8,.15+i*.06,Math.sin(a)*.8));}
    }
    private void shape(ServerPlayerEntity p,String category,Vec3d origin) {
        var e=equipped(p,category);if(e==null&&category.equals("trail"))e=equipped(p,"footprint");if(e==null)return;
        double phase=ticks*.08;
        switch(category) {
            case "trail", "footprint" -> {
                // A visible patch across the walking path instead of one isolated dot.
                double yaw=Math.toRadians(p.getBodyYaw());
                for(int i=0;i<config.trailPoints;i++) {
                    double across=((i%4)/3.0-.5)*.65, behind=.1+(i/4)*.17;
                    emit(p,e.style(),origin.add(Math.cos(yaw)*across+Math.sin(yaw)*behind,.06,Math.sin(yaw)*across-Math.cos(yaw)*behind));
                }
            }
            case "aura" -> {
                int reserve=(equipped(p,"crown")!=null?12:0)+(equipped(p,"circle")!=null?12:0);
                int points=Math.min(20,Math.max(12,playerBudget-reserve));
                if(playerBudget<points||globalBudget<points)return;
                for(int i=0;i<points;i++) {
                    double t=i/(double)(points-1),a=phase+t*Math.PI*4;
                    attached(p,e.style(),origin.add(Math.cos(a)*.55,.08+t*(p.getHeight()-.1),Math.sin(a)*.55));
                }
            }
            case "circle" -> {if(playerBudget<12||globalBudget<12)return;for(int i=0;i<12;i++){double a=phase+i*Math.PI/6;attached(p,e.style(),origin.add(Math.cos(a)*.85,.08,Math.sin(a)*.85));}}
            case "crown" -> {
                if(playerBudget<12||globalBudget<12)return;
                for(int i=0;i<12;i++) {
                    double a=i*Math.PI/6;
                    attached(p,e.style(),origin.add(Math.cos(a)*.36,p.getHeight()-.18+(i%2)*.04,Math.sin(a)*.36));
                }
            }
            case "wings" -> {
                if(p.hasVehicle()||p.isSwimming()||p.isFallFlying()||playerBudget<32||globalBudget<32)return;
                double yaw=Math.toRadians(p.getBodyYaw());
                // Mirrored feather contours: upper edge, pointed tip, scalloped lower edge and root.
                double[][] outline={{.14,.66},{.32,.78},{.52,.9},{.76,.98},{1.0,1.04},{1.26,1.08},{1.16,.9},{1.04,.73},{.94,.6},{.86,.7},{.72,.49},{.64,.59},{.49,.4},{.4,.52},{.25,.44},{.14,.57}};
                for(int side:new int[]{-1,1})for(var point:outline) {
                    double x=side*point[0],back=.3+Math.sin(phase)*.04*point[0];
                    attached(p,e.style(),origin.add(Math.cos(yaw)*x+Math.sin(yaw)*back,point[1]*p.getHeight(),Math.sin(yaw)*x-Math.cos(yaw)*back),true);
                }
            }
        }
    }
    /** Vanilla dust scale 0.095 has a 1–3 tick maxAge in MC 1.21.1's AbstractDustParticle.
     * Refresh wings no faster than four ticks. No invented lifetime field in particle packets.
     */
    private void attached(ServerPlayerEntity p,String style,Vec3d pos) {attached(p,style,pos,false);}
    private void attached(ServerPlayerEntity p,String style,Vec3d pos,boolean wing) {
        Vector3f color=switch(style) {
            case "fire" -> new Vector3f(1,.35f,.08f);
            case "water" -> new Vector3f(.15f,.65f,1);
            case "nature" -> new Vector3f(.25f,1,.35f);
            case "electric" -> new Vector3f(1,.9f,.12f);
            case "soul" -> new Vector3f(.6f,.3f,1);
            case "heart" -> new Vector3f(1,.35f,.6f);
            default -> new Vector3f(.75f,1,1);
        };
        emit(p,new DustParticleEffect(color,.095f),pos,wing);
    }
    private static SimpleParticleType particle(String style) {
        return switch(style) {case "fire"->ParticleTypes.FLAME;case "water"->ParticleTypes.SPLASH;case "nature"->ParticleTypes.HAPPY_VILLAGER;case "electric"->ParticleTypes.ELECTRIC_SPARK;case "soul"->ParticleTypes.SOUL;case "snow"->ParticleTypes.SNOWFLAKE;case "heart"->ParticleTypes.HEART;default->ParticleTypes.END_ROD;};
    }
    private void emit(ServerPlayerEntity p,String style,Vec3d pos) {emit(p,particle(style),pos);}
    private void emit(ServerPlayerEntity p,ParticleEffect effect,Vec3d pos) {emit(p,effect,pos,false);}
    private void emit(ServerPlayerEntity p,ParticleEffect effect,Vec3d pos,boolean wing) {
        if(globalBudget<=0||playerBudget<=0)return;
        globalBudget--;playerBudget--;
        for(var viewer:p.getServerWorld().getPlayers()) {
            if(wing&&models.supports(viewer))continue;
            var state=selected.get(viewer.getUuid());
            if(state!=null&&state.cosmeticsHidden)continue; // personal opt-out also hides other players' effects
            if(viewer.getPos().squaredDistanceTo(pos)<=config.viewDistance*config.viewDistance)
                p.getServerWorld().spawnParticles(viewer,effect,false,pos.x,pos.y,pos.z,1,0,0,0,0);
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
