package dev.shirokai.essentials.kits;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.shirokai.essentials.*;
import dev.shirokai.essentials.config.*;
import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.utilities.ReadOnlyScreen;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.IOException;
import java.util.*;

public final class KitService {
    public record Receipt(String id, UUID player, String kit, long createdAt, List<JsonElement> before, List<JsonElement> after, String state){}
    private final ShirokaiEssentials app;
    private final Map<UUID,Long> lastClaim=new HashMap<>();
    public KitService(ShirokaiEssentials app){this.app=app;}
    public boolean access(ServerCommandSource source,String name){
        var kit=app.settings().kits.kits.get(name);return kit!=null&&app.permissions.has(source,kit.permission.isBlank()?"kit."+name:kit.permission);
    }
    public List<ItemStack> decode(ServerPlayerEntity player,MoreSettings.Kit kit) throws IOException {
        List<ItemStack> items=new ArrayList<>();
        for(var json:kit.items){
            var item=ItemStack.CODEC.parse(player.getServer().getRegistryManager().getOps(JsonOps.INSTANCE),json).result();
            if(item.isEmpty()||item.get().isEmpty()||item.get().getCount()>item.get().getMaxCount())throw new IOException("Item/data components inválidos no kit");items.add(item.get());
        }
        return items;
    }
    private List<JsonElement> encode(ServerPlayerEntity player,List<ItemStack> items) throws IOException {
        List<JsonElement> out=new ArrayList<>();for(var item:items){
            if(item.isEmpty()){out.add(JsonNull.INSTANCE);continue;}
            var result=ItemStack.CODEC.encodeStart(player.getServer().getRegistryManager().getOps(JsonOps.INSTANCE),item).result();
            if(result.isEmpty())throw new IOException("Falha ao serializar item/data components");out.add(result.get());
        }return out;
    }
    public int create(ServerPlayerEntity p,String name)throws IOException{
        if(!Settings.validName(name))return app.messages.error(p.getCommandSource(),"invalid_name");
        List<ItemStack> items=new ArrayList<>();for(int i=0;i<36;i++)if(!p.getInventory().getStack(i).isEmpty())items.add(p.getInventory().getStack(i).copy());
        if(items.isEmpty())return app.messages.error(p.getCommandSource(),"kit_empty");
        var kit=new MoreSettings.Kit();kit.items=encode(p,items);decode(p,kit);
        var next=JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(app.settings().kits),MoreSettings.Kits.class);next.kits.put(name,kit);
        app.data().audit(p.getName().getString(),"createkit",name,"");
        JsonFiles.write(app.configDir.resolve("kits.json"),next);app.settings().kits=next;
        return app.messages.send(p.getCommandSource(),"kit_saved","name",name);
    }
    public int delete(ServerCommandSource source,String name)throws IOException{
        var next=JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(app.settings().kits),MoreSettings.Kits.class);
        if(next.kits.remove(name)==null)return app.messages.error(source,"kit_missing");
        app.data().audit(source.getName(),"delkit",name,"");JsonFiles.write(app.configDir.resolve("kits.json"),next);app.settings().kits=next;
        return app.messages.send(source,"ok");
    }
    public int claim(ServerPlayerEntity p,String name,boolean firstJoin)throws IOException{
        var cfg=app.settings();var kit=cfg.kits.kits.get(name);
        if(kit==null||(!firstJoin&&!access(p.getCommandSource(),name)))return app.messages.error(p.getCommandSource(),"kit_missing");
        var player=app.data().player(p.getUuid());long now=System.currentTimeMillis();
        if(!player.pendingKit.isEmpty())return app.messages.error(p.getCommandSource(),"kit_pending","id",player.pendingKit);
        if(firstJoin&&player.claimedKits.contains(name))return 0;
        long next=player.kitCooldowns.getOrDefault(name,0L);
        if(cfg.feature("kit_cooldowns")&&next>now&&!app.permissions.has(p.getCommandSource(),"kit.bypass.cooldown"))return app.messages.error(p.getCommandSource(),"kit_wait","time",(next-now+999)/1000);
        if(now-lastClaim.getOrDefault(p.getUuid(),0L)<1000)return app.messages.error(p.getCommandSource(),"busy");
        if(!kit.rewardCommands.isEmpty()&&!cfg.kits.allowRewardCommands)return app.messages.error(p.getCommandSource(),"denied");
        for(String command:kit.rewardCommands){if(command.isBlank()||command.contains("\n")||command.contains("\r")||!cfg.kits.allowedRewardRoots.contains(command.split(" ",2)[0]))return app.messages.error(p.getCommandSource(),"denied");}
        List<ItemStack> before=new ArrayList<>();for(int i=0;i<36;i++)before.add(p.getInventory().getStack(i).copy());
        var plan=InventoryPlan.create(before,decode(p,kit));if(plan.isEmpty())return app.messages.error(p.getCommandSource(),"kit_space");
        String id=UUID.randomUUID().toString();var receipt=new Receipt(id,p.getUuid(),name,now,encode(p,before),encode(p,plan.get()),"PREPARED");
        JsonFiles.write(app.data().root().resolve("kit-receipts").resolve(id+".json"),receipt);
        app.data().audit(p.getName().getString(),"kit",name,id);
        // Fail closed across the JSON/player NBT boundary: any uncertain delivery remains blocked.
        app.data().update(p.getUuid(),d->{d.pendingKit=id;d.kitCooldowns.put(name,now+kit.cooldownSeconds*1000L);d.claimedKits.add(name);});
        lastClaim.put(p.getUuid(),now);
        try {
        for(int i=0;i<36;i++)p.getInventory().setStack(i,plan.get().get(i));
        p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();p.getServer().getPlayerManager().saveAllPlayerData();
        JsonFiles.write(app.data().root().resolve("kit-receipts").resolve(id+".json"),new Receipt(id,p.getUuid(),name,now,receipt.before,receipt.after,"SAVED"));
        app.data().update(p.getUuid(),d->d.pendingKit="");
        } catch(IOException e) {
            ShirokaiEssentials.LOGGER.error("Entrega de kit interrompida; verificar recibo " + id,e);
            return app.messages.error(p.getCommandSource(),"kit_delivery_error","id",id);
        }
        for(String command:kit.rewardCommands)p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(),command.replace("{player}",p.getName().getString()).replace("{uuid}",p.getUuidAsString()));
        return app.messages.send(p.getCommandSource(),"kit_received","name",name);
    }
    public int preview(ServerPlayerEntity p,String name)throws IOException{
        if(!access(p.getCommandSource(),name))return app.messages.error(p.getCommandSource(),"kit_missing");
        var inv=new SimpleInventory(54);var items=decode(p,app.settings().kits.kits.get(name));for(int i=0;i<items.size();i++)inv.setStack(i,items.get(i).copy());
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inventory,user)->new ReadOnlyScreen(id,inventory,inv),app.messages.text("kit_title","name",name)));return 1;
    }
    public int reset(ServerCommandSource source,UUID id,String name)throws IOException{
        var d=app.data().player(id);if(!d.pendingKit.isEmpty())return app.messages.error(source,"kit_pending","id",d.pendingKit);
        app.data().audit(source.getName(),"resetkit",id.toString(),name);app.data().update(id,p->p.kitCooldowns.remove(name));return app.messages.send(source,"ok");
    }
    public int recover(ServerCommandSource source,UUID id,String receipt)throws IOException{
        var d=app.data().player(id);if(!d.pendingKit.equals(receipt)||receipt.isEmpty())return app.messages.error(source,"missing");
        app.data().audit(source.getName(),"kitrecover",id.toString(),receipt);app.data().update(id,p->p.pendingKit="");return app.messages.send(source,"kit_recovered","id",receipt);
    }
    public void join(ServerPlayerEntity p,boolean first)throws IOException{
        if(!app.settings().module("kits")||!first)return;
        for(var e:app.settings().kits.kits.entrySet())if(e.getValue().firstJoin)claim(p,e.getKey(),true);
    }
    public void disconnect(UUID id){lastClaim.remove(id);}
}
