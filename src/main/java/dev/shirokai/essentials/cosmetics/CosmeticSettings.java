package dev.shirokai.essentials.cosmetics;

import dev.shirokai.essentials.persistence.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;

public final class CosmeticSettings {
    public boolean enabled=true, nickEnabled=true, suppressInBattle=true;
    public int intervalTicks=5, maxParticlesPerPlayer=36, maxParticlesPerTick=400, viewDistance=32, celebrationCooldownSeconds=10;
    public int wingsIntervalTicks=4, trailDelayTicks=10, trailPoints=12;
    public String timezone="America/Sao_Paulo";
    public Set<String> disabled=new HashSet<>();
    public static CosmeticSettings load(Path path) throws IOException {
        var cfg=JsonFiles.read(path,CosmeticSettings.class,new CosmeticSettings());
        cfg.validate();return cfg;
    }
    public void validate() throws IOException {
        if(wingsIntervalTicks<4||wingsIntervalTicks>40||trailDelayTicks<2||trailDelayTicks>200||trailPoints<4||trailPoints>40||intervalTicks<2||intervalTicks>40||maxParticlesPerPlayer<1||maxParticlesPerPlayer>200||maxParticlesPerTick<1||maxParticlesPerTick>5000||viewDistance<4||viewDistance>64||celebrationCooldownSeconds<1||celebrationCooldownSeconds>3600||disabled==null||disabled.stream().anyMatch(id->CosmeticCatalog.find(id)==null))throw new IOException("Limites de cosméticos inválidos");
        try { ZoneId.of(timezone); } catch(Exception e) {throw new IOException("Fuso horário de cosméticos inválido",e);}
    }
}
