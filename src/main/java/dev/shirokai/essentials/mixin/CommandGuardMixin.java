package dev.shirokai.essentials.mixin;

import com.mojang.brigadier.ParseResults;
import dev.shirokai.essentials.ShirokaiEssentials;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric's message events do not cover /msg. Guard the fixed 1.21.1 execution entrypoint. */
@Mixin(CommandManager.class)
public abstract class CommandGuardMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private void shirokai$guard(ParseResults<ServerCommandSource> parse,String command,CallbackInfo ci){
        var app=ShirokaiEssentials.instance();if(app==null)return;
        var source=parse.getContext().getSource();if(!source.isExecutedByPlayer())return;
        var parsed=parse.getContext().getNodes();
        String entered=parsed.isEmpty()?"":parsed.getFirst().getNode().getName();
        var afk=app.settings().commands.commands.get("afk");
        boolean isAfk=entered.equals("afk")||(afk!=null&&afk.aliases.contains(entered));
        if(!isAfk&&app.settings().module("chat")&&(app.settings().feature("afk_auto")||app.settings().feature("afk_manual")))app.chat.touch(source.getPlayer());
        if(!app.settings().module("moderation")||!app.settings().feature("moderation_chat")||!app.settings().moderation.enforceCommandMute)return;
        var nodes=parse.getContext().getNodes();if(nodes.isEmpty())return;
        var node=nodes.getFirst().getNode();
        for(int depth=0;depth<8&&node.getRedirect()!=null;depth++)node=node.getRedirect();
        String name=node.getName();if(name.contains(":"))name=name.substring(name.indexOf(':')+1);
        if(app.settings().moderation.mutedCommandRoots.contains(name)&&!app.moderation.canSpeak(source.getPlayer()))ci.cancel();
    }
}
