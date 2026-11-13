package dev.shirokai.essentials;
import dev.shirokai.essentials.moderation.Durations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DurationsTest {
    @Test void acceptsExplicitUnits(){assertEquals(30000,Durations.millis("30s"));assertEquals(600000,Durations.millis("10m"));assertEquals(7200000,Durations.millis("2h"));assertEquals(604800000,Durations.millis("7d"));}
    @Test void rejectsNegativeZeroMissingUnitAndOverflow(){for(String s:new String[]{"0s","-1s","30","9999999999999999999999d","366d","1ms","1.5h","1d command"})assertThrows(IllegalArgumentException.class,()->Durations.millis(s),s);}
}
