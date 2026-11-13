package dev.shirokai.essentials;

import dev.shirokai.essentials.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path dir;
    final UUID id = UUID.randomUUID();
    final Position position = new Position("multiworld:modserver", 10.5, 64, -40.5, 90, -10, 123);
    @Test void homesBackCooldownAndToggleSurviveRestart() throws Exception {
        var db = new DataStore(dir);
        db.update(id, p -> { p.homes.put("casa", position); p.back = position; p.requestsBlocked = true; p.nextTeleportAt = 99; });
        var restored = new DataStore(dir).player(id);
        assertEquals(position, restored.homes.get("casa")); assertEquals(position, restored.back);
        assertTrue(restored.requestsBlocked); assertEquals(99, restored.nextTeleportAt);
    }
    @Test void globalDestinationsPreserveNamespacesAndVisibility() throws Exception {
        var db = new DataStore(dir);
        db.updateWorld(w -> { w.warps.put("privado", new DataStore.Warp(position, id, false)); w.spawns.put("multiworld:spawn", position); });
        var w = new DataStore(dir).world(); assertFalse(w.warps.get("privado").isPublic);
        assertEquals(id, w.warps.get("privado").owner); assertEquals(position, w.spawns.get("multiworld:spawn"));
    }
    @Test void failedWritePreservesLiveAndStoredState() throws Exception {
        var db = new DataStore(dir); db.update(id, p -> p.homes.put("one", position));
        Path file = dir.resolve("players").resolve(id + ".json"); String previous = Files.readString(file);
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        Files.deleteIfExists(backup); Files.createDirectory(backup); Files.writeString(backup.resolve("block"), "cannot replace directory");
        assertThrows(IOException.class, () -> db.update(id, p -> p.homes.clear()));
        assertEquals(position, db.player(id).homes.get("one")); assertEquals(previous, Files.readString(file));
    }
    @Test void corruptFileIsPreserved() throws Exception {
        Files.writeString(dir.resolve("world.json"), "{broken");
        assertThrows(IOException.class, () -> new DataStore(dir)); assertEquals("{broken", Files.readString(dir.resolve("world.json")));
    }
    @Test void backupsContainPreviousSuccessfulRevision() throws Exception {
        var db = new DataStore(dir); db.update(id, p -> p.homes.put("one", position));
        Path file = dir.resolve("players").resolve(id + ".json"); String previous = Files.readString(file);
        db.update(id, p -> p.homes.put("two", position));
        assertEquals(previous, Files.readString(file.resolveSibling(file.getFileName() + ".bak")));
    }
    @Test void callersCannotMutateWithoutPersisting() throws Exception {
        var db = new DataStore(dir); db.player(id).homes.put("lost", position); assertTrue(db.player(id).homes.isEmpty());
        db.world().spawns.put("lost", position); assertTrue(db.world().spawns.isEmpty());
    }
    @Test void invalidCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Position("world",0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new Position("minecraft:overworld", Double.NaN,0,0,0,0,0));
    }
}
