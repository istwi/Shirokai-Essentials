package dev.shirokai.essentials.moderation;

import java.util.regex.Pattern;
public final class Durations {
    private static final Pattern FORMAT = Pattern.compile("([1-9][0-9]{0,8})(s|m|h|d)");
    public static long millis(String value) {
        var matcher=FORMAT.matcher(value); if (!matcher.matches()) throw new IllegalArgumentException("Duração: use 30s, 10m, 2h ou 7d");
        long n=Long.parseLong(matcher.group(1)); long factor=switch(matcher.group(2)){case "s"->1000;case "m"->60000;case "h"->3600000;default->86400000;};
        long duration=Math.multiplyExact(n,factor); if (duration>365L*86400000) throw new IllegalArgumentException("Duração máxima: 365 dias"); return duration;
    }
    private Durations() {}
}
