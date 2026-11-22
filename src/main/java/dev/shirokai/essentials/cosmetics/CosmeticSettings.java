package dev.shirokai.essentials.cosmetics;

import dev.shirokai.essentials.persistence.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class CosmeticSettings {
    public boolean enabled=true, nickEnabled=true, suppressInBattle=true;
    public int intervalTicks=5, maxParticlesPerPlayer=36, maxParticlesPerTick=400, viewDistance=32, celebrationCooldownSeconds=10;
    public Set<String> disabled=new HashSet<>();
    public static CosmeticSettings load(Path path) throws IOException {
        var cfg=JsonFiles.read(path,CosmeticSettings.class,new CosmeticSettings());
        cfg.validate();return cfg;
    }
    public void validate() throws IOException {
        if(intervalTicks<2||intervalTicks>40||maxParticlesPerPlayer<1||maxParticlesPerPlayer>200||maxParticlesPerTick<1||maxParticlesPerTick>5000||viewDistance<4||viewDistance>64||celebrationCooldownSeconds<1||celebrationCooldownSeconds>3600||disabled==null)throw new IOException("Limites de cosméticos inválidos");
        disabled.removeIf(id->CosmeticCatalog.find(id)==null);
    }
}
