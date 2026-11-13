package dev.shirokai.essentials.config;

import dev.shirokai.essentials.persistence.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class Settings {
    public static final Set<String> IMPLEMENTED_FEATURES = Set.of("teleport_warmup", "teleport_cooldown", "teleport_safety", "home_limits", "tpa_requests", "back_history", "back_on_death", "first_join_spawn", "respawn_at_spawn", "private_messages", "mail", "socialspy", "nicknames", "afk_manual", "afk_auto", "motd_auto", "join_messages", "quit_messages", "death_messages", "first_join_messages", "fly", "god_mode", "kit_cooldowns", "moderation_chat", "moderation_freeze", "moderation_jail", "moderation_warnings");
    public static final Set<String> IMPLEMENTED = Set.of("teleport", "homes", "warps", "spawn", "chat", "utilities", "administration", "moderation", "kits");
    public static final Map<String, String> COMMAND_MODULE;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        for (String s : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tpatoggle", "back", "tp", "tphere", "tpall", "tppos", "top", "jump", "near", "rtp")) map.put(s, "teleport");
        for (String s : List.of("home", "homes", "sethome", "delhome", "renamehome")) map.put(s, "homes");
        for (String s : List.of("warp", "warps", "setwarp", "delwarp")) map.put(s, "warps");
        for (String s : List.of("spawn", "setspawn")) map.put(s, "spawn");
        for (String name : List.of("msg", "tell", "whisper", "r", "reply", "ignore", "unignore", "mail", "socialspy", "msgtoggle", "broadcast", "me", "afk", "nick", "realname", "help", "rules", "motd", "info", "list", "seen", "whois")) map.put(name, "chat");
        for (String name : List.of("fly", "god", "heal", "feed", "speed", "invsee", "enderchest", "workbench", "anvil", "repair", "hat", "clearinventory", "ping", "gc")) map.put(name, "utilities");
        for (String name : List.of("sudo", "time", "weather")) map.put(name, "administration");
        for (String name : List.of("kick", "ban", "tempban", "unban", "mute", "tempmute", "unmute", "warn", "warnings", "clearwarnings", "setjail", "deljail", "jail", "unjail", "freeze")) map.put(name, "moderation");
        for (String name : List.of("kit", "kits", "createkit", "delkit", "showkit", "resetkit", "kitrecover")) map.put(name, "kits");
        map.put("ecimport", "core");
        map.put("shirokaiessentials", "core");
        COMMAND_MODULE = Collections.unmodifiableMap(map);
    }
    public static class General { public int schemaVersion = 1; public boolean auditCommands = true; }
    public static class Modules {
        public int schemaVersion = 1;
        public Map<String, Boolean> modules = new LinkedHashMap<>();
        public Map<String, Boolean> features = new LinkedHashMap<>();
        public Modules() {
            for (String s : List.of("teleport", "homes", "warps", "spawn")) modules.put(s, true);
            for (String s : List.of("chat", "kits", "moderation", "administration", "utilities")) modules.put(s, false);
            for (String s : List.of("teleport_warmup", "teleport_cooldown", "teleport_safety", "home_limits", "tpa_requests", "back_history")) features.put(s, true);
            for (String s : List.of("back_on_death", "first_join_spawn", "respawn_at_spawn", "motd_auto", "afk_auto", "join_messages", "quit_messages", "death_messages")) features.put(s, false);
            for (String s : List.of("private_messages", "mail", "socialspy", "afk_manual", "kit_cooldowns", "moderation_chat", "moderation_freeze", "moderation_jail", "moderation_warnings")) features.put(s, true);
            features.put("fly", true); features.put("god_mode", false); features.put("nicknames", false); features.put("first_join_messages", false);
        }
    }
    public static class Command { public boolean enabled = true; public List<String> aliases = new ArrayList<>(); public Map<String, Boolean> aliasEnabled = new LinkedHashMap<>(); }
    public static class Commands {
        public int schemaVersion = 1;
        public Map<String, Command> commands = new LinkedHashMap<>();
        public Map<String, Boolean> adminSubcommands = new LinkedHashMap<>();
        public Commands() {
            COMMAND_MODULE.keySet().forEach(s -> commands.put(s, new Command()));
            for (String s : List.of("status", "version", "reload", "conflicts", "report")) adminSubcommands.put(s, true);
        }
    }
    public enum Policy { DISABLE_OWN_COMMAND, USE_ALTERNATIVE_ALIAS, FAIL_MODULE, FAIL_STARTUP }
    public static class Conflicts {
        public int schemaVersion = 1;
        public boolean enabled = true, scanOnStartup = true, logConflicts = true, preventCommandOverride = true, checkAliases = true, checkSubcommands = true, checkModDependencies = true;
        public Policy defaultPolicy = Policy.DISABLE_OWN_COMMAND;
        public Map<String, Policy> commands = new LinkedHashMap<>();
    }
    public static class Permissions {
        public int schemaVersion = 1;
        public boolean requireLuckPerms = false, opFallback = true;
        public int defaultHomeLimit = 3;
        public Map<String, Integer> homeLimits = new LinkedHashMap<>(Map.of("vip", 10, "admin", 100));
        public Map<String, Boolean> defaults = new LinkedHashMap<>();
        public Permissions() {
            for (String s : List.of("home", "homes", "sethome", "delhome", "renamehome", "warp", "warps", "spawn", "tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tpatoggle", "back", "msg", "tell", "whisper", "r", "reply", "ignore", "unignore", "mail", "msgtoggle", "afk", "help", "rules", "motd", "info", "list", "realname", "seen", "ping", "kit", "kits", "showkit")) defaults.put(s, true);
        }
    }
    public static class Teleport {
        public int schemaVersion = 1;
        public int warmupSeconds = 3, cooldownSeconds = 10, requestExpirySeconds = 60, maxPendingRequests = 3, maxPendingTeleports = 128;
        public boolean cancelOnMovement = true, cancelOnDamage = true;
        public double movementTolerance = 0.15;
        public int backRetentionSeconds = 86400, rtpRadius = 1000, rtpAttempts = 16, rtpMinRadius = 100;
        public List<String> blockedWorlds = new ArrayList<>();
        public boolean rejectUnloadedChunks = true, asyncChunkLoading = false;
        public int asyncTimeoutSeconds = 15;
    }
    public static class Homes { public int schemaVersion = 1; public String defaultHome = "home"; public List<String> blockedWorlds = new ArrayList<>(); }
    public static class Warps { public int schemaVersion = 1; public boolean individualPermissions = false; }
    public static class Spawn { public int schemaVersion = 1; public boolean perWorld = false; }
    public static class Disabled { public int schemaVersion = 1; public boolean enabled = false; public String status = "Não implementado nesta versão; não ativar."; }
    public static class Messages {
        public int schemaVersion = 1;
        public String prefix = "✦ SHIROKAI ";
        public Map<String, String> messages = new LinkedHashMap<>();
        public Messages() {
            messages.putAll(MoreMessages.defaults());
            messages.put("status", "Versão {version} | módulos: {modules} | integrações: {integrations}");
            messages.put("version", "Shirokai Essentials {version} / Minecraft 1.21.1");
            messages.put("conflicts", "Conflitos: {conflicts}");
            messages.put("ok", "Concluído."); messages.put("saved", "Destino {name} salvo.");
            messages.put("deleted", "Destino {name} removido."); messages.put("missing", "Destino não encontrado.");
            messages.put("invalid_name", "Use um nome de 1 a 32 letras, números, _ ou -.");
            messages.put("limit", "Você atingiu o limite de {limit} homes.");
            messages.put("unsafe", "Destino indisponível ou inseguro. Tente outro local.");
            messages.put("cooldown", "Aguarde {time}s para teleportar novamente.");
            messages.put("warmup", "Teleporte em {time}s. Permaneça parado.");
            messages.put("cancelled", "Teleporte cancelado."); messages.put("teleported", "Teleporte concluído.");
            messages.put("pending", "Você já possui um teleporte pendente.");
            messages.put("busy", "Muitos pedidos pendentes. Tente novamente.");
            messages.put("request_sent", "Pedido enviado para {player}.");
            messages.put("request_received", "{player} pediu um teleporte ({type}). Use /tpaccept {player} ou /tpdeny {player}.");
            messages.put("request_missing", "Pedido inexistente ou expirado.");
            messages.put("request_expired", "O pedido de teleporte expirou.");
            messages.put("self", "Escolha outro jogador."); messages.put("requests_off", "Este jogador bloqueou pedidos de teleporte.");
            messages.put("toggle", "Pedidos de teleporte: {state}.");
            messages.put("list", "Destinos: {names}"); messages.put("near", "Jogadores próximos: {names}");
            messages.put("no_back", "Nenhuma posição anterior válida.");
            messages.put("storage_error", "Não foi possível salvar. Nenhuma alteração foi aplicada.");
            messages.put("reload", "Configurações dinâmicas recarregadas. Alterações em módulos, funcionalidades, comandos, aliases e políticas de conflito exigem reinício.");
            messages.put("reload_error", "Configuração inválida. A configuração anterior foi mantida; consulte o log.");
            messages.put("report", "Relatório salvo em {path}.");
        }
    }
    public General general; public Modules modules; public Commands commands; public Conflicts conflicts;
    public Permissions permissions; public Teleport teleport; public Homes homes; public Warps warps; public Spawn spawn; public Messages messages;
    public MoreSettings.Chat chat; public MoreSettings.Utilities utilities; public MoreSettings.Moderation moderation; public MoreSettings.Kits kits; public MoreSettings.Integrations integrations;
    public static Settings load(Path dir) throws IOException {
        Settings s = new Settings();
        s.general = JsonFiles.read(dir.resolve("config.json"), General.class, new General());
        s.modules = JsonFiles.read(dir.resolve("modules.json"), Modules.class, new Modules());
        s.commands = JsonFiles.read(dir.resolve("commands.json"), Commands.class, new Commands());
        s.conflicts = JsonFiles.read(dir.resolve("conflicts.json"), Conflicts.class, new Conflicts());
        s.permissions = JsonFiles.read(dir.resolve("permissions.json"), Permissions.class, new Permissions());
        s.teleport = JsonFiles.read(dir.resolve("teleport.json"), Teleport.class, new Teleport());
        s.homes = JsonFiles.read(dir.resolve("homes.json"), Homes.class, new Homes());
        s.warps = JsonFiles.read(dir.resolve("warps.json"), Warps.class, new Warps());
        s.spawn = JsonFiles.read(dir.resolve("spawn.json"), Spawn.class, new Spawn());
        s.messages = JsonFiles.read(dir.resolve("messages.json"), Messages.class, new Messages());
        s.chat = JsonFiles.read(dir.resolve("chat.json"), MoreSettings.Chat.class, new MoreSettings.Chat());
        s.utilities = JsonFiles.read(dir.resolve("utilities.json"), MoreSettings.Utilities.class, new MoreSettings.Utilities());
        s.moderation = JsonFiles.read(dir.resolve("moderation.json"), MoreSettings.Moderation.class, new MoreSettings.Moderation());
        s.kits = JsonFiles.read(dir.resolve("kits.json"), MoreSettings.Kits.class, new MoreSettings.Kits());
        s.integrations = JsonFiles.read(dir.resolve("integrations.json"), MoreSettings.Integrations.class, new MoreSettings.Integrations());
        // Retired in 0.2.2. Ignore old flags/aliases without rewriting users' files.
        // economy.json is deliberately never read: teleport and kit claims are free.
        s.modules.modules.remove("economy");
        for (String command : List.of("balance", "bal", "money", "pay", "baltop", "eco")) {
            s.commands.commands.remove(command);
            s.conflicts.commands.remove(command);
            s.permissions.defaults.keySet().removeIf(key -> key.equals(command) || key.startsWith(command + "."));
        }
        // Additive schema-1 upgrade: retain every explicit flag and add descriptions for new settings.
        Set<String> newModules=Set.of("chat","utilities","administration","moderation","kits");
        new Commands().commands.forEach((k,v)->{if(newModules.contains(COMMAND_MODULE.get(k))||k.equals("ecimport"))s.commands.commands.putIfAbsent(k,v);});
        Set<String> newFeatures=Set.of("private_messages","mail","socialspy","nicknames","afk_manual","fly","god_mode","kit_cooldowns","moderation_chat","moderation_freeze","moderation_jail","moderation_warnings","first_join_messages");
        new Modules().features.forEach((k,v)->{if(newFeatures.contains(k))s.modules.features.putIfAbsent(k,v);});
        new Modules().modules.forEach((k,v)->{if(newModules.contains(k))s.modules.modules.putIfAbsent(k,v);});
        new Permissions().defaults.forEach((k,v)->{if(newModules.contains(COMMAND_MODULE.get(k)))s.permissions.defaults.putIfAbsent(k,v);});
        try { s.validate(); } catch (RuntimeException e) { throw new IOException("Configuração inválida: " + e.getMessage(), e); }
        return s;
    }
    public boolean feature(String name) { return modules.features.getOrDefault(name, false); }
    public boolean module(String name) { return "core".equals(name) || (IMPLEMENTED.contains(name) && modules.modules.getOrDefault(name, false)); }
    public boolean command(String name) {
        Command c = commands.commands.get(name);
        if (c == null || !c.enabled || !module(COMMAND_MODULE.getOrDefault(name, "missing"))) return false;
        return switch (name) {
            case "msg", "tell", "whisper", "r", "reply", "msgtoggle" -> feature("private_messages");
            case "mute", "tempmute", "unmute" -> feature("moderation_chat");
            case "freeze" -> feature("moderation_freeze");
            case "jail", "unjail", "setjail", "deljail" -> feature("moderation_jail");
            case "warn", "warnings", "clearwarnings" -> feature("moderation_warnings");
            case "mail" -> feature("mail"); case "socialspy" -> feature("socialspy");
            case "nick", "realname" -> feature("nicknames"); case "afk" -> feature("afk_manual");
            case "fly" -> feature("fly"); case "god" -> feature("god_mode");
            case "sudo" -> utilities.allowSudo;
            case "ban", "tempban", "unban" -> moderation.manageVanillaBans;
            default -> true;
        };
    }
    public boolean subcommand(String name) { return commands.adminSubcommands.getOrDefault(name, false); }
    public static boolean validName(String name) { return name != null && name.matches("[a-zA-Z0-9_-]{1,32}"); }
    private void validate() {
        if (chat.schemaVersion != 1 || utilities.schemaVersion != 1 || moderation.schemaVersion != 1 || kits.schemaVersion != 1 || integrations.schemaVersion != 1) throw new IllegalArgumentException("Schema de módulo não suportado");
        if (chat.maxMessageLength < 1 || chat.maxMessageLength > 4096 || chat.mailboxLimit < 1 || chat.mailboxLimit > 1000 || chat.mailCooldownSeconds < 1 || chat.afkSeconds < 10 || moderation.warningLimit < 1 || moderation.warningLimit > 1000 || moderation.maxReasonLength < 1 || moderation.maxReasonLength > 4096 || !Double.isFinite(moderation.jailRadius) || moderation.jailRadius < 1 || moderation.jailRadius > 100) throw new IllegalArgumentException("Limites de módulos inválidos");
        java.time.ZoneId.of(chat.timeZone);
        Objects.requireNonNull(chat.motd); Objects.requireNonNull(chat.rules); Objects.requireNonNull(chat.help); Objects.requireNonNull(chat.info);
        for(var lines:List.of(chat.motd,chat.rules,chat.help,chat.info))if(lines.size()>100||lines.stream().anyMatch(line->line==null||line.length()>4096))throw new IllegalArgumentException("Texto de chat excessivo");
        if(moderation.ranks.values().stream().anyMatch(rank->rank==null||rank<0||rank>10000))throw new IllegalArgumentException("Hierarquia inválida");
        Objects.requireNonNull(moderation.mutedCommandRoots);
        Objects.requireNonNull(utilities.sudoAllowedCommands); Objects.requireNonNull(moderation.ranks); Objects.requireNonNull(kits.kits);
        for (var e : kits.kits.entrySet()) if (!validName(e.getKey()) || e.getValue() == null || e.getValue().items == null || e.getValue().items.size() > 54 || e.getValue().items.stream().anyMatch(i->i==null||i.isJsonNull()) || e.getValue().permission==null || e.getValue().rewardCommands == null || e.getValue().cooldownSeconds < 0 || e.getValue().cooldownSeconds > 31536000) throw new IllegalArgumentException("Kit inválido: " + e.getKey());
        if (general.schemaVersion != 1 || modules.schemaVersion != 1 || commands.schemaVersion != 1 || conflicts.schemaVersion != 1 || permissions.schemaVersion != 1 || teleport.schemaVersion != 1 || homes.schemaVersion != 1 || warps.schemaVersion != 1 || spawn.schemaVersion != 1 || messages.schemaVersion != 1) throw new IllegalArgumentException("schemaVersion não suportado");
        Objects.requireNonNull(modules.features); Objects.requireNonNull(commands.adminSubcommands); Objects.requireNonNull(messages.messages);
        if (conflicts.commands.values().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Política de conflito inválida");
        Objects.requireNonNull(conflicts.defaultPolicy); Objects.requireNonNull(conflicts.commands); Objects.requireNonNull(permissions.defaults);
        Objects.requireNonNull(teleport.blockedWorlds); Objects.requireNonNull(homes.blockedWorlds); Objects.requireNonNull(messages.prefix);
        if (messages.messages.containsValue(null) || permissions.defaults.containsValue(null) || commands.adminSubcommands.containsValue(null)) throw new IllegalArgumentException("Valor nulo na configuração");
        modules.modules.forEach((k,v) -> { if (v == null || (v && !IMPLEMENTED.contains(k))) throw new IllegalArgumentException("Módulo indisponível: " + k); });
        modules.features.forEach((k,v) -> { if (v == null || (v && !IMPLEMENTED_FEATURES.contains(k))) throw new IllegalArgumentException("Funcionalidade indisponível: " + k); });
        commands.commands.forEach((k,c) -> {
            if (!COMMAND_MODULE.containsKey(k) || c == null || c.aliases == null || c.aliasEnabled == null) throw new IllegalArgumentException("Comando inválido: " + k);
            for (String alias : c.aliases) if (!validName(alias) || !alias.equals(alias.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Alias inválido: " + alias);
        });
        if (permissions.defaultHomeLimit < 0 || permissions.homeLimits.values().stream().anyMatch(n -> n == null || n < 0)) throw new IllegalArgumentException("Limites negativos");
        if (!validName(homes.defaultHome)) throw new IllegalArgumentException("Home padrão inválida");
        if (teleport.asyncTimeoutSeconds < 1 || teleport.asyncTimeoutSeconds > 60 || teleport.warmupSeconds < 0 || teleport.warmupSeconds > 300 || teleport.cooldownSeconds < 0 || teleport.cooldownSeconds > 86400 || teleport.requestExpirySeconds < 1 || teleport.requestExpirySeconds > 600 || teleport.maxPendingRequests < 1 || teleport.maxPendingRequests > 100 || teleport.maxPendingTeleports < 1 || teleport.maxPendingTeleports > 1000 || !Double.isFinite(teleport.movementTolerance) || teleport.movementTolerance < 0 || teleport.movementTolerance > 5 || teleport.backRetentionSeconds < 1 || teleport.rtpAttempts < 1 || teleport.rtpAttempts > 100 || teleport.rtpMinRadius < 0 || teleport.rtpRadius <= teleport.rtpMinRadius || teleport.rtpRadius > 1000000) throw new IllegalArgumentException("Limites de teleporte inválidos");
    }
    /** Brigadier topology remains the startup snapshot; safe dynamic values are swapped together. */
    public Settings dynamicFrom(Settings candidate) {
        candidate.modules = modules; candidate.commands = commands; candidate.conflicts = conflicts;
        candidate.utilities.allowSudo = utilities.allowSudo; candidate.moderation.manageVanillaBans = moderation.manageVanillaBans;
        candidate.chat.formatPublicChat = chat.formatPublicChat;
        candidate.integrations.coreBridge = integrations.coreBridge;
        return candidate;
    }
}
