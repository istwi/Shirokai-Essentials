package dev.shirokai.essentials.conflicts;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import dev.shirokai.essentials.ShirokaiEssentials;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.persistence.JsonFiles;
import net.minecraft.server.command.ServerCommandSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class ConflictService {
    public record Conflict(String command, String root, String phase, String policy, String detail) {}
    private final List<Conflict> entries = new ArrayList<>();
    private final Map<String, Map<String, Object>> snapshots = new LinkedHashMap<>();
    private final Set<String> failedModules = new HashSet<>();
    public Map<String, List<String>> plan(Settings cfg, CommandDispatcher<ServerCommandSource> dispatcher) {
        entries.clear(); snapshots.clear(); failedModules.clear();
        Map<String, List<String>> plan = new LinkedHashMap<>();
        Set<String> occupied = new HashSet<>();
        dispatcher.getRoot().getChildren().forEach(n -> occupied.add(n.getName()));
        for (String command : Settings.COMMAND_MODULE.keySet()) {
            if (!cfg.command(command)) continue;
            var c = cfg.commands.commands.get(command);
            List<String> candidates = new ArrayList<>(); candidates.add(command);
            for (String alias : c.aliases) if (c.aliasEnabled.getOrDefault(alias, true) && !candidates.contains(alias)) candidates.add(alias);
            var policy = cfg.conflicts.commands.getOrDefault(command, cfg.conflicts.defaultPolicy);
            boolean rootConflict = occupied.contains(command);
            if (rootConflict) {
                add(cfg, new Conflict(command, command, "pre-registration", policy.name(), "Raiz existente preservada"));
                if (policy == Settings.Policy.FAIL_STARTUP) throw new IllegalStateException("Colisão crítica: /" + command);
                if (policy == Settings.Policy.FAIL_MODULE) failedModules.add(Settings.COMMAND_MODULE.get(command));
                if (policy != Settings.Policy.USE_ALTERNATIVE_ALIAS) { plan.put(command, List.of()); continue; }
            }
            List<String> selected = new ArrayList<>();
            for (String root : candidates) {
                if (occupied.contains(root)) {
                    if (!root.equals(command)) {
                        add(cfg, new Conflict(command, root, "pre-registration", policy.name(), "Alias existente preservado"));
                        if (policy == Settings.Policy.FAIL_STARTUP) throw new IllegalStateException("Colisão crítica: /" + root);
                        if (policy == Settings.Policy.FAIL_MODULE) failedModules.add(Settings.COMMAND_MODULE.get(command));
                    }
                    continue;
                }
                // Reserve every configured primary root, even if its command appears later.
                if (!root.equals(command) && Settings.COMMAND_MODULE.containsKey(root)) {
                    add(cfg, new Conflict(command, root, "configuration", policy.name(), "Alias coincide com raiz própria reservada")); continue;
                }
                occupied.add(root); selected.add(root);
            }
            plan.put(command, selected);
        }
        failedModules.forEach(module -> cfg.modules.modules.put(module, false));
        plan.replaceAll((cmd, roots) -> failedModules.contains(Settings.COMMAND_MODULE.get(cmd)) ? List.of() : roots);
        return plan;
    }
    private void add(Settings cfg, Conflict c) {
        entries.add(c);
        if (cfg.conflicts.logConflicts) ShirokaiEssentials.LOGGER.warn("Conflito /{}: {} ({})", c.root(), c.detail(), c.phase());
    }
    public boolean moduleFailed(String module) { return failedModules.contains(module); }
    private static Map<String, Object> snapshot(CommandNode<ServerCommandSource> node) {
        Map<String, Object> result = new TreeMap<>(); collect(node, "", result); return result;
    }
    private static void collect(CommandNode<ServerCommandSource> node, String path, Map<String, Object> result) {
        result.put(path + ":node", node);
        if (node.getCommand() != null) result.put(path + ":command", node.getCommand());
        if (node.getRedirect() != null) result.put(path + ":redirect", node.getRedirect());
        for (var child : node.getChildren()) collect(child, path + "/" + child.getName(), result);
    }
    public void registered(String root, CommandNode<ServerCommandSource> node) { snapshots.put(root, snapshot(node)); }
    public void audit(Settings cfg, CommandDispatcher<ServerCommandSource> dispatcher) {
        for (var e : snapshots.entrySet()) {
            var node = dispatcher.getRoot().getChild(e.getKey());
            if (node == null || !e.getValue().equals(snapshot(node))) add(cfg, new Conflict(e.getKey(), e.getKey(), "post-registration", "REPORT_ONLY", "Árvore alterada após registro; revisar mods e reiniciar. Nenhum nó de terceiros foi removido."));
        }
    }
    public List<Conflict> entries() { return List.copyOf(entries); }
    public void report(Path path) throws IOException {
        JsonFiles.write(path, Map.of("generatedAt", Instant.now().toString(), "conflicts", entries, "failedModules", failedModules,
            "limitations", List.of("Registros posteriores podem mesclar nós Brigadier. A auditoria não remove nem reconstrói comandos.", "Raízes de terceiros são sempre preservadas, inclusive com diagnóstico desativado.", "Auditoria compara raízes, subcomandos e executores ao final da inicialização; registros ainda posteriores não são observados.")));
    }
}
