package dev.shirokai.essentials;

import dev.shirokai.essentials.commands.Commands;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.conflicts.ConflictService;
import dev.shirokai.essentials.permissions.PermissionService;
import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.teleport.TeleportService;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class ShirokaiEssentials implements DedicatedServerModInitializer {
    public static final String VERSION = "0.6.3-simple.1";
    public static final Logger LOGGER = LoggerFactory.getLogger("shirokai_essentials");
    private static ShirokaiEssentials INSTANCE;
    public static ShirokaiEssentials instance() { return INSTANCE; }
    public final dev.shirokai.essentials.cosmetics.CosmeticService cosmetics = new dev.shirokai.essentials.cosmetics.CosmeticService(this);
    public final dev.shirokai.essentials.chat.ChatService chat = new dev.shirokai.essentials.chat.ChatService(this);
    public final dev.shirokai.essentials.moderation.ModerationService moderation = new dev.shirokai.essentials.moderation.ModerationService(this);
    public final dev.shirokai.essentials.utilities.UtilityService utilities = new dev.shirokai.essentials.utilities.UtilityService(this);
    public final dev.shirokai.essentials.kits.KitService kits = new dev.shirokai.essentials.kits.KitService(this);
    public final dev.shirokai.essentials.integrations.BattleGuard battles = new dev.shirokai.essentials.integrations.BattleGuard(this);
    public final dev.shirokai.essentials.migrations.EssentialImporter migrations = new dev.shirokai.essentials.migrations.EssentialImporter(this);
    public Path configDir;
    private Settings settings;
    private DataStore data;
    private net.minecraft.server.MinecraftServer currentServer;
    public net.minecraft.server.MinecraftServer server() { return currentServer; }
    public final ConflictService conflicts = new ConflictService();
    public final PermissionService permissions = new PermissionService(this::settings);
    public final Messages messages = new Messages(this::settings);
    public final TeleportService teleports = new TeleportService(this::settings, this::data, messages, permissions);
    public Settings settings() { return settings; }
    public DataStore data() { return Objects.requireNonNull(data, "Servidor ainda não iniciou"); }
    public String spawnKey(ServerPlayerEntity p) { return settings.spawn.perWorld ? p.getServerWorld().getRegistryKey().getValue().toString() : "global"; }
    public List<String> activeModules() { return Settings.IMPLEMENTED.stream().filter(settings::module).filter(m -> !conflicts.moduleFailed(m)).sorted().toList(); }
    public Map<String,String> integrations() {
        Map<String,String> result = new TreeMap<>();
        for (String id : List.of("luckperms", "cobblemon", "multiworld", "essential_commands", "skieskits")) {
            var mod = FabricLoader.getInstance().getModContainer(id);
            result.put(id, mod.map(c -> c.getMetadata().getVersion().getFriendlyString() + (id.equals("luckperms") ? " (API de permissões)" : " (detectado; integração específica não validada)")).orElse("ausente"));
        }
        result.put("cobblemon-guard",battles.status()); result.put("core-chat",Boolean.toString(dev.shirokai.essentials.api.EssentialsBridge.ownsChat()));
        return result;
    }
    @Override public void onInitializeServer() {
        INSTANCE=this;
        teleports.setRestriction(p->moderation.restricted(p)||battles.blocked(p));
        teleports.setBattleRestriction(battles::blocked);
        configDir = FabricLoader.getInstance().getConfigDir().resolve("shirokai-essentials");
        try { settings = Settings.load(configDir); cosmetics.initialize(); } catch (IOException e) { throw new IllegalStateException("Configuração Shirokai inválida; arquivos preservados", e); }
        if (settings.permissions.requireLuckPerms && !FabricLoader.getInstance().isModLoaded("luckperms")) LOGGER.error("LuckPerms obrigatório ausente: jogadores não terão acesso aos comandos");
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> new Commands(this).register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer=server;
            try { data = new DataStore(server.getSavePath(WorldSavePath.ROOT).resolve("shirokai-essentials")); moderation.start(); }
            catch (IOException e) { throw new IllegalStateException("Dados Shirokai inválidos; inicialização interrompida para preservar arquivos", e); }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            battles.start();
            if (settings.conflicts.enabled && settings.conflicts.scanOnStartup) conflicts.audit(settings, server.getCommandManager().getDispatcher());
            try { conflicts.report(configDir.resolve("conflicts-report.json")); } catch (IOException e) { LOGGER.error("Falha no relatório de conflitos", e); }
            LOGGER.info("Shirokai Essentials iniciado. Módulos: {}. Integrações: {}", activeModules(), integrations());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { dev.shirokai.essentials.api.EssentialsBridge.clear(); teleports.clear(); chat.clear(); utilities.clear(); data = null; currentServer=null; INSTANCE=null; });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            var p=handler.player;
            try {
                boolean first=!data().player(p.getUuid()).joined&&!java.nio.file.Files.exists(server.getSavePath(WorldSavePath.PLAYERDATA).resolve(p.getUuidAsString()+".dat"));
                if(settings.module("chat")||settings.module("moderation")||settings.module("kits"))data().seen(p.getUuid(),p.getName().getString(),System.currentTimeMillis());
                utilities.join(p); moderation.join(p); chat.join(p,first); kits.join(p,first);
                if(!data().player(p.getUuid()).joined)data().update(p.getUuid(),d->d.joined=true);
            } catch(IOException e) { LOGGER.error("Falha ao carregar jogador; login interrompido",e);p.networkHandler.disconnect(messages.text("storage_error")); }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;teleports.disconnect(p.getUuid());chat.disconnect(p.getUuid());utilities.disconnect(p.getUuid());kits.disconnect(p.getUuid());
            if(data!=null){try{if(data.profile(p.getUuidAsString()).isPresent())data.seen(p.getUuid(),p.getName().getString(),System.currentTimeMillis());}catch(IOException e){LOGGER.error("Falha ao atualizar último acesso",e);}data.evict(p.getUuid());}
        });
        if(settings.module("chat")||settings.module("moderation"))ServerTickEvents.END_SERVER_TICK.register(server->{chat.tick(server);moderation.tick(server);});
        if(settings.module("utilities")&&settings.feature("god_mode"))ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->!(entity instanceof ServerPlayerEntity p)||!utilities.god(p));
        if(settings.module("moderation")&&settings.feature("moderation_chat"))net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,p,params)->dev.shirokai.essentials.api.EssentialsBridge.ownsChat()||moderation.canSpeak(p));
        if(settings.module("chat"))net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,p,params)->{
            if(!settings.module("chat"))return true;
            chat.touch(p);if(dev.shirokai.essentials.api.EssentialsBridge.ownsChat()||!settings.chat.formatPublicChat)return true;
            try{p.getServer().getPlayerManager().broadcast(messages.text("public_chat","prefix",permissions.prefix(p),"player",chat.display(p),"message",message.getContent().getString()),false);return false;}
            catch(IOException e){LOGGER.error("Falha ao formatar chat",e);return false;}
        });
        if(settings.module("chat"))net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server,message,overlay)->{
            if(!settings.module("chat")||overlay||!(message.getContent() instanceof net.minecraft.text.TranslatableTextContent t))return true;
            String key=t.getKey(),replace=null;
            if(dev.shirokai.essentials.api.EssentialsBridge.ownsChat()&&(key.startsWith("multiplayer.player.joined")||key.equals("multiplayer.player.left")))return true;
            if(settings.feature("join_messages")&&key.startsWith("multiplayer.player.joined"))replace="join";
            if(settings.feature("quit_messages")&&key.equals("multiplayer.player.left"))replace="quit";
            if(settings.feature("death_messages")&&key.startsWith("death."))replace="death";
            if(replace==null)return true;
            Object who=t.getArgs().length==0?"":t.getArgs()[0];String player=who instanceof net.minecraft.text.Text text?text.getString():String.valueOf(who);
            server.getPlayerManager().broadcast(messages.text(replace,"player",player,"message",message.getString()),false);return false;
        });
        if(settings.module("moderation")){
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->player instanceof ServerPlayerEntity p&&moderation.restricted(p)?net.minecraft.util.ActionResult.FAIL:net.minecraft.util.ActionResult.PASS);
            net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->!(player instanceof ServerPlayerEntity p)||!moderation.restricted(p));
            net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->player instanceof ServerPlayerEntity p&&moderation.restricted(p)?net.minecraft.util.ActionResult.FAIL:net.minecraft.util.ActionResult.PASS);
            net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->player instanceof ServerPlayerEntity p&&moderation.restricted(p)?net.minecraft.util.ActionResult.FAIL:net.minecraft.util.ActionResult.PASS);
            net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,world,hand)->player instanceof ServerPlayerEntity p&&moderation.restricted(p)?net.minecraft.util.TypedActionResult.fail(player.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand)));
        }
        if (settings.module("teleport") || settings.module("homes") || settings.module("warps") || settings.module("spawn")) {
            ServerTickEvents.END_SERVER_TICK.register(teleports::tick);
            ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, amount, blocked) -> { if (entity instanceof ServerPlayerEntity p && amount > 0 && !blocked) teleports.damage(p); });
        }
        if (settings.module("teleport") && settings.feature("back_history") && settings.feature("back_on_death")) ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!settings.module("teleport") || !(entity instanceof ServerPlayerEntity p)) return;
            var pos = TeleportService.position(p);
            if (!teleports.safe(p.getServer(), pos)) return;
            try { data().update(p.getUuid(), d -> d.back = pos); } catch (IOException e) { LOGGER.error("Falha ao salvar posição de morte", e); }
        });
        if (settings.module("spawn") && settings.feature("first_join_spawn")) ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!settings.module("spawn")) return;
            var p = handler.player;
            // Vanilla player-data existence identifies first join without introducing a second identity store.
            Path playerFile = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(p.getUuidAsString() + ".dat");
            if (!java.nio.file.Files.exists(playerFile)) {
                var pos = data().spawn(spawnKey(p));
                if (pos != null) try { teleports.systemTeleport(p, pos); } catch (IOException e) { LOGGER.error("Falha no spawn inicial", e); }
            }
        });
        if (settings.module("spawn") && settings.feature("respawn_at_spawn")) ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive || !settings.module("spawn")) return;
            var pos = data().spawn(spawnKey(newPlayer));
            if (pos != null) try { teleports.systemTeleport(newPlayer, pos); } catch (IOException e) { LOGGER.error("Falha no spawn após morte", e); }
        });
    }
    public int reload(ServerCommandSource source) {
        try {
            Settings next = Settings.load(configDir);
            settings = settings.dynamicFrom(next);
            chat.clear();
            teleports.cancelAll(source.getServer()); // no scheduled work survives a dynamic settings change
            source.getServer().getPlayerManager().getPlayerList().forEach(source.getServer().getCommandManager()::sendCommandTree);
            return messages.send(source, "reload");
        } catch (IOException e) { LOGGER.error("Reload rejeitado; configuração anterior mantida", e); return messages.error(source, "reload_error"); }
    }
}
