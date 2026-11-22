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
    @Test void seasonsUseTheirMonthsAndOrdinaryEntriesRemainAvailable() {
        assertTrue(CosmeticCatalog.find("trail_halloween").available(LocalDate.of(2026,10,31)));
        assertFalse(CosmeticCatalog.find("trail_halloween").available(LocalDate.of(2026,11,1)));
        assertTrue(CosmeticCatalog.find("circle_valentine").available(LocalDate.of(2026,6,12)));
        assertTrue(CosmeticCatalog.find("trail_stars").available(LocalDate.of(2026,1,1)));
    }
    @Test void collectionsCoverVisualCategoriesWithoutChangingNickname() {
        for(String style:List.of("stars","fire","water","nature","electric","soul")) {
            var entries=CosmeticCatalog.collection(style);assertEquals(9,entries.size());assertFalse(entries.containsKey("nick"));
            entries.forEach((category,id)->assertEquals(category,CosmeticCatalog.find(id).category()));
        }
        assertThrows(IllegalArgumentException.class,()->CosmeticCatalog.collection("unknown"));
    }
    @Test void settingsRejectUnboundedWorkAndMalformedFilesWithoutOverwriting() throws Exception {
        var file=root.resolve("cosmetics.json");var cfg=CosmeticSettings.load(file);cfg.maxParticlesPerTick=0;
        assertThrows(IOException.class,cfg::validate);
        cfg.maxParticlesPerTick=400;cfg.intervalTicks=0;assertThrows(IOException.class,cfg::validate);
        cfg.intervalTicks=5;cfg.wingsIntervalTicks=3;assertThrows(IOException.class,cfg::validate);
        cfg.wingsIntervalTicks=4;cfg.trailDelayTicks=0;assertThrows(IOException.class,cfg::validate);
        cfg.trailDelayTicks=10;cfg.timezone="invalid/zone";assertThrows(IOException.class,cfg::validate);
        Files.writeString(file,"{broken");assertThrows(IOException.class,()->CosmeticSettings.load(file));assertEquals("{broken",Files.readString(file));
    }
    @Test void cosmeticChoicesPersistAndFailedSaveDoesNotPublishNewSelection() throws Exception {
        var id=UUID.randomUUID();var store=new DataStore(root);
        store.update(id,p->{p.cosmetics.put("wings","wings_stars");p.cosmeticsHidden=true;});
        var reloaded=new DataStore(root);assertEquals("wings_stars",reloaded.player(id).cosmetics.get("wings"));assertTrue(reloaded.player(id).cosmeticsHidden);
        var path=root.resolve("players").resolve(id+".json");Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("blocked"),"fixture");
        assertThrows(IOException.class,()->store.update(id,p->p.cosmetics.put("wings","wings_fire")));
        assertEquals("wings_stars",store.player(id).cosmetics.get("wings"));
    }
    @Test void legacyProfilesAcquireEmptyCosmeticChoices() throws Exception {
        var id=UUID.randomUUID();var path=root.resolve("players").resolve(id+".json");Files.createDirectories(path.getParent());Files.writeString(path,"{\"schemaVersion\":2,\"nickname\":\"Legacy\"}");
        var data=new DataStore(root).player(id);assertEquals("Legacy",data.nickname);assertTrue(data.cosmetics.isEmpty());assertFalse(data.cosmeticsHidden);
    }
}
