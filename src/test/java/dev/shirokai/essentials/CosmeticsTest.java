package dev.shirokai.essentials;

import dev.shirokai.essentials.cosmetics.*;
import dev.shirokai.essentials.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticsTest {
    @TempDir Path root;
    @Test void catalogueHasNoAmbiguousIdentifiersAndEveryCategoryHasOptions() {
        assertEquals(CosmeticCatalog.ENTRIES.size(),CosmeticCatalog.ENTRIES.stream().map(CosmeticCatalog.Entry::id).distinct().count());
        for(var category:CosmeticCatalog.ORDER)assertTrue(CosmeticCatalog.ENTRIES.stream().anyMatch(e->e.category().equals(category)));
        assertNull(CosmeticCatalog.find(null));
    }
    @Test void catalogueContainsOnlyTheSixSimpleCategories() {
        assertEquals(List.of("nick","trail","aura","join","victory","raid"),CosmeticCatalog.ORDER);
        assertEquals(63,CosmeticCatalog.ENTRIES.size());
        for(String added:List.of("trail_snow","trail_heart","trail_magic","trail_enchant","trail_cloud","trail_cherry"))assertNotNull(CosmeticCatalog.find(added));
        for(String removed:List.of("wings_stars","crown_fire","circle_water","footprint_nature","teleport_soul","trail_halloween"))
            assertNull(CosmeticCatalog.find(removed));
    }
    @Test void settingsRejectUnboundedWorkAndMalformedFilesWithoutOverwriting() throws Exception {
        var file=root.resolve("cosmetics.json");var cfg=CosmeticSettings.load(file);cfg.maxParticlesPerTick=0;
        assertThrows(IOException.class,cfg::validate);
        cfg.maxParticlesPerTick=400;cfg.intervalTicks=0;assertThrows(IOException.class,cfg::validate);
        cfg.intervalTicks=5;cfg.trailIntervalTicks=1;assertThrows(IOException.class,cfg::validate);
        cfg.trailIntervalTicks=6;cfg.disabled.add("wings_stars");cfg.validate();assertTrue(cfg.disabled.isEmpty());
        Files.writeString(file,"{broken");assertThrows(IOException.class,()->CosmeticSettings.load(file));assertEquals("{broken",Files.readString(file));
    }
    @Test void cosmeticChoicesPersistAndFailedSaveDoesNotPublishNewSelection() throws Exception {
        var id=UUID.randomUUID();var store=new DataStore(root);
        store.update(id,p->{p.cosmetics.put("trail","trail_stars");p.cosmeticsHidden=true;});
        var reloaded=new DataStore(root);assertEquals("trail_stars",reloaded.player(id).cosmetics.get("trail"));assertTrue(reloaded.player(id).cosmeticsHidden);
        var path=root.resolve("players").resolve(id+".json");Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("blocked"),"fixture");
        assertThrows(IOException.class,()->store.update(id,p->p.cosmetics.put("trail","trail_fire")));
        assertEquals("trail_stars",store.player(id).cosmetics.get("trail"));
    }
    @Test void cosmeticPersonalizationPersistsWithinSafeBounds() throws Exception {
        var id=UUID.randomUUID();var store=new DataStore(root);
        store.update(id,p->{p.cosmeticPreferences.speed=3;p.cosmeticPreferences.size=1;p.cosmeticPreferences.density=3;p.cosmeticPreferences.direction=-1;});
        var saved=new DataStore(root).player(id).cosmeticPreferences;
        assertEquals(3,saved.speed);assertEquals(1,saved.size);assertEquals(3,saved.density);assertEquals(-1,saved.direction);
        assertThrows(IOException.class,()->store.update(id,p->p.cosmeticPreferences.density=4));
    }
    @Test void removedChoicesRemainReadableButAreInactive() throws Exception {
        var id=UUID.randomUUID();var store=new DataStore(root);
        store.update(id,p->{p.cosmetics.put("wings","wings_stars");p.cosmetics.put("trail","trail_water");});
        var reloaded=new DataStore(root).player(id);
        assertEquals("wings_stars",reloaded.cosmetics.get("wings"));
        assertNull(CosmeticCatalog.find(reloaded.cosmetics.get("wings")));
        assertNotNull(CosmeticCatalog.find(reloaded.cosmetics.get("trail")));
    }
    @Test void legacyProfilesAcquireEmptyCosmeticChoices() throws Exception {
        var id=UUID.randomUUID();var path=root.resolve("players").resolve(id+".json");Files.createDirectories(path.getParent());Files.writeString(path,"{\"schemaVersion\":2,\"nickname\":\"Legacy\"}");
        var data=new DataStore(root).player(id);assertEquals("Legacy",data.nickname);assertTrue(data.cosmetics.isEmpty());assertFalse(data.cosmeticsHidden);assertEquals(2,data.cosmeticPreferences.density);
    }
}
