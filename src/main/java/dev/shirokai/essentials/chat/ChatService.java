package dev.shirokai.essentials.chat;

import dev.shirokai.essentials.*;
import dev.shirokai.essentials.persistence.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import java.util.*;

public final class ChatService {
    private record Activity(double x, double y, double z, long time) {}
    private final ShirokaiEssentials app;
    private final Map<UUID,UUID> replies = new HashMap<>();
    private final Map<UUID,Long> lastMail = new HashMap<>();
    private final Map<UUID,Activity> activity = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();
    public ChatService(ShirokaiEssentials app) { this.app = app; }
    public String display(ServerPlayerEntity p) throws IOException {
        String nick = app.settings().feature("nicknames") ? app.data().nickname(p.getUuid()) : "";
        return nick.isEmpty() ? p.getName().getString() : nick;
    }
    public DataStore.Profile resolve(MinecraftServer server, String value) {
        var online = server.getPlayerManager().getPlayer(value);
        if (online != null) return new DataStore.Profile(online.getUuid(), online.getName().getString(), 0, System.currentTimeMillis());
        return app.data().profile(value).orElse(null);
    }
    public boolean textValid(String text) { return !text.isBlank() && text.length() <= app.settings().chat.maxMessageLength && text.codePoints().noneMatch(c -> Character.isISOControl(c)); }
    public int message(ServerPlayerEntity from, ServerPlayerEntity to, String text) throws IOException {
        if (!textValid(text)) return app.messages.error(from.getCommandSource(), "invalid", "value", "mensagem");
        if(dev.shirokai.essentials.api.EssentialsBridge.ownsChat())return routed(from,Map.of("action","message","target",to.getUuidAsString(),"text",text));
        if (!app.moderation.canSpeak(from)) return 0;
        var a = app.data().messaging(from.getUuid()); var b = app.data().messaging(to.getUuid());
        if (a.blocked() || b.blocked() || a.ignored().contains(to.getUuid()) || b.ignored().contains(from.getUuid())) return app.messages.error(from.getCommandSource(), "private_blocked");
        app.messages.send(from.getCommandSource(), "private_sent", "player", display(to), "message", text);
        app.messages.send(to.getCommandSource(), "private_received", "player", display(from), "message", text);
        replies.put(from.getUuid(),to.getUuid()); replies.put(to.getUuid(),from.getUuid()); touch(from);
        if (app.settings().feature("socialspy")) for (var p : from.getServer().getPlayerManager().getPlayerList()) {
            if (p != from && p != to && app.data().socialSpy(p.getUuid()) && app.permissions.has(p.getCommandSource(), "socialspy")) app.messages.send(p.getCommandSource(), "spy", "sender", from.getName().getString(), "player", to.getName().getString(), "message", text);
        }
        return 1;
    }
    public int reply(ServerPlayerEntity from, String text) throws IOException {
        if(dev.shirokai.essentials.api.EssentialsBridge.ownsChat())return routed(from,Map.of("action","reply","text",text));
        UUID id = replies.get(from.getUuid()); var to = id == null ? null : from.getServer().getPlayerManager().getPlayer(id);
        return to == null ? app.messages.error(from.getCommandSource(), "reply_missing") : message(from,to,text);
    }
    public int routed(ServerPlayerEntity player,Map<String,String> request) {
        try {String error=dev.shirokai.essentials.api.EssentialsBridge.route(player.getUuid(),request);
            if(!error.isEmpty()){player.sendMessage(net.minecraft.text.Text.literal(error),false);return 0;}return 1;
        }catch(RuntimeException error){player.sendMessage(net.minecraft.text.Text.literal("Chat Core indisponível"),false);return 0;}
    }
    public int sendMail(ServerPlayerEntity from, DataStore.Profile to, String text) throws IOException {
        if (to == null) return app.messages.error(from.getCommandSource(), "player_missing");
        if (!textValid(text)) return app.messages.error(from.getCommandSource(), "invalid", "value", "mensagem");
        if (!app.moderation.canSpeak(from)) return 0;
        var recipient = app.data().player(to.id());
        if (recipient.messagesBlocked || recipient.ignored.contains(from.getUuid()) || app.data().player(from.getUuid()).ignored.contains(to.id())) return app.messages.error(from.getCommandSource(), "private_blocked");
        if (recipient.mail.size() >= app.settings().chat.mailboxLimit) return app.messages.error(from.getCommandSource(), "mail_full");
        long now = System.currentTimeMillis(); long wait = app.settings().chat.mailCooldownSeconds * 1000L - (now - lastMail.getOrDefault(from.getUuid(),0L));
        if (wait > 0) return app.messages.error(from.getCommandSource(), "mail_wait", "time", (wait+999)/1000);
        var mail = new DataStore.Mail(UUID.randomUUID().toString(),from.getUuid(),from.getName().getString(),text,now);
        app.data().update(to.id(),d -> d.mail.add(mail)); lastMail.put(from.getUuid(),now);
        return app.messages.send(from.getCommandSource(), "mail_sent", "player", to.name());
    }
    public boolean afk(UUID id) { return afk.contains(id); }
    public void touch(ServerPlayerEntity p) {
        activity.put(p.getUuid(),new Activity(p.getX(),p.getY(),p.getZ(),System.currentTimeMillis()));
        if (afk.remove(p.getUuid())) announceAfk(p,false);
    }
    public int toggleAfk(ServerPlayerEntity p) {
        boolean enabled = !afk.remove(p.getUuid()); if (enabled) afk.add(p.getUuid());
        activity.put(p.getUuid(),new Activity(p.getX(),p.getY(),p.getZ(),System.currentTimeMillis()));
        announceAfk(p,enabled); return 1;
    }
    private void announceAfk(ServerPlayerEntity p, boolean enabled) { p.getServer().getPlayerManager().broadcast(app.messages.text("afk", "player", p.getName().getString(), "state", enabled ? "ausente" : "disponível"),false); }
    public void tick(MinecraftServer server) {
        if (!app.settings().module("chat") || !app.settings().feature("afk_auto") || server.getTicks()%20 != 0) return;
        long now=System.currentTimeMillis();
        for (var p : server.getPlayerManager().getPlayerList()) {
            Activity a=activity.get(p.getUuid());
            if (a == null || p.getPos().squaredDistanceTo(a.x,a.y,a.z)>.01) { touch(p); continue; }
            if (!afk.contains(p.getUuid()) && now-a.time >= app.settings().chat.afkSeconds*1000L) { afk.add(p.getUuid()); announceAfk(p,true); }
        }
    }
    public void join(ServerPlayerEntity p, boolean first) throws IOException {
        if (!app.settings().module("chat")) return;
        touch(p);
        if (app.settings().feature("motd_auto")) lines(p.getCommandSource(),app.settings().chat.motd);
        if (first && app.settings().feature("first_join_messages")) p.getServer().getPlayerManager().broadcast(app.messages.text("first_join", "player",p.getName().getString()),false);
        if (app.settings().feature("mail")) { int count=app.data().player(p.getUuid()).mail.size(); if (count>0) app.messages.send(p.getCommandSource(), "mail_notice", "count",count); }
    }
    public int lines(ServerCommandSource source, List<String> lines) {
        for (String line : lines) app.messages.configuredLine(source,line,"player",source.getName()); return 1;
    }
    public void disconnect(UUID id) { replies.remove(id); replies.values().removeIf(id::equals); activity.remove(id); afk.remove(id); lastMail.remove(id); }
    public void clear() { replies.clear(); activity.clear(); afk.clear(); lastMail.clear(); }
}
