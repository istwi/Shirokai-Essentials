package dev.shirokai.essentials.config;

import java.nio.file.Path;

/** Build-time utility, not a server entrypoint. */
public final class ConfigGenerator {
    public static void main(String[] args) throws Exception { Settings.load(Path.of(args.length == 0 ? "examples/config/shirokai-essentials" : args[0])); }
}
