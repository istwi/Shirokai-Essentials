package dev.shirokai.essentials;

import dev.shirokai.essentials.config.Settings;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import java.util.function.Supplier;

public final class Messages {
    private static final java.util.Map<String,String> DEFAULTS=java.util.Map.copyOf(new Settings.Messages().messages);
    private static final MiniMessage MINI=MiniMessage.miniMessage();
    private final Supplier<Settings> settings;
    public Messages(Supplier<Settings> settings){this.settings=settings;}
    public Text text(String key,Object... replacements){return render(settings.get().messages.messages.getOrDefault(key,DEFAULTS.getOrDefault(key,key)),false,replacements);}
    private Text render(String body,boolean error,Object... replacements){
        String literal=body;
        var builder=TagResolver.builder();
        for(int i=0;i+1<replacements.length;i+=2){
            String name=String.valueOf(replacements[i]);String value=String.valueOf(replacements[i+1]);
            // Only templates are parsed. Dynamic values never become MiniMessage tags/click actions.
            body=body.replace("{"+name+"}","<shiro_"+name+">");
            builder.resolver(Placeholder.unparsed("shiro_"+name,value));literal=literal.replace("{"+name+"}",value);
        }
        var app=ShirokaiEssentials.instance();
        if(app!=null&&app.server()!=null){
            try{
                var component=MINI.deserialize(body,builder.build());
                Text result=Text.Serialization.fromJson(GsonComponentSerializer.gson().serialize(component),app.server().getRegistryManager());
                if(result!=null)return Text.literal(settings.get().messages.prefix).formatted(Formatting.AQUA).append(result.copy().formatted(error?Formatting.RED:Formatting.GRAY));
            }catch(RuntimeException e){ShirokaiEssentials.LOGGER.warn("Mensagem configurada inválida; usando texto literal: {}",e.getMessage());}
        }
        return Text.literal(settings.get().messages.prefix).formatted(Formatting.AQUA).append(Text.literal(literal).formatted(error?Formatting.RED:Formatting.GRAY));
    }
    public int configuredLine(ServerCommandSource source,String template,Object... values){source.sendFeedback(()->render(template,false,values),false);return 1;}
    public int send(ServerCommandSource source,String key,Object... values){source.sendFeedback(()->text(key,values),false);return 1;}
    public int error(ServerCommandSource source,String key,Object... values){source.sendError(render(settings.get().messages.messages.getOrDefault(key,DEFAULTS.getOrDefault(key,key)),true,values));return 0;}
}
