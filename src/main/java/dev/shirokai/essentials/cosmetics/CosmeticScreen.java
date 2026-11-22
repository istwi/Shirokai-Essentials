package dev.shirokai.essentials.cosmetics;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.io.IOException;
import java.util.*;

/** Display-only inventory: packet actions cannot extract previews or move real items. */
public final class CosmeticScreen extends GenericContainerScreenHandler {
    private final ServerPlayerEntity owner;
    private final CosmeticService service;
    private final SimpleInventory display;
    private final Map<Integer,Runnable> actions=new HashMap<>();
    private String page;
    private static final int[] POSITIONS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    public CosmeticScreen(int syncId,PlayerInventory inventory,ServerPlayerEntity owner,CosmeticService service,String page) {
        this(syncId,inventory,owner,service,page,new SimpleInventory(54));
    }
    private CosmeticScreen(int id,PlayerInventory inventory,ServerPlayerEntity owner,CosmeticService service,String page,SimpleInventory display) {
        super(ScreenHandlerType.GENERIC_9X6,id,inventory,display,6);this.owner=owner;this.service=service;this.page=page;this.display=display;render();
    }
    public static String title(String page) {
        return switch(page) {case "main"->"Escolha uma categoria";case "seasonal"->"Sazonais";case "collections"->"Coleções temáticas";default->CosmeticCatalog.CATEGORIES.getOrDefault(page,"Cosméticos");};
    }
    private static Item icon(String category) {
        return switch(category) {case "nick"->Items.NAME_TAG;case "trail"->Items.BLAZE_POWDER;case "aura"->Items.NETHER_STAR;case "footprint"->Items.LEATHER_BOOTS;case "crown"->Items.GOLDEN_HELMET;case "wings"->Items.FEATHER;case "circle"->Items.ENDER_EYE;case "teleport"->Items.ENDER_PEARL;case "join"->Items.FIREWORK_ROCKET;case "victory"->Items.TOTEM_OF_UNDYING;case "raid"->Items.DRAGON_BREATH;default->Items.AMETHYST_SHARD;};
    }
    private void button(int slot,Item item,String name,List<String> lore,Formatting color,Runnable action) {
        var stack=new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(name).styled(s->s.withColor(color).withItalic(false)));
        stack.set(DataComponentTypes.LORE,new LoreComponent(lore.stream().map(line->(Text)Text.literal(line).styled(s->s.withColor(Formatting.GRAY).withItalic(false))).toList()));
        display.setStack(slot,stack);if(action!=null)actions.put(slot,action);
    }
    private void render() {
        actions.clear();
        for(int i=0;i<54;i++)button(i,Items.GRAY_STAINED_GLASS_PANE," ",List.of(),Formatting.WHITE,null);
        try {
            var state=service.state(owner);
            if(page.equals("main")) {
                int i=0;
                for(String category:CosmeticCatalog.ORDER) {
                    var e=CosmeticCatalog.find(state.cosmetics.get(category));
                    button(POSITIONS[i++],icon(category),title(category),List.of("Selecionado: "+(e==null?"Nenhum":e.label()),description(category),"Clique para ver as opções."),Formatting.AQUA,()->service.open(owner,category));
                }
                button(30,Items.CLOCK,"Cosméticos sazonais",List.of("Opções disponíveis em meses especiais.","Confira o período de cada efeito."),Formatting.GOLD,()->service.open(owner,"seasonal"));
                button(32,Items.CHEST,"Coleções temáticas",List.of("Equipe um conjunto de efeitos combinando.","Exige acesso a cada efeito da coleção."),Formatting.AQUA,()->service.open(owner,"collections"));
                button(49,state.cosmeticsHidden?Items.LIME_DYE:Items.BARRIER,state.cosmeticsHidden?"Reativar cosméticos":"Desativar tudo",List.of("Sua seleção será mantida.","Desativado: oculta seus efeitos e", "as partículas de outros jogadores para você."),state.cosmeticsHidden?Formatting.GREEN:Formatting.RED,()->{if(state.cosmeticsHidden)service.resume(owner);else service.disable(owner);render();});
            } else if(page.equals("collections")) {
                int i=0;
                for(String style:List.of("stars","fire","water","nature","electric","soul")) {
                    var entries=CosmeticCatalog.collection(style);
                    boolean unlocked=entries.values().stream().map(CosmeticCatalog::find).allMatch(e->service.unlocked(owner,e));
                    String label=CosmeticCatalog.find("aura_"+style).label();
                    button(POSITIONS[i++],unlocked?Items.AMETHYST_SHARD:Items.IRON_BARS,label,List.of(unlocked?"Disponível":"Bloqueada: faltam efeitos para desbloquear.","Combina rastro, aura, pegadas, asas,", "coroa, círculo e efeitos de eventos.","A cor do nick será mantida.","Clique para equipar o conjunto."),unlocked?Formatting.AQUA:Formatting.DARK_GRAY,()->{service.collection(owner,style);render();});
                }
            } else {
                var entries=CosmeticCatalog.ENTRIES.stream().filter(e->page.equals("seasonal")?e.month()!=0:e.category().equals(page)&&e.month()==0).toList();
                int i=0;
                for(var entry:entries) {
                    boolean unlocked=service.unlocked(owner,entry), equipped=entry.id().equals(state.cosmetics.get(entry.category()));
                    var lore=new ArrayList<String>();
                    lore.add(equipped?(!unlocked?"Selecionado · indisponível":state.cosmeticsHidden?"Selecionado · efeitos desativados":"Equipado"):unlocked?"Disponível":"Bloqueado");
                    if(!unlocked)lore.add(!entry.available(service.today())?"Fora da temporada.":service.config().disabled.contains(entry.id())?"Desativado pelo servidor.":"Sem acesso a esta opção.");
                    lore.add(description(entry.category()));
                    if(entry.month()!=0)lore.add("Temporada: mês "+entry.month()+" · "+service.config().timezone);
                    lore.add("Permissão: shirokai.essentials.cosmetics."+entry.id());
                    lore.add("Clique para equipar. Não concede vantagens.");
                    if(entry.category().equals("nick"))lore.add("Outra tag/equipe pode impedir a cor do nick.");
                    button(POSITIONS[i++],unlocked?icon(entry.category()):Items.IRON_BARS,entry.label(),lore,equipped?Formatting.GREEN:unlocked?Formatting.AQUA:Formatting.DARK_GRAY,()->{service.equip(owner,entry);render();});
                }
                if(!page.equals("seasonal"))button(49,Items.BARRIER,"Remover desta categoria",List.of("As outras categorias serão mantidas."),Formatting.RED,()->{service.remove(owner,page);render();});
            }
            if(!page.equals("main"))button(45,Items.ARROW,"Voltar",List.of("Retornar às categorias."),Formatting.AQUA,()->service.open(owner,"main"));
            button(53,Items.OAK_DOOR,"Fechar",List.of(),Formatting.GRAY,owner::closeHandledScreen);
        } catch(IOException e) {service.error(owner,e);}
        sendContentUpdates();
    }
    private static String description(String category) {
        return switch(category) {case "nick"->"Altera a cor do nome; não muda seu nick.";case "trail"->"Partículas ao caminhar.";case "footprint"->"Partículas nos pés ao andar no chão.";case "aura"->"Partículas orbitando ao seu redor.";case "crown"->"Uma coroa de partículas acima da cabeça.";case "wings"->"Asas visuais nas costas. Não permitem voar.";case "circle"->"Um anel de partículas ao redor dos pés.";case "teleport"->"Efeito ao chegar de um teleporte detectado.";case "join"->"Efeito breve ao entrar no servidor.";case "victory"->"Celebração ao vencer PvP ou via integração.";case "raid"->"Celebração ativada pela integração de raid.";default->"Efeito visual.";};
    }
    @Override public boolean canUse(PlayerEntity player) {return player==owner&&owner.isAlive()&&service.canUse(owner);}
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player) {
        if(canUse(player)&&owner.currentScreenHandler==this&&action==SlotActionType.PICKUP&&(button==0||button==1)) {
            var actionHandler=actions.get(slot);if(actionHandler!=null)actionHandler.run();
        }
        if(owner.currentScreenHandler==this)syncState();
    }
    @Override public ItemStack quickMove(PlayerEntity player,int slot) {return ItemStack.EMPTY;}
}
