package dev.shirokai.essentials.moderation;

import dev.shirokai.essentials.persistence.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class Punishments {
    public record Warning(String id, String actor, String reason, long at) {}
    public static class Entry {
        public long muteUntil, jailUntil;
        public String muteReason="", jail="";
        public Position returnPosition, freeze;
        public List<Warning> warnings=new ArrayList<>();
        public boolean muted(long now) { return muteUntil>now; }
        public boolean jailed(long now) { return !jail.isEmpty() && jailUntil>now; }
    }
    public static class State { public int schemaVersion=1; public Map<UUID,Entry> players=new HashMap<>(); }
    private State state; private final Path file;
    private final Set<UUID> active=new HashSet<>(), frozen=new HashSet<>(), jailed=new HashSet<>();
    private void rebuildActive(){active.clear();frozen.clear();jailed.clear();state.players.forEach((id,e)->{if(e.freeze!=null)frozen.add(id);if(!e.jail.isEmpty())jailed.add(id);if(e.freeze!=null||!e.jail.isEmpty())active.add(id);});}
    public record Status(long muteUntil,String muteReason,String jail,long jailUntil,Position returnPosition,Position freeze){
        public boolean muted(long now){return muteUntil>now;}public boolean jailed(long now){return !jail.isEmpty()&&jailUntil>now;}
    }
    public Status status(UUID id){var e=state.players.get(id);return e==null?new Status(0,"","",0,null,null):new Status(e.muteUntil,e.muteReason,e.jail,e.jailUntil,e.returnPosition,e.freeze);}
    public Punishments(Path file) throws IOException {
        this.file=file; state=JsonFiles.read(file,State.class,new State()); validate(state);rebuildActive();
    }
    private void validate(State s) throws IOException {
        if(s.schemaVersion!=1 || s.players==null || s.players.values().stream().anyMatch(e->e==null||e.jail==null||e.muteReason==null||e.warnings==null||e.warnings.size()>1000)) throw new IOException("Punições inválidas; arquivo preservado");
    }
    public Entry get(UUID id) { return JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(state.players.getOrDefault(id,new Entry())),Entry.class); }
    public void update(UUID id, Consumer<Entry> action) throws IOException {
        State next=JsonFiles.GSON.fromJson(JsonFiles.GSON.toJson(state),State.class);
        action.accept(next.players.computeIfAbsent(id,k->new Entry())); validate(next); JsonFiles.write(file,next); state=next;rebuildActive();
    }
    public Set<UUID> active(boolean includeFreeze,boolean includeJail) {Set<UUID> result=new HashSet<>();if(includeFreeze)result.addAll(frozen);if(includeJail)result.addAll(jailed);return result;}
    public Set<UUID> active() { return Set.copyOf(active); }
}
