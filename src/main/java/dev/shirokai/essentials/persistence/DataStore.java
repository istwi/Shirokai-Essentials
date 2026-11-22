package dev.shirokai.essentials.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Copy, persist, then publish; failed writes leave the live state unchanged. Server thread only. */
public final class DataStore {
    public static class PlayerData {
        public int schemaVersion = 2;
        public Map<String, Position> homes = new TreeMap<>();
        public Position back;
        public boolean requestsBlocked = false;
        public long nextTeleportAt;
        public long nextSmeltAt;
        public Set<UUID> ignored = new HashSet<>();
        public List<Mail> mail = new ArrayList<>();
        public boolean messagesBlocked, socialSpy, fly, managedFlight, god, joined;
        public String nickname = "", pendingKit = "";
        public Map<String,String> cosmetics = new TreeMap<>();
        public boolean cosmeticsHidden = false;
        public CosmeticPreferences cosmeticPreferences = new CosmeticPreferences();
        public Map<String, Long> kitCooldowns = new TreeMap<>();
        public Set<String> claimedKits = new HashSet<>();
    }
    public static class CosmeticPreferences {
        public int speed = 2, size = 2, density = 2, direction = 1;
    }
    public record Mail(String id, UUID sender, String senderName, String content, long sentAt) {}
    public record Profile(UUID id, String name, long firstSeen, long lastSeen) {}
    public static class Warp {
        public Position position;
        public UUID owner;
        public boolean isPublic = true;
        public Warp(Position p, UUID owner, boolean isPublic) { this.position = p; this.owner = owner; this.isPublic = isPublic; }
    }
    public static class WorldData {
        public int schemaVersion = 2;
        public Map<String, Warp> warps = new TreeMap<>();
        public Map<String, Position> spawns = new TreeMap<>();
        public Map<UUID, Profile> profiles = new HashMap<>();
        public Map<String, Position> jails = new TreeMap<>();
    }
    private final Path root;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private WorldData world;
    private final Map<String,UUID> names = new HashMap<>();
    private void indexNames() { names.clear();world.profiles.forEach((id,p)->names.put(p.name().toLowerCase(Locale.ROOT),id)); }
    public DataStore(Path root) throws IOException {
        this.root = root;
        world = JsonFiles.read(root.resolve("world.json"), WorldData.class, new WorldData());
        if(world.schemaVersion==1)world.schemaVersion=2;
        validate(world); indexNames();
    }
    private static void validate(PlayerData p) throws IOException {
        if (p.schemaVersion != 2 || p.cosmetics == null || p.cosmetics.size() > 20 || p.cosmetics.entrySet().stream().anyMatch(e -> e.getKey()==null || e.getValue()==null) || p.cosmeticPreferences == null || p.cosmeticPreferences.speed<1 || p.cosmeticPreferences.speed>3 || p.cosmeticPreferences.size<1 || p.cosmeticPreferences.size>3 || p.cosmeticPreferences.density<1 || p.cosmeticPreferences.density>3 || Math.abs(p.cosmeticPreferences.direction)!=1 || p.ignored == null || p.mail == null || p.mail.size() > 1000 || p.nickname == null || p.pendingKit == null || p.kitCooldowns == null || p.claimedKits == null || p.homes == null || p.homes.size() > 10000 || p.homes.entrySet().stream().anyMatch(e -> !dev.shirokai.essentials.config.Settings.validName(e.getKey()) || e.getValue() == null)) throw new IOException("Dados de jogador inválidos; arquivo preservado");
    }
    private static void validate(WorldData w) throws IOException {
        if (w.schemaVersion != 2 || w.profiles == null || w.jails == null || w.warps == null || w.spawns == null || w.warps.entrySet().stream().anyMatch(e -> !dev.shirokai.essentials.config.Settings.validName(e.getKey()) || e.getValue() == null || e.getValue().position == null) || w.spawns.containsValue(null)) throw new IOException("Dados globais inválidos; arquivo preservado");
    }
    private PlayerData internal(UUID id) throws IOException {
        PlayerData p = players.get(id);
        if (p == null) {
            p = JsonFiles.read(root.resolve("players").resolve(id + ".json"), PlayerData.class, new PlayerData());
            if(p.schemaVersion==1)p.schemaVersion=2;
            validate(p); players.put(id, p);
        }
        return p;
    }
    private static <T> T copy(T obj, Class<T> type) { return JsonFiles.GSON.fromJson(JsonFiles.GSON.toJsonTree(obj), type); }
    /** Read-only snapshot for migration previews: never creates a destination file or cache entry. */
    public PlayerData peekPlayer(UUID id) throws IOException {
        if(players.containsKey(id))return copy(players.get(id),PlayerData.class);
        Path file=root.resolve("players").resolve(id+".json");if(!java.nio.file.Files.exists(file))return new PlayerData();
        var value=JsonFiles.read(file,PlayerData.class,new PlayerData());if(value.schemaVersion==1)value.schemaVersion=2;validate(value);return value;
    }
    public boolean loaded(UUID id) { return players.containsKey(id); }
    public PlayerData player(UUID id) throws IOException { return copy(internal(id), PlayerData.class); }
    public WorldData world() { return copy(world, WorldData.class); }
    public void update(UUID id, Consumer<PlayerData> action) throws IOException {
        PlayerData next = player(id); action.accept(next); validate(next);
        JsonFiles.write(root.resolve("players").resolve(id + ".json"), next); players.put(id, next);
    }
    public void updateWorld(Consumer<WorldData> action) throws IOException {
        WorldData next = world(); action.accept(next); validate(next);
        JsonFiles.write(root.resolve("world.json"), next); world = next; indexNames();
    }
    public Optional<Profile> profile(String value) {
        try { UUID id = UUID.fromString(value); return Optional.ofNullable(world.profiles.get(id)); } catch (IllegalArgumentException ignored) {}
        return Optional.ofNullable(world.profiles.get(names.get(value.toLowerCase(Locale.ROOT))));
    }
    public String nickname(UUID id) throws IOException { return internal(id).nickname; }
    public boolean socialSpy(UUID id) throws IOException { return internal(id).socialSpy; }
    public record Messaging(boolean blocked, Set<UUID> ignored) {}
    public Messaging messaging(UUID id) throws IOException { var d=internal(id);return new Messaging(d.messagesBlocked,Set.copyOf(d.ignored)); }
    public Position spawn(String key) { return world.spawns.get(key); }
    public Position jail(String key) { return world.jails.get(key); }
    public Warp warp(String key) { var w=world.warps.get(key);return w==null?null:new Warp(w.position,w.owner,w.isPublic); }
    public Map<String,Warp> warps() { Map<String,Warp> result=new TreeMap<>();world.warps.forEach((k,w)->result.put(k,copy(w,Warp.class)));return Collections.unmodifiableMap(result); }
    public void seen(UUID id, String name, long now) throws IOException {
        updateWorld(w -> { var old = w.profiles.get(id); w.profiles.put(id, new Profile(id, name, old == null ? now : old.firstSeen(), now)); });
    }
    public void audit(String actor, String action, String target, String detail) throws IOException {
        java.nio.file.Files.writeString(root.resolve("audit.jsonl"), JsonFiles.GSON.toJson(Map.of("time", java.time.Instant.now().toString(), "actor", actor, "action", action, "target", target, "detail", detail)).replace("\n", "") + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
    public Path root() { return root; }
    public void evict(UUID id) { players.remove(id); }
}
