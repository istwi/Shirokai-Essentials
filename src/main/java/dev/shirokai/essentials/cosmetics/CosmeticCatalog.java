package dev.shirokai.essentials.cosmetics;

import java.time.*;
import java.util.*;

/** Stable identifiers, deliberately separate from the Portuguese display names. */
public final class CosmeticCatalog {
    public record Entry(String id,String category,String label,String style,int month) {
        public boolean available(LocalDate date) { return month==0 || date.getMonthValue()==month; }
    }
    public static final Map<String,String> CATEGORIES = Collections.unmodifiableMap(new LinkedHashMap<>(Map.ofEntries(
        Map.entry("nick","Cor do nick"),Map.entry("trail","Rastros"),Map.entry("aura","Auras"),
        Map.entry("footprint","Pegadas elementais"),Map.entry("crown","Coroas"),Map.entry("wings","Asas de partículas"),
        Map.entry("circle","Círculos elementais"),Map.entry("teleport","Teleportes temáticos"),
        Map.entry("join","Entrada triunfal"),Map.entry("victory","Efeitos de vitória"),Map.entry("raid","Celebração de raid")
    )));
    public static final List<String> ORDER = List.of("nick","trail","aura","footprint","crown","wings","circle","teleport","join","victory","raid");
    public static final List<Entry> ENTRIES;
    static {
        var entries=new ArrayList<Entry>();
        entries.add(new Entry("nick_cyan","nick","Ciano","cyan",0));
        entries.add(new Entry("nick_gold","nick","Dourado","gold",0));
        entries.add(new Entry("nick_rainbow","nick","Arco-íris suave","rainbow",0));
        for(String category:ORDER) if(!category.equals("nick")) {
            entries.add(new Entry(category+"_stars",category,"Estelar","stars",0));
            entries.add(new Entry(category+"_fire",category,"Fogo","fire",0));
            entries.add(new Entry(category+"_water",category,"Água","water",0));
            entries.add(new Entry(category+"_nature",category,"Natureza","nature",0));
            entries.add(new Entry(category+"_electric",category,"Elétrico","electric",0));
            entries.add(new Entry(category+"_soul",category,"Fantasma","soul",0));
        }
        entries.add(new Entry("trail_halloween","trail","Halloween · Almas","soul",10));
        entries.add(new Entry("aura_christmas","aura","Natal · Neve","snow",12));
        entries.add(new Entry("circle_valentine","circle","Dia dos Namorados · Corações","heart",6));
        ENTRIES=List.copyOf(entries);
    }
    public static Entry find(String id) { return ENTRIES.stream().filter(e->e.id.equals(id)).findFirst().orElse(null); }
    public static Map<String,String> collection(String style) {
        if(!Set.of("stars","fire","water","nature","electric","soul").contains(style))throw new IllegalArgumentException("Coleção desconhecida");
        var result=new TreeMap<String,String>();
        for(String category:ORDER) if(!category.equals("nick"))result.put(category,category+"_"+style);
        return result;
    }
    private CosmeticCatalog() {}
}
