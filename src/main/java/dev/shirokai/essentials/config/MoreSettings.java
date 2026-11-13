package dev.shirokai.essentials.config;

import com.google.gson.JsonElement;
import java.util.*;

public final class MoreSettings {
    public static class Chat {
        public int schemaVersion = 1, maxMessageLength = 256, mailboxLimit = 100, mailCooldownSeconds = 10, afkSeconds = 300;
        public boolean formatPublicChat = false;
        public String timeZone = "America/Sao_Paulo";
        public List<String> motd = new ArrayList<>(List.of("Bem-vindo ao Shirokai, {player}!"));
        public List<String> rules = new ArrayList<>(List.of("Respeite os jogadores e a equipe."));
        public List<String> info = new ArrayList<>(List.of("Servidor Shirokai — divirta-se!"));
        public List<String> help = new ArrayList<>(List.of("Use /homes, /warps e /spawn para consultar destinos."));
    }
    public static class Utilities {
        public int schemaVersion = 1;
        public boolean persistModes = false, allowSudo = false;
        public List<String> sudoAllowedCommands = new ArrayList<>(List.of("me"));
    }
    public static class Moderation {
        public int schemaVersion = 1, warningLimit = 100, maxReasonLength = 256;
        public boolean manageVanillaBans = false, enforceCommandMute = true;
        public List<String> mutedCommandRoots = new ArrayList<>(List.of("msg", "tell", "w", "whisper", "me", "say"));
        public double jailRadius = 4;
        public Map<String, Integer> ranks = new LinkedHashMap<>(Map.of("helper", 10, "moderator", 50, "admin", 100));
    }
    public static class Kit {
        public long cooldownSeconds = 86400;
        public String permission = "";
        public boolean firstJoin = false;
        public List<JsonElement> items = new ArrayList<>();
        public List<String> rewardCommands = new ArrayList<>();
    }
    public static class Kits {
        public int schemaVersion = 1;
        public boolean allowRewardCommands = false;
        public Map<String, Kit> kits = new TreeMap<>();
        public List<String> allowedRewardRoots = new ArrayList<>();
    }
    public static class Integrations {
        public int schemaVersion = 1;
        public boolean blockCobblemonBattles = true, coreBridge = true;
        public String essentialCommandsVersion = "";
    }
    private MoreSettings() {}
}
