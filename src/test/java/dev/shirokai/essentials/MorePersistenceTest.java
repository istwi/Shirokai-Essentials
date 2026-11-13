package dev.shirokai.essentials;

import dev.shirokai.essentials.persistence.*;
import dev.shirokai.essentials.moderation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MorePersistenceTest {
    @TempDir Path dir;
    @Test void mailIgnoreNickModesAndKitReceiptSurviveRestart()throws Exception {
        UUID id=UUID.randomUUID(),other=UUID.randomUUID();var db=new DataStore(dir);
        db.update(id,d->{d.ignored.add(other);d.mail.add(new DataStore.Mail("id",other,"Other","hello",1));d.nickname="apelido";d.fly=true;d.god=true;d.pendingKit="receipt";d.kitCooldowns.put("starter",42L);d.claimedKits.add("starter");});
        var restored=new DataStore(dir).player(id);assertTrue(restored.ignored.contains(other));assertEquals("hello",restored.mail.getFirst().content());assertEquals("apelido",restored.nickname);assertTrue(restored.fly&&restored.god);assertEquals("receipt",restored.pendingKit);assertEquals(42,restored.kitCooldowns.get("starter"));assertTrue(restored.claimedKits.contains("starter"));
    }
    @Test void schemaOneUpgradesWithoutLosingHomesAndKeepsBackup()throws Exception{
        UUID id=UUID.randomUUID();Files.createDirectories(dir.resolve("players"));Path file=dir.resolve("players").resolve(id+".json");
        String old="{\"schemaVersion\":1,\"homes\":{\"casa\":{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2,\"yaw\":0,\"pitch\":0,\"savedAt\":1}}}";
        Files.writeString(file,old);Files.writeString(dir.resolve("world.json"),"{\"schemaVersion\":1,\"warps\":{},\"spawns\":{}}");
        var db=new DataStore(dir);assertEquals(2,db.player(id).schemaVersion);assertTrue(db.player(id).homes.containsKey("casa"));
        db.update(id,p->p.nickname="Novo");assertEquals(old,Files.readString(file.resolveSibling(file.getFileName()+".bak")));assertEquals(2,new DataStore(dir).player(id).schemaVersion);
    }
    @Test void unsupportedFutureDataSchemaIsNeverRewritten()throws Exception{
        Path file=dir.resolve("world.json");String text="{\"schemaVersion\":99,\"warps\":{},\"spawns\":{}}";Files.writeString(file,text);assertThrows(java.io.IOException.class,()->new DataStore(dir));assertEquals(text,Files.readString(file));
    }
    @Test void migrationPreviewNeverCreatesMissingPlayerFiles()throws Exception{
        var db=new DataStore(dir);UUID unknown=UUID.randomUUID();assertTrue(db.peekPlayer(unknown).homes.isEmpty());assertFalse(Files.exists(dir.resolve("players").resolve(unknown+".json")));
    }
    @Test void knownPlayerDirectoryKeepsOriginalFirstSeenAndUpdatesName()throws Exception{
        UUID id=UUID.randomUUID();var db=new DataStore(dir);db.seen(id,"Before",10);db.seen(id,"After",20);
        var p=new DataStore(dir).profile("after").orElseThrow();assertEquals(id,p.id());assertEquals(10,p.firstSeen());assertEquals(20,p.lastSeen());assertTrue(db.profile("before").isEmpty());
    }
    @Test void muteJailFreezeAndWarningsAreDurableAndExpiryIsExact()throws Exception{
        UUID id=UUID.randomUUID();var file=dir.resolve("moderation.json");var state=new Punishments(file);
        var pos=new Position("custom:jail",1,64,2,0,0,1);
        state.update(id,e->{e.muteUntil=100;e.muteReason="reason";e.jail="cell";e.jailUntil=100;e.returnPosition=pos;e.freeze=pos;e.warnings.add(new Punishments.Warning("warn","admin","reason",1));});
        var p=new Punishments(file).get(id);assertTrue(p.muted(99));assertFalse(p.muted(100));assertTrue(p.jailed(99));assertFalse(p.jailed(100));assertEquals(pos,p.freeze);assertEquals(1,p.warnings.size());
    }
    @Test void warningAndMuteOnlyRecordsDoNotCreateTickWork()throws Exception{
        var state=new Punishments(dir.resolve("moderation.json"));UUID id=UUID.randomUUID();state.update(id,e->e.muteUntil=100);assertTrue(state.active().isEmpty());
        state.update(id,e->e.jail="cell");assertTrue(state.active().contains(id));
    }
    @Test void auditIsValidJsonPerLineEvenWithNewlinesInInput()throws Exception{
        var db=new DataStore(dir);db.audit("admin","action","uuid","reason\nnext");
        var lines=Files.readAllLines(dir.resolve("audit.jsonl"));assertEquals(1,lines.size());assertEquals("reason\nnext",com.google.gson.JsonParser.parseString(lines.getFirst()).getAsJsonObject().get("detail").getAsString());
    }
    @Test void invalidModerationDataIsNotOverwritten()throws Exception{
        Path file=dir.resolve("mod.json");Files.writeString(file,"{bad");assertThrows(java.io.IOException.class,()->new Punishments(file));assertEquals("{bad",Files.readString(file));
    }
}
