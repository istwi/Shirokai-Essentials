package dev.shirokai.essentials;

import dev.shirokai.essentials.persistence.DataStore;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PaidAccessJournalTest {
    @TempDir Path dir;

    @Test void pendingPaymentSurvivesEvictionAndReload() throws Exception {
        var id=UUID.randomUUID();var store=new DataStore(dir);
        store.update(id,p->{p.pendingPaidAccess="payment-id";p.pendingPaidAccessAmount="1000";p.pendingPaidAccessCurrency="impactor:dollars";});
        store.evict(id);var loaded=store.player(id);
        assertEquals("payment-id",loaded.pendingPaidAccess);
        assertEquals("1000",loaded.pendingPaidAccessAmount);
        assertEquals("impactor:dollars",loaded.pendingPaidAccessCurrency);
    }

    @Test void administrativeClearIsPersistedAtomically() throws Exception {
        var id=UUID.randomUUID();var store=new DataStore(dir);
        store.update(id,p->{p.pendingPaidAccess="payment-id";p.pendingPaidAccessAmount="1000";p.pendingPaidAccessCurrency="impactor:dollars";});
        store.update(id,p->{p.pendingPaidAccess="";p.pendingPaidAccessAmount="";p.pendingPaidAccessCurrency="";});
        store.evict(id);assertTrue(store.player(id).pendingPaidAccess.isBlank());
    }
}
