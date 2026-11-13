package dev.shirokai.essentials.api;

import dev.shirokai.essentials.ShirokaiEssentials;
import java.util.*;
import java.util.function.BiFunction;

/** Protocol 1: JDK-only boundary, server thread only. No world/profile identity changes. */
public final class EssentialsBridge {
    private static Object owner;
    private static BiFunction<UUID,Map<String,String>,String> chat;
    private EssentialsBridge() {}
    public static int protocol(){return 1;}
    private static ShirokaiEssentials app(){
        var app=ShirokaiEssentials.instance();
        if(app==null||app.server()==null||!app.server().isOnThread())throw new IllegalStateException("Essentials bridge unavailable outside server thread");
        return app;
    }
    public static boolean enabled(){return app().settings().integrations.coreBridge;}
    public static void attach(Object token,BiFunction<UUID,Map<String,String>,String> router){
        if(!enabled())throw new IllegalStateException("Bridge disabled");if(owner!=null&&owner!=token)throw new IllegalStateException("Chat bridge already owned");
        owner=Objects.requireNonNull(token);chat=Objects.requireNonNull(router);
    }
    public static void detach(Object token){if(owner==token){owner=null;chat=null;}}
    public static void clear(){owner=null;chat=null;}
    public static boolean ownsChat(){return chat!=null;}
    public static String route(UUID player,Map<String,String> request){app();if(chat==null)throw new IllegalStateException("Core chat unavailable");return chat.apply(player,Map.copyOf(request));}
    /** Only already-loaded online profiles: this API never reads JSON while rendering. */
    public static Map<String,String> snapshot(UUID id){
        var a=app();var p=a.server().getPlayerManager().getPlayer(id);
        if(p==null||!a.data().loaded(id))return Map.of();
        try{
            var d=a.data().player(id);var values=new HashMap<String,String>();
            String nick=a.settings().module("chat")&&a.settings().feature("nicknames")?d.nickname:"";
            values.put("nickname",nick);values.put("realname",p.getName().getString());values.put("displayname",nick.isEmpty()?p.getName().getString():nick);
            boolean afk=a.settings().module("chat")&&a.chat.afk(id);
            values.put("afk",Boolean.toString(afk));values.put("afk_suffix",afk?" [AFK]":"");
            values.put("fly",Boolean.toString(a.utilities.flight(p)));values.put("god",Boolean.toString(a.utilities.god(p)));
            if(a.settings().module("homes")){values.put("homes",Integer.toString(d.homes.size()));values.put("home_limit",Integer.toString(a.permissions.homeLimit(p.getCommandSource())));}
            if(a.settings().module("chat")&&a.settings().feature("mail"))values.put("mail",Integer.toString(d.mail.size()));
            if(a.settings().module("teleport"))values.put("teleport_seconds",Long.toString(a.settings().feature("teleport_cooldown")?Math.max(0,(d.nextTeleportAt-System.currentTimeMillis()+999)/1000):0));
            if(a.settings().module("kits")){
                int ready=0;long next=Long.MAX_VALUE;
                for(var e:a.settings().kits.kits.entrySet())if(a.kits.access(p.getCommandSource(),e.getKey())){
                    long remaining=a.settings().feature("kit_cooldowns")&&!a.permissions.has(p.getCommandSource(),"kit.bypass.cooldown")?Math.max(0,(d.kitCooldowns.getOrDefault(e.getKey(),0L)-System.currentTimeMillis()+999)/1000):0;
                    if(remaining==0&&d.pendingKit.isEmpty())ready++;next=Math.min(next,remaining);
                }
                values.put("kits_ready",Integer.toString(ready));values.put("kit_pending",Boolean.toString(!d.pendingKit.isEmpty()));
                if(next!=Long.MAX_VALUE&&d.pendingKit.isEmpty())values.put("kit_seconds",Long.toString(next));
            }
            return Map.copyOf(values);
        }catch(java.io.IOException e){throw new IllegalStateException("Profile unavailable",e);}
    }
    public static String rejection(UUID id){
        var a=app();if(!a.data().loaded(id))return "Perfil Essentials carregando";
        if(a.moderation.enabled()&&a.settings().feature("moderation_chat")){
            var state=a.moderation.state().status(id);
            if(state.muted(System.currentTimeMillis()))return "Você está silenciado: "+state.muteReason();
        }
        return "";
    }
    public static boolean accepts(UUID receiver,UUID sender,boolean privateMessage){
        var a=app();if(!a.settings().module("chat"))return true;
        if(!a.data().loaded(receiver)||!a.data().loaded(sender))return false;
        try{var r=a.data().messaging(receiver);var s=a.data().messaging(sender);
            return !r.ignored().contains(sender)&&(!privateMessage||!r.blocked()&&!s.blocked()&&!s.ignored().contains(receiver));
        }catch(java.io.IOException e){return false;}
    }
    public static boolean ignored(UUID player,UUID target){
        var a=app();if(!a.settings().module("chat"))return false;
        if(!a.data().loaded(player))throw new IllegalStateException("Profile loading");
        try{return a.data().messaging(player).ignored().contains(target);}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static void unignore(UUID player,UUID target){var a=app();if(ignored(player,target))try{a.data().update(player,d->d.ignored.remove(target));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public static void activity(UUID id){var a=app();var p=a.server().getPlayerManager().getPlayer(id);if(p!=null&&a.settings().module("chat"))a.chat.touch(p);}
    public static void delivered(UUID sender,UUID target,String text){
        var a=app();if(!a.settings().module("chat")||!a.settings().feature("socialspy"))return;
        var from=a.server().getPlayerManager().getPlayer(sender);var to=a.server().getPlayerManager().getPlayer(target);if(from==null||to==null)return;
        for(var viewer:a.server().getPlayerManager().getPlayerList())if(viewer!=from&&viewer!=to&&a.permissions.has(viewer.getCommandSource(),"socialspy"))try{
            if(!route(viewer.getUuid(),Map.of("action","visible","target",sender.toString())).equals("true")||!route(viewer.getUuid(),Map.of("action","visible","target",target.toString())).equals("true"))continue;
            if(a.data().socialSpy(viewer.getUuid()))a.messages.send(viewer.getCommandSource(),"spy","sender",from.getName().getString(),"player",to.getName().getString(),"message",text);
        }catch(java.io.IOException e){ShirokaiEssentials.LOGGER.warn("Socialspy profile unavailable",e);}
    }
}
