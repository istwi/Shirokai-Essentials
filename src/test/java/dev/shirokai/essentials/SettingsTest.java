package dev.shirokai.essentials;

import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.persistence.JsonFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir Path dir;
    @Test void createsAllConfigurationFiles() throws Exception {
        var s = Settings.load(dir);
        assertTrue(s.command("home")); assertFalse(s.module("economy"));
        assertFalse(s.feature("motd_auto")); assertFalse(s.feature("afk_auto"));
        try (var files = Files.list(dir)) { assertEquals(15, files.count()); }
    }
    @Test void disabledModuleDisablesAllItsCommands() throws Exception {
        var s = Settings.load(dir); s.modules.modules.put("homes", false);
        JsonFiles.write(dir.resolve("modules.json"), s.modules);
        s = Settings.load(dir);
        for (String c : new String[]{"home","homes","sethome","delhome","renamehome"}) assertFalse(s.command(c));
        assertTrue(s.command("spawn"));
    }
    @Test void rejectsUnsupportedSchemaAndDoesNotOverwrite() throws Exception {
        Settings.load(dir); Path file = dir.resolve("teleport.json");
        Files.writeString(file, "{\"schemaVersion\":99}");
        assertThrows(IOException.class, () -> Settings.load(dir));
        assertEquals("{\"schemaVersion\":99}", Files.readString(file));
    }
    @Test void rejectsUnsupportedModule() throws Exception {
        var s = Settings.load(dir); s.modules.modules.put("unknown-module", true);
        JsonFiles.write(dir.resolve("modules.json"), s.modules);
        assertThrows(IOException.class, () -> Settings.load(dir));
    }
    @Test void rejectsBadLimitsAndAliases() throws Exception {
        var s = Settings.load(dir); s.teleport.rtpAttempts = Integer.MAX_VALUE;
        JsonFiles.write(dir.resolve("teleport.json"), s.teleport);
        assertThrows(IOException.class, () -> Settings.load(dir));
        s.teleport.rtpAttempts = 5; JsonFiles.write(dir.resolve("teleport.json"), s.teleport);
        s.commands.commands.get("home").aliases.add("invalid alias");
        JsonFiles.write(dir.resolve("commands.json"), s.commands);
        assertThrows(IOException.class, () -> Settings.load(dir));
    }
    @Test void reloadKeepsStartupTopologyButUpdatesSafeValues() throws Exception {
        var original = Settings.load(dir); var candidate = Settings.load(dir);
        candidate.modules.modules.put("homes", false); candidate.commands.commands.get("home").enabled = false;
        candidate.teleport.cooldownSeconds = 30;candidate.integrations.coreBridge=false;
        var reloaded = original.dynamicFrom(candidate);
        assertTrue(reloaded.command("home")); assertEquals(30, reloaded.teleport.cooldownSeconds);assertTrue(reloaded.integrations.coreBridge);
    }
    @Test void upgradingNeverRestoresOmittedOldPermissionsCommandsOrFeatures() throws Exception {
        var s=Settings.load(dir);s.permissions.defaults.remove("home");s.commands.commands.remove("home");s.modules.features.remove("home_limits");s.modules.modules.remove("homes");
        JsonFiles.write(dir.resolve("permissions.json"),s.permissions);JsonFiles.write(dir.resolve("commands.json"),s.commands);JsonFiles.write(dir.resolve("modules.json"),s.modules);
        s=Settings.load(dir);assertFalse(s.permissions.defaults.containsKey("home"));assertFalse(s.command("home"));assertFalse(s.feature("home_limits"));assertFalse(s.module("homes"));
    }
    @Test void newModulesRemainOptInAndEconomyCommandsAreAbsent() throws Exception {
        var s=Settings.load(dir);for(String module:java.util.List.of("chat","moderation","utilities","administration","kits"))assertFalse(s.module(module));
        assertFalse(s.modules.modules.containsKey("economy"));
        assertFalse(Files.exists(dir.resolve("economy.json")));
        for(String command:java.util.List.of("balance","bal","money","pay","baltop","eco")) {
            assertFalse(s.command(command));assertFalse(Settings.COMMAND_MODULE.containsKey(command));
            assertFalse(s.commands.commands.containsKey(command));assertFalse(s.permissions.defaults.containsKey(command));
        }
    }
    @Test void oldEconomySettingsCannotRestoreCommandsOrPreventFreeServices() throws Exception {
        var s=Settings.load(dir);
        s.modules.modules.put("economy",true);s.modules.modules.put("kits",true);
        s.teleport.cooldownSeconds=37;
        for(String command:java.util.List.of("balance","bal","money","pay","baltop","eco")) {
            var legacy=new Settings.Command();legacy.aliases.add("s"+command);
            s.commands.commands.put(command,legacy);
            s.permissions.defaults.put(command,true);
            s.conflicts.commands.put(command,Settings.Policy.FAIL_STARTUP);
        }
        JsonFiles.write(dir.resolve("modules.json"),s.modules);
        JsonFiles.write(dir.resolve("commands.json"),s.commands);
        JsonFiles.write(dir.resolve("permissions.json"),s.permissions);
        JsonFiles.write(dir.resolve("conflicts.json"),s.conflicts);
        JsonFiles.write(dir.resolve("teleport.json"),s.teleport);
        Files.writeString(dir.resolve("economy.json"),"obsolete invalid JSON");
        String originalCommands=Files.readString(dir.resolve("commands.json"));
        var loaded=Settings.load(dir);
        assertFalse(loaded.module("economy"));assertTrue(loaded.command("home"));assertTrue(loaded.command("kit"));
        assertEquals(37,loaded.teleport.cooldownSeconds);
        for(String command:java.util.List.of("balance","bal","money","pay","baltop","eco")) {
            assertFalse(loaded.command(command));assertFalse(loaded.commands.commands.containsKey(command));
            assertFalse(loaded.permissions.defaults.containsKey(command));assertFalse(loaded.conflicts.commands.containsKey(command));
        }
        assertEquals("obsolete invalid JSON",Files.readString(dir.resolve("economy.json")));
        assertEquals(originalCommands,Files.readString(dir.resolve("commands.json")));
    }
    @Test void moderationFunctionsHaveIndependentGates()throws Exception{
        var s=Settings.load(dir);s.modules.modules.put("moderation",true);
        for(String feature:java.util.List.of("moderation_chat","moderation_freeze","moderation_jail","moderation_warnings"))s.modules.features.put(feature,false);
        for(String command:java.util.List.of("mute","tempmute","unmute","freeze","setjail","jail","unjail","warn","warnings","clearwarnings"))assertFalse(s.command(command),command);
        assertTrue(s.command("kick"));
    }
    @Test void motdAndAfkAutomationDoNotDependOnCommands() throws Exception {
        var s=Settings.load(dir);s.modules.modules.put("chat",true);s.modules.features.put("motd_auto",true);s.modules.features.put("afk_auto",true);
        s.commands.commands.get("motd").enabled=false;s.commands.commands.get("afk").enabled=false;
        assertTrue(s.feature("motd_auto"));assertTrue(s.feature("afk_auto"));assertFalse(s.command("motd"));assertFalse(s.command("afk"));
    }
    @Test void privilegeAndOptionalFeatureGatesCannotBeBypassedByCommands() throws Exception {
        var s=Settings.load(dir);s.modules.modules.put("utilities",true);s.modules.modules.put("administration",true);s.modules.modules.put("moderation",true);
        assertFalse(s.command("god"));assertFalse(s.command("sudo"));assertFalse(s.command("tempban"));
        s.modules.features.put("god_mode",true);assertTrue(s.command("god"));
    }
    @Test void featureAndCommandAreIndependent() throws Exception {
        var s = Settings.load(dir); s.modules.features.put("first_join_spawn", true);
        s.commands.commands.get("spawn").enabled = false;
        assertTrue(s.feature("first_join_spawn")); assertFalse(s.command("spawn"));
    }
}
