package dev.shirokai.essentials.permissions;

import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnderChestPermissionTest {
    private static final String OWN = "shirokai.essentials.enderchest";
    private static final String OTHERS = OWN + ".others";
    private Tristate resolve(String query, String grant, Tristate result) {
        return PermissionService.enderChestResult(query, result, grant);
    }
    @Test void ownPermissionDoesNotExpandToOtherPlayers() {
        assertEquals(Tristate.UNDEFINED, resolve(OTHERS, OWN, Tristate.TRUE));
        assertEquals(Tristate.UNDEFINED, resolve(OTHERS, "shirokai.essentials", Tristate.TRUE));
        assertEquals(Tristate.UNDEFINED, resolve(OTHERS, null, Tristate.TRUE));
    }
    @Test void explicitOthersAndAdministrativeWildcardsRemainSupported() {
        for (String grant : new String[]{OTHERS, OWN + ".*", "shirokai.essentials.*", "*"})
            assertEquals(Tristate.TRUE, resolve(OTHERS, grant, Tristate.TRUE));
    }
    @Test void explicitDenialIsNeverOverridden() {
        assertEquals(Tristate.FALSE, resolve(OTHERS, OTHERS, Tristate.FALSE));
        assertEquals(Tristate.UNDEFINED, resolve(OTHERS, null, Tristate.UNDEFINED));
    }
    @Test void ownChestAndOtherCommandsKeepExistingPermissionSemantics() {
        assertEquals(Tristate.TRUE, resolve(OWN, OWN, Tristate.TRUE));
        assertEquals(Tristate.TRUE, resolve("shirokai.essentials.heal.others", "shirokai.essentials.heal", Tristate.TRUE));
    }
}
