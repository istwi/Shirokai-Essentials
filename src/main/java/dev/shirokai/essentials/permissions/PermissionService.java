package dev.shirokai.essentials.permissions;

import dev.shirokai.essentials.config.Settings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import java.util.function.Supplier;

public final class PermissionService {
    private final Supplier<Settings> settings;
    public PermissionService(Supplier<Settings> settings) { this.settings = settings; }
    public boolean has(ServerCommandSource source, String suffix) {
        if (source.getEntity() == null) return source.hasPermissionLevel(4);
        if (!source.isExecutedByPlayer()) return false;
        var cfg = settings.get().permissions;
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            // Isolated method keeps the optional API out of class loading when LuckPerms is absent.
            Boolean result = luckPerms(source, "shirokai.essentials." + suffix);
            if (result != null) return result;
            if (cfg.requireLuckPerms) return false;
        } else if (cfg.requireLuckPerms) return false;
        return cfg.defaults.getOrDefault(suffix, false) || (cfg.opFallback && source.hasPermissionLevel(2));
    }
    private Boolean luckPerms(ServerCommandSource source, String node) {
        try {
            var api = LuckPermsProvider.get();
            var user = api.getUserManager().getUser(source.getPlayer().getUuid());
            if (user == null) return false; // no cached user: fail closed
            var options = api.getContextManager().getQueryOptions(user).orElse(api.getContextManager().getStaticQueryOptions());
            var permission = user.getCachedData().getPermissionData(options).queryPermission(node);
            Tristate result = enderChestResult(node, permission.result(), permission.node() == null ? null : permission.node().getKey());
            return result == Tristate.UNDEFINED ? null : result.asBoolean();
        } catch (IllegalStateException | LinkageError e) { return false; }
    }
    /** LuckPerms shorthand may expand a plain parent grant to its child nodes. */
    static Tristate enderChestResult(String requested, Tristate result, String origin) {
        if (requested.equals("shirokai.essentials.enderchest.others") && result == Tristate.TRUE
                && (origin == null || requested.startsWith(origin + "."))) {
            // Ignore the inferred grant; explicit configuration/OP fallback still applies.
            return Tristate.UNDEFINED;
        }
        return result;
    }
    public int rank(net.minecraft.server.MinecraftServer server, java.util.UUID id) {
        var player=server.getPlayerManager().getPlayer(id);
        if(FabricLoader.getInstance().isModLoaded("luckperms")) {
            try {
                var api=LuckPermsProvider.get(); var user=api.getUserManager().getUser(id);
                if(user==null)return -1;
                var opts=api.getContextManager().getQueryOptions(user).orElse(api.getContextManager().getStaticQueryOptions());
                var permissions=user.getCachedData().getPermissionData(opts);
                if(permissions.checkPermission("shirokai.essentials.moderation.exempt").asBoolean())return Integer.MAX_VALUE;
                int rank=0;
                for(var entry:settings.get().moderation.ranks.entrySet()) if(permissions.checkPermission("shirokai.essentials.moderation.rank."+entry.getKey()).asBoolean())rank=Math.max(rank,entry.getValue());
                return rank;
            }catch(IllegalStateException|LinkageError e){return -1;}
        }
        if(settings.get().permissions.requireLuckPerms)return -1;
        var profile=new com.mojang.authlib.GameProfile(id,player==null?"offline":player.getName().getString());
        return server.getPlayerManager().isOperator(profile)?100:0;
    }
    public String prefix(net.minecraft.server.network.ServerPlayerEntity p) {
        if(!FabricLoader.getInstance().isModLoaded("luckperms"))return "";
        try {
            var api=LuckPermsProvider.get();var u=api.getUserManager().getUser(p.getUuid());if(u==null)return "";
            var opts=api.getContextManager().getQueryOptions(u).orElse(api.getContextManager().getStaticQueryOptions());
            return java.util.Objects.requireNonNullElse(u.getCachedData().getMetaData(opts).getPrefix(),"");
        }catch(IllegalStateException|LinkageError e){return "";}
    }
    public int homeLimit(ServerCommandSource source) {
        var cfg = settings.get().permissions;
        int limit = cfg.defaultHomeLimit;
        // Highest explicitly granted limit wins; OP/default fallback never grants a tier implicitly.
        if (FabricLoader.getInstance().isModLoaded("luckperms")) for (var entry : cfg.homeLimits.entrySet()) {
            if (Boolean.TRUE.equals(luckPerms(source, "shirokai.essentials.home.multiple." + entry.getKey()))) limit = Math.max(limit, entry.getValue());
        }
        return Math.min(10000, limit);
    }
}
