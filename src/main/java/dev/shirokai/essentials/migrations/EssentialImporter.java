package dev.shirokai.essentials.migrations;

import dev.shirokai.essentials.*;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.persistence.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit adapter for the actual 0.35.2 NBT format. Originals are always read-only. */
public final class EssentialImporter {
    public record Home(UUID player,String name,Position position){}
    public record Warp(String name,Position position,String permission){}
    public record Plan(List<Home> homes,List<Warp> warps,Position spawn,List<String> issues,Map<String,String> hashes){}
    private final ShirokaiEssentials app; private final AtomicBoolean busy=new AtomicBoolean();
    public EssentialImporter(ShirokaiEssentials app){this.app=app;}
    private static NbtCompound read(Path p)throws IOException{
        if(Files.isSymbolicLink(p)||Files.size(p)>4*1024*1024)throw new IOException("Arquivo excessivo ou link simbólico: "+p.getFileName());
        var root=NbtIo.readCompressed(p,NbtSizeTracker.of(8*1024*1024));
        if(!root.contains("data",NbtElement.COMPOUND_TYPE))throw new IOException("Wrapper NBT data ausente: "+p.getFileName());return root.getCompound("data");
    }
    public static Position position(NbtCompound tag){
        if(!tag.contains("WorldRegistryKey",NbtElement.STRING_TYPE))throw new IllegalArgumentException("Dimensão ausente ou inválida");
        for(String k:List.of("x","y","z","headYaw","pitch"))if(!tag.contains(k,NbtElement.NUMBER_TYPE))throw new IllegalArgumentException("Campo numérico ausente: "+k);
        return new Position(tag.getString("WorldRegistryKey"),tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"),tag.getFloat("headYaw"),tag.getFloat("pitch"),System.currentTimeMillis());
    }
    public static Plan inspect(Path root)throws IOException{
        List<Home> homes=new ArrayList<>();List<Warp> warps=new ArrayList<>();List<String> issues=new ArrayList<>();Map<String,String> hashes=new TreeMap<>();Position spawn=null;
        if(Files.isSymbolicLink(root.resolve("modplayerdata"))||Files.isSymbolicLink(root.resolve("essentialcommands")))throw new IOException("Diretório de origem simbólico");
        long totalBytes=0;
        Path world=root.resolve("essentialcommands/world_data.dat");
        if(Files.isRegularFile(world)){
            totalBytes+=Files.size(world);
            var nbt=read(world);hashes.put(root.relativize(world).toString(),hash(world));
            if(nbt.contains("spawn",NbtElement.COMPOUND_TYPE)&&!nbt.getCompound("spawn").isEmpty())try{spawn=position(nbt.getCompound("spawn"));}catch(IllegalArgumentException e){issues.add("spawn: "+e.getMessage());}
            if(nbt.contains("warps",NbtElement.COMPOUND_TYPE))for(String name:nbt.getCompound("warps").getKeys()){
                try{var tag=nbt.getCompound("warps").getCompound(name);warps.add(new Warp(normalize(name),position(tag),tag.getString("permissionString")));}catch(IllegalArgumentException e){issues.add("warp "+name+": "+e.getMessage());}
            }
        }
        Path players=root.resolve("modplayerdata");
        if(Files.isSymbolicLink(players)||Files.isSymbolicLink(root.resolve("essentialcommands")))throw new IOException("Diretório de origem simbólico");
        if(Files.isDirectory(players))try(var files=Files.list(players)){
            var list=files.filter(p->p.getFileName().toString().endsWith(".dat")).sorted().limit(1001).toList();
            if(list.size()>1000)throw new IOException("Mais de 1000 jogadores; divida a cópia de migração em lotes");
            for(Path file:list){
                String filename=file.getFileName().toString();UUID id;
                try{id=UUID.fromString(filename.substring(0,filename.length()-4));}catch(IllegalArgumentException e){issues.add("Nome de arquivo inválido: "+filename);continue;}
                totalBytes+=Files.size(file);if(totalBytes>64L*1024*1024)throw new IOException("Lote de origem excede 64 MiB; divida a migração");
                var nbt=read(file);hashes.put(root.relativize(file).toString(),hash(file));
                if(!nbt.containsUuid("playerUuid")||!id.equals(nbt.getUuid("playerUuid")))throw new IOException("UUID interno divergente: "+filename);
                if(nbt.contains("homes",NbtElement.COMPOUND_TYPE))for(String name:nbt.getCompound("homes").getKeys()){
                    try{homes.add(new Home(id,normalize(name),position(nbt.getCompound("homes").getCompound(name))));if(homes.size()+warps.size()+issues.size()>10000)throw new IOException("Mais de 10000 destinos/erros no lote");}catch(IllegalArgumentException e){issues.add(filename+" home "+name+": "+e.getMessage());}
                }else if(nbt.contains("homes"))issues.add(filename+": homes em formato legado não suportado; preserve e atualize com o mod original");
            }
        }
        if(hashes.isEmpty())throw new IOException("Não foram encontrados essentialcommands/world_data.dat ou modplayerdata/*.dat");
        return new Plan(homes,warps,spawn,issues,hashes);
    }
    private static String normalize(String name){String result=name.toLowerCase(Locale.ROOT);if(!Settings.validName(result))throw new IllegalArgumentException("nome incompatível");return result;}
    private static String hash(Path p)throws IOException{try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public int run(ServerCommandSource source,String relative,boolean apply){
        if(!app.settings().integrations.essentialCommandsVersion.equals("0.35.2"))return app.messages.error(source,"import_blocked","reason","defina integrations.json: essentialCommandsVersion=0.35.2 após identificar a versão dos dados");
        if(apply&&(source.isExecutedByPlayer()||source.getServer().getCurrentPlayerCount()!=0||FabricLoader.getInstance().isModLoaded("essential_commands")))return app.messages.error(source,"import_blocked","reason","apply exige console, servidor vazio e Essential Commands inativo");
        if(!busy.compareAndSet(false,true))return app.messages.error(source,"busy");
        Path directory;
        try{
            Path world=source.getServer().getSavePath(WorldSavePath.ROOT).toRealPath();directory=world.resolve(relative).normalize().toRealPath();
            if(!directory.startsWith(world)||directory.startsWith(app.data().root().toRealPath()))throw new IOException("Use uma cópia dentro da pasta do mundo, fora dos dados Shirokai");
        }catch(IOException|InvalidPathException e){busy.set(false);return app.messages.error(source,"import_blocked","reason",e.getMessage());}
        final Path base=directory;
        CompletableFuture.supplyAsync(()->{try{return inspect(base);}catch(IOException e){throw new java.util.concurrent.CompletionException(e);}}).whenComplete((plan,error)->source.getServer().execute(()->{
            try{
                if(error!=null){app.messages.error(source,"import_blocked","reason",error.getCause()==null?error.getMessage():error.getCause().getMessage());return;}
                if(apply&&(source.getServer().getCurrentPlayerCount()!=0||!app.permissions.has(source,"ecimport.apply"))){app.messages.error(source,"import_blocked","reason","condições de apply mudaram; tente novamente com servidor vazio");return;}
                perform(source,base,plan,apply);
            }catch(IOException|RuntimeException e){ShirokaiEssentials.LOGGER.error("Migração não concluída; consulte journal e backups",e);app.messages.error(source,"import_blocked","reason","falha; consulte o log, journal e backups. Pode haver progresso parcial");}
            finally{busy.set(false);}
        }));return 1;
    }
    private void perform(ServerCommandSource source,Path base,Plan plan,boolean apply)throws IOException{
        String id=UUID.randomUUID().toString();Path report=app.configDir.resolve("migration-"+id+".json");
        List<String> issues=new ArrayList<>(plan.issues),applied=new ArrayList<>(),candidates=new ArrayList<>();
        Map<UUID,DataStore.PlayerData> before=new LinkedHashMap<>();var oldWorld=app.data().world();
        Map<UUID,Map<String,Position>> homes=new LinkedHashMap<>();Map<String,DataStore.Warp> warps=new TreeMap<>();Map<String,Position> spawns=new TreeMap<>();
        Set<String> seen=new HashSet<>();
        for(var h:plan.homes){
            if(!before.containsKey(h.player))before.put(h.player,app.data().peekPlayer(h.player));
            String key=h.player+"/"+h.name;
            if(!seen.add(key)||before.get(h.player).homes.containsKey(h.name)){issues.add("home duplicada, preservada: "+key);continue;}
            if(app.teleports.world(source.getServer(),h.position)==null){issues.add("dimensão ausente: "+key);continue;}
            homes.computeIfAbsent(h.player,k->new TreeMap<>()).put(h.name,h.position);candidates.add("home "+key);
        }
        for(var w:plan.warps){
            if(!w.permission.isBlank()){issues.add("warp com permissão antiga, não importada sem mapeamento: "+w.name);continue;}
            if(warps.containsKey(w.name)||oldWorld.warps.containsKey(w.name)){issues.add("warp duplicada, preservada: "+w.name);continue;}
            if(app.teleports.world(source.getServer(),w.position)==null){issues.add("dimensão ausente: "+w.name);continue;}
            warps.put(w.name,new DataStore.Warp(w.position,null,true));candidates.add("warp "+w.name);
        }
        if(plan.spawn!=null){String key=app.settings().spawn.perWorld?plan.spawn.dimension():"global";
            if(oldWorld.spawns.containsKey(key))issues.add("spawn existente preservado: "+key);
            else if(app.teleports.world(source.getServer(),plan.spawn)==null)issues.add("spawn com dimensão ausente");
            else{spawns.put(key,plan.spawn);candidates.add("spawn "+key);}
        }
        Map<String,Object> journal=new LinkedHashMap<>();journal.put("id",id);journal.put("mode",apply?"apply":"dry-run");journal.put("format","Essential Commands 0.35.2 / NBT");journal.put("sourceHashes",plan.hashes);journal.put("issues",issues);journal.put("candidates",candidates);journal.put("applied",applied);journal.put("state",apply?"PREPARED":"DRY_RUN");
        JsonFiles.write(report,journal);
        if(apply){
            Path backup=app.data().root().getParent().resolve("shirokai-migration-backups").resolve(id);
            JsonFiles.write(backup.resolve("destination-before.json"),Map.of("world",oldWorld,"players",before));
            for(var entry:plan.hashes.entrySet()){
                Path input=base.resolve(entry.getKey());if(!hash(input).equals(entry.getValue()))throw new IOException("Origem mudou após leitura: "+entry.getKey());
                Path out=backup.resolve("source").resolve(entry.getKey());Files.createDirectories(out.getParent());Files.copy(input,out);
            }
            app.data().audit(source.getName(),"ecimport",base.toString(),id);
            try{
                // One commit per player instead of rewriting a growing home file for every home.
                for(var entry:homes.entrySet()){
                    app.data().update(entry.getKey(),player->player.homes.putAll(entry.getValue()));
                    entry.getValue().keySet().forEach(name->applied.add("home "+entry.getKey()+"/"+name));JsonFiles.write(report,journal);
                }
                if(!warps.isEmpty()||!spawns.isEmpty()){
                    app.data().updateWorld(w->{w.warps.putAll(warps);w.spawns.putAll(spawns);});
                    warps.keySet().forEach(name->applied.add("warp "+name));spawns.keySet().forEach(name->applied.add("spawn "+name));
                }
                journal.put("state","APPLIED_WITH_REPORT");
            }catch(IOException|RuntimeException error){journal.put("state","PARTIAL_REVIEW_REQUIRED");journal.put("error",error.toString());JsonFiles.write(report,journal);throw error;}
            JsonFiles.write(report,journal);
        }
        app.messages.send(source,"import_report","mode",apply?"apply":"dry-run","path",report);
    }
}
