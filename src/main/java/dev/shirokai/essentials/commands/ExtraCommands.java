package dev.shirokai.essentials.commands;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.shirokai.essentials.*;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.moderation.Durations;
import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.teleport.TeleportService;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

final class ExtraCommands {
    private final ShirokaiEssentials app; private final Commands owner;
    ExtraCommands(ShirokaiEssentials app,Commands owner){this.app=app;this.owner=owner;}
    private String str(CommandContext<ServerCommandSource> c,String key){return StringArgumentType.getString(c,key);}
    private String name(CommandContext<ServerCommandSource> c){return str(c,"name").toLowerCase(Locale.ROOT);}
    private DataStore.Profile profile(CommandContext<ServerCommandSource> c){return app.chat.resolve(c.getSource().getServer(),str(c,"player"));}
    LiteralArgumentBuilder<ServerCommandSource> build(String cmd,String root){
        var b=literal(root);
        return switch(cmd){
            case "msg","tell","whisper" -> b.then(argument("player",EntityArgumentType.player()).then(argument("message",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.chat.message(c.getSource().getPlayerOrThrow(),EntityArgumentType.getPlayer(c,"player"),str(c,"message"))))));
            case "r","reply" -> b.then(argument("message",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.chat.reply(c.getSource().getPlayerOrThrow(),str(c,"message")))));
            case "ignore","unignore" -> b.then(argument("player",StringArgumentType.word()).executes(owner.action(cmd,c->{
                var p=c.getSource().getPlayerOrThrow();var target=profile(c);if(target==null)return app.messages.error(c.getSource(),"player_missing");
                if(target.id().equals(p.getUuid()))return app.messages.error(c.getSource(),"self");
                app.data().update(p.getUuid(),d->{if(cmd.equals("ignore"))d.ignored.add(target.id());else d.ignored.remove(target.id());});if(cmd.equals("unignore")&&dev.shirokai.essentials.api.EssentialsBridge.ownsChat())app.chat.routed(p,Map.of("action","unignore","target",target.id().toString()));return app.messages.send(c.getSource(),"ignored","player",target.name());
            })));
            case "msgtoggle","socialspy" -> b.executes(owner.action(cmd,c->{
                var p=c.getSource().getPlayerOrThrow();var d=app.data().player(p.getUuid());boolean enabled=cmd.equals("socialspy")?!d.socialSpy:!d.messagesBlocked;
                app.data().update(p.getUuid(),next->{if(cmd.equals("socialspy"))next.socialSpy=enabled;else next.messagesBlocked=enabled;});
                return app.messages.send(c.getSource(),"state","feature",cmd,"state",enabled?"ativado":"desativado");
            }));
            case "mail" -> mail(b,cmd);
            case "motd","rules","info","help" -> b.executes(owner.action(cmd,c->app.chat.lines(c.getSource(),switch(cmd){case "motd"->app.settings().chat.motd;case "rules"->app.settings().chat.rules;case "info"->app.settings().chat.info;default->app.settings().chat.help;})));
            case "afk" -> b.executes(owner.action(cmd,c->app.chat.toggleAfk(c.getSource().getPlayerOrThrow())));
            case "nick" -> b.then(argument("nickname",StringArgumentType.word()).executes(owner.action(cmd,c->nick(c,c.getSource().getPlayerOrThrow())))
                .then(argument("player",EntityArgumentType.player()).requires(s->app.permissions.has(s,"nick.others")).executes(owner.action(cmd,c->nick(c,EntityArgumentType.getPlayer(c,"player"))))));
            case "realname" -> b.then(argument("nickname",StringArgumentType.word()).executes(owner.action(cmd,c->{
                for(var p:c.getSource().getServer().getPlayerManager().getPlayerList())if(app.chat.display(p).equalsIgnoreCase(str(c,"nickname")))return app.messages.send(c.getSource(),"realname","nickname",app.chat.display(p),"player",p.getName().getString());return app.messages.error(c.getSource(),"player_missing");
            })));
            case "list" -> b.executes(owner.action(cmd,c->{List<String> players=new ArrayList<>();for(var p:c.getSource().getServer().getPlayerManager().getPlayerList())players.add(app.chat.display(p));return app.messages.send(c.getSource(),"players","count",players.size(),"players",String.join(", ",players));}));
            case "seen","whois" -> b.then(argument("player",StringArgumentType.word()).executes(owner.action(cmd,c->{
                var p=profile(c);if(p==null)return app.messages.error(c.getSource(),"player_missing");
                if(cmd.equals("seen"))return app.messages.send(c.getSource(),"seen","player",p.name(),"uuid",p.id(),"time",java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(java.time.ZoneId.of(app.settings().chat.timeZone)).format(Instant.ofEpochMilli(p.lastSeen())));
                var online=c.getSource().getServer().getPlayerManager().getPlayer(p.id());
                return app.messages.send(c.getSource(),"whois","player",p.name(),"uuid",p.id(),"world",online==null?"offline":online.getServerWorld().getRegistryKey().getValue(),"position",online==null?"offline":online.getBlockPos().toShortString());
            })));
            case "broadcast","me" -> b.then(argument("message",StringArgumentType.greedyString()).executes(owner.action(cmd,c->{
                String text=str(c,"message");if(!app.chat.textValid(text))return app.messages.error(c.getSource(),"invalid","value","mensagem");
                if(c.getSource().isExecutedByPlayer()&&!app.moderation.canSpeak(c.getSource().getPlayer()))return 0;
                c.getSource().getServer().getPlayerManager().broadcast(app.messages.text(cmd.equals("me")?"emote":"broadcast","player",c.getSource().getName(),"message",text),false);return 1;
            })));
            case "fly","god","heal","feed","repair","hat","clearinventory","invsee","enderchest" -> b.executes(owner.action(cmd,c->app.utilities.execute(c.getSource(),cmd,c.getSource().getPlayerOrThrow(),1)))
                .then(argument("player",EntityArgumentType.player()).requires(s->app.permissions.has(s,cmd+".others")).executes(owner.action(cmd,c->app.utilities.execute(c.getSource(),cmd,EntityArgumentType.getPlayer(c,"player"),1))));
            case "speed" -> b.then(argument("speed",IntegerArgumentType.integer(1,10)).executes(owner.action(cmd,c->app.utilities.execute(c.getSource(),cmd,c.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(c,"speed"))))
                .then(argument("player",EntityArgumentType.player()).requires(s->app.permissions.has(s,"speed.others")).executes(owner.action(cmd,c->app.utilities.execute(c.getSource(),cmd,EntityArgumentType.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"speed"))))));
            case "luz" -> b.executes(owner.action(cmd,c->app.utilities.light(c.getSource().getPlayerOrThrow())));
            case "esquentar" -> b.executes(owner.action(cmd,c->app.utilities.smelt(c.getSource().getPlayerOrThrow())));
            case "workbench","anvil","grindstone" -> b.executes(owner.action(cmd,c->app.utilities.station(c.getSource().getPlayerOrThrow(),cmd)));
            case "ping" -> b.executes(owner.action(cmd,c->app.messages.send(c.getSource(),"ping","ping",c.getSource().getPlayerOrThrow().networkHandler.getLatency())));
            case "gc" -> b.executes(owner.action(cmd,c->{var runtime=Runtime.getRuntime();return app.messages.send(c.getSource(),"memory","used",(runtime.totalMemory()-runtime.freeMemory())/(1024*1024),"max",runtime.maxMemory()/(1024*1024),"count",c.getSource().getServer().getCurrentPlayerCount());}));
            case "sudo" -> b.then(argument("player",EntityArgumentType.player()).requires(s->app.permissions.has(s,"sudo.others")).then(argument("message",StringArgumentType.greedyString()).executes(owner.action(cmd,this::sudo))));
            case "time" -> b.then(argument("ticks",IntegerArgumentType.integer(0,24000)).executes(owner.action(cmd,c->{c.getSource().getWorld().setTimeOfDay(IntegerArgumentType.getInteger(c,"ticks"));return app.messages.send(c.getSource(),"ok");})));
            case "weather" -> b.then(argument("type",StringArgumentType.word()).suggests((c,s)->{for(String type:List.of("clear","rain","thunder"))s.suggest(type);return s.buildFuture();}).executes(owner.action(cmd,c->{String t=str(c,"type");if(!List.of("clear","rain","thunder").contains(t))return app.messages.error(c.getSource(),"invalid","value",t);c.getSource().getWorld().setWeather(t.equals("clear")?6000:0,t.equals("clear")?0:6000,!t.equals("clear"),t.equals("thunder"));return app.messages.send(c.getSource(),"ok");})));
            case "kick","ban","mute","warn" -> b.then(argument("player",StringArgumentType.word()).then(argument("reason",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.moderation.punish(c.getSource(),cmd,profile(c),str(c,"reason"),Long.MAX_VALUE)))));
            case "tempban","tempmute" -> b.then(argument("player",StringArgumentType.word()).then(argument("duration",StringArgumentType.word()).then(argument("reason",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.moderation.punish(c.getSource(),cmd,profile(c),str(c,"reason"),duration(c)))))));
            case "unban","unmute","clearwarnings" -> b.then(argument("player",StringArgumentType.word()).executes(owner.action(cmd,c->app.moderation.punish(c.getSource(),cmd,profile(c),"Ação administrativa",0))));
            case "warnings" -> b.then(argument("player",StringArgumentType.word()).executes(owner.action(cmd,c->{var p=profile(c);if(p==null)return app.messages.error(c.getSource(),"player_missing");return app.messages.send(c.getSource(),"warnings","player",p.name(),"warnings",app.moderation.state().get(p.id()).warnings);}))); 
            case "freeze" -> b.then(argument("player",StringArgumentType.word()).executes(owner.action(cmd,c->app.moderation.freeze(c.getSource(),profile(c)))));
            case "setjail" -> b.then(argument("name",StringArgumentType.word()).executes(owner.action(cmd,c->{String n=name(c);if(!Settings.validName(n))return app.messages.error(c.getSource(),"invalid_name");var pos=TeleportService.position(c.getSource().getPlayerOrThrow());app.data().audit(c.getSource().getName(),cmd,n,"");app.data().updateWorld(w->w.jails.put(n,pos));return app.messages.send(c.getSource(),"saved","name",n);})));
            case "deljail" -> b.then(argument("name",StringArgumentType.word()).executes(owner.action(cmd,c->{String n=name(c);if(app.moderation.state().active().stream().anyMatch(id->app.moderation.state().get(id).jail.equals(n)))return app.messages.error(c.getSource(),"busy");app.data().updateWorld(w->w.jails.remove(n));return app.messages.send(c.getSource(),"deleted","name",n);})));
            case "jail" -> b.then(argument("player",StringArgumentType.word()).then(argument("name",StringArgumentType.word()).executes(owner.action(cmd,c->app.moderation.jail(c.getSource(),profile(c),name(c),Long.MAX_VALUE)))
                .then(argument("duration",StringArgumentType.word()).executes(owner.action(cmd,c->app.moderation.jail(c.getSource(),profile(c),name(c),duration(c)))))));
            case "unjail" -> b.then(argument("player",StringArgumentType.word()).executes(owner.action(cmd,c->app.moderation.release(c.getSource(),profile(c)))));
            case "kit","showkit" -> b.then(argument("name",StringArgumentType.word()).suggests((c,s)->{app.settings().kits.kits.keySet().stream().filter(n->app.kits.access(c.getSource(),n)).forEach(s::suggest);return s.buildFuture();}).executes(owner.action(cmd,c->cmd.equals("kit")?app.kits.claim(c.getSource().getPlayerOrThrow(),name(c),false):app.kits.preview(c.getSource().getPlayerOrThrow(),name(c)))));
            case "kits" -> b.executes(owner.action(cmd,c->app.messages.send(c.getSource(),"list","names",String.join(", ",app.settings().kits.kits.keySet().stream().filter(n->app.kits.access(c.getSource(),n)).toList()))));
            case "createkit" -> b.then(argument("name",StringArgumentType.word()).executes(owner.action(cmd,c->app.kits.create(c.getSource().getPlayerOrThrow(),name(c)))));
            case "delkit" -> b.then(argument("name",StringArgumentType.word()).executes(owner.action(cmd,c->app.kits.delete(c.getSource(),name(c)))));
            case "resetkit","kitrecover" -> b.then(argument("player",StringArgumentType.word()).then(argument("name",StringArgumentType.word()).executes(owner.action(cmd,c->{var p=profile(c);if(p==null)return app.messages.error(c.getSource(),"player_missing");return cmd.equals("resetkit")?app.kits.reset(c.getSource(),p.id(),name(c)):app.kits.recover(c.getSource(),p.id(),str(c,"name"));}))));

            case "ecimport" -> b.then(literal("dry-run").then(argument("path",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.migrations.run(c.getSource(),str(c,"path"),false)))))
                .then(literal("apply").requires(s->app.permissions.has(s,"ecimport.apply")).then(argument("path",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.migrations.run(c.getSource(),str(c,"path"),true)))));
            default -> throw new IllegalArgumentException("Comando não implementado: "+cmd);
        };
    }
    private long duration(CommandContext<ServerCommandSource> c)throws CommandSyntaxException{
        try{return Durations.millis(str(c,"duration"));}catch(IllegalArgumentException|ArithmeticException e){throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(net.minecraft.text.Text.literal(e.getMessage())).create();}
    }
    private int nick(CommandContext<ServerCommandSource> c,ServerPlayerEntity target)throws IOException{
        if(c.getSource().getEntity()!=target&&!app.permissions.has(c.getSource(),"nick.others"))return app.messages.error(c.getSource(),"denied");
        String value=str(c,"nickname");if(value.equalsIgnoreCase("off"))value="";
        if(!value.isEmpty()&&(!value.matches("[\\p{L}0-9_]{3,24}")||app.data().profile(value).filter(p->!p.id().equals(target.getUuid())).isPresent()))return app.messages.error(c.getSource(),"invalid","value","apelido");
        for(var p:c.getSource().getServer().getPlayerManager().getPlayerList())if(p!=target&&app.chat.display(p).equalsIgnoreCase(value))return app.messages.error(c.getSource(),"invalid","value","apelido em uso");
        String nickname=value;app.data().update(target.getUuid(),d->d.nickname=nickname);return app.messages.send(c.getSource(),"nick","player",nickname.isEmpty()?target.getName().getString():nickname);
    }
    private LiteralArgumentBuilder<ServerCommandSource> mail(LiteralArgumentBuilder<ServerCommandSource> b,String cmd){
        return b.executes(owner.action(cmd,c->app.messages.send(c.getSource(),"mail_notice","count",app.data().player(c.getSource().getPlayerOrThrow().getUuid()).mail.size())))
            .then(literal("send").then(argument("player",StringArgumentType.word()).then(argument("message",StringArgumentType.greedyString()).executes(owner.action(cmd,c->app.chat.sendMail(c.getSource().getPlayerOrThrow(),profile(c),str(c,"message")))))))
            .then(literal("read").executes(owner.action(cmd,c->readMail(c,1))).then(argument("page",IntegerArgumentType.integer(1,100)).executes(owner.action(cmd,c->readMail(c,IntegerArgumentType.getInteger(c,"page"))))))
            .then(literal("clear").executes(owner.action(cmd,c->{app.data().update(c.getSource().getPlayerOrThrow().getUuid(),d->d.mail.clear());return app.messages.send(c.getSource(),"mail_clear");})));
    }
    private int readMail(CommandContext<ServerCommandSource> c,int page)throws IOException,CommandSyntaxException{
        var mail=app.data().player(c.getSource().getPlayerOrThrow().getUuid()).mail;int from=(page-1)*10;
        for(int i=from;i<Math.min(from+10,mail.size());i++){var e=mail.get(i);app.messages.send(c.getSource(),"mail_line","id",i+1,"player",e.senderName(),"message",e.content());}return 1;
    }
    private int sudo(CommandContext<ServerCommandSource> c)throws IOException,CommandSyntaxException{
        var target=EntityArgumentType.getPlayer(c,"player");String command=str(c,"message");String root=command.split(" ",2)[0];
        if(command.contains("\n")||command.contains("\r")||command.startsWith("/")||!app.settings().utilities.allowSudo||!app.settings().utilities.sudoAllowedCommands.contains(root)||!app.permissions.has(c.getSource(),"sudo.command."+root))return app.messages.error(c.getSource(),"denied");
        var profile=new DataStore.Profile(target.getUuid(),target.getName().getString(),0,0);if(!app.moderation.guard(c.getSource(),profile))return 0;
        app.data().audit(c.getSource().getName(),"sudo",target.getUuidAsString(),command);
        c.getSource().getServer().getCommandManager().executeWithPrefix(target.getCommandSource().withLevel(0),command);return 1;
    }
}
