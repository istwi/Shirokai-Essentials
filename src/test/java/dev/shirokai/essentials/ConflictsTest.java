package dev.shirokai.essentials;

import com.mojang.brigadier.CommandDispatcher;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.conflicts.ConflictService;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static net.minecraft.server.command.CommandManager.literal;
import static org.junit.jupiter.api.Assertions.*;

class ConflictsTest {
    @TempDir Path dir;
    @Test void preservesExistingCommandAndDoesNotRegisterBypassAlias() throws Exception {
        var s = Settings.load(dir); s.commands.commands.get("home").aliases.add("casa");
        var d = new CommandDispatcher<ServerCommandSource>(); var original = d.register(literal("home").executes(c -> 7));
        var conflicts = new ConflictService(); var plan = conflicts.plan(s,d);
        assertTrue(plan.get("home").isEmpty()); assertSame(original,d.getRoot().getChild("home"));
    }
    @Test void alternativePolicyUsesOnlyExplicitAvailableAlias() throws Exception {
        var s = Settings.load(dir); s.commands.commands.get("home").aliases.addAll(java.util.List.of("casa","ocupado"));
        s.conflicts.commands.put("home",Settings.Policy.USE_ALTERNATIVE_ALIAS);
        var d = new CommandDispatcher<ServerCommandSource>(); d.register(literal("home")); d.register(literal("ocupado"));
        assertEquals(java.util.List.of("casa"),new ConflictService().plan(s,d).get("home"));
    }
    @Test void disabledAliasAndCommandNeverEnterPlan() throws Exception {
        var s = Settings.load(dir); var c = s.commands.commands.get("home"); c.aliases.add("casa"); c.aliasEnabled.put("casa",false);
        var d = new CommandDispatcher<ServerCommandSource>();
        assertEquals(java.util.List.of("home"),new ConflictService().plan(s,d).get("home"));
        c.enabled = false; assertFalse(new ConflictService().plan(s,d).containsKey("home"));
    }
    @Test void failModuleAndFailStartupAreAppliedBeforeRegistration() throws Exception {
        var s = Settings.load(dir); s.conflicts.commands.put("home",Settings.Policy.FAIL_MODULE);
        var d = new CommandDispatcher<ServerCommandSource>(); d.register(literal("home"));
        var conflicts = new ConflictService(); var plan = conflicts.plan(s,d);
        assertTrue(conflicts.moduleFailed("homes")); assertTrue(plan.get("sethome").isEmpty());
        s.modules.modules.put("homes", true); s.conflicts.commands.put("home",Settings.Policy.FAIL_STARTUP);
        assertThrows(IllegalStateException.class, () -> new ConflictService().plan(s,d));
    }
    @Test void laterMergedSubcommandsAreReportedWithoutRemovingThem() throws Exception {
        var s = Settings.load(dir); var d = new CommandDispatcher<ServerCommandSource>();
        var conflicts = new ConflictService(); var own = d.register(literal("home").executes(c -> 1)); conflicts.registered("home",own);
        d.register(literal("home").then(literal("external").executes(c -> 2)));
        conflicts.audit(s,d);
        assertEquals(1,conflicts.entries().size()); assertNotNull(d.getRoot().getChild("home").getChild("external"));
    }
}
