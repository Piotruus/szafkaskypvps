package pl.szafa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Menus implements Listener {

    // uklad glownego okna (54 sloty): zwierzatko po lewej, kostium po prawej
    private static final int PET_SLOT = 10;
    private static final int COSTUME_SLOT = 16;
    private static final int PET_LIST_SLOT = 19;
    private static final int COSTUME_LIST_SLOT = 25;
    private static final int PET_CLOCK_SLOT = 28;
    private static final int COSTUME_CLOCK_SLOT = 34;
    private static final int BACK_SLOT = 49;

    // Y = zolty naroznik, O = pomaranczowy, L = jasnoszary, G = szary, . = tlo
    private static final String[] LAYOUT = {
            "YOLLGLLOY",
            "O.......O",
            "L.......L",
            "L.......L",
            "O.......O",
            "YOLLGLLOY"
    };

    private static final List<Integer> LIST_SLOTS = new ArrayList<>();

    static {
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                LIST_SLOTS.add(row * 9 + col);
            }
        }
    }

    private static final NamespacedKey PET_PLACEHOLDER = NamespacedKey.fromString("szafa:pet_placeholder");

    /** Holder oznaczajacy nasze GUI. kind == null => glowne okno SZAFA. */
    private static final class Holder implements InventoryHolder {
        final Kind kind;
        Inventory inv;

        Holder(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final SzafaPlugin plugin;

    public Menus(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ otwieranie

    public void openMain(Player p) {
        Holder h = new Holder(null);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&a&lSZAFA"));
        h.inv = inv;
        renderMain(p, inv);
        p.openInventory(inv);
    }

    public void openList(Player p, Kind kind) {
        Holder h = new Holder(kind);
        String title = kind == Kind.PET ? "&a&lLista Petów" : "&6&lLista Kostiumów";
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(title));
        h.inv = inv;
        renderList(p, inv, kind);
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ rysowanie

    private void renderMain(Player p, Inventory inv) {
        fillFrame(inv);
        inv.setItem(PET_SLOT, slotItem(p, Kind.PET));
        inv.setItem(COSTUME_SLOT, slotItem(p, Kind.COSTUME));
        inv.setItem(PET_LIST_SLOT, listBook(p, Kind.PET));
        inv.setItem(COSTUME_LIST_SLOT, listBook(p, Kind.COSTUME));
        inv.setItem(PET_CLOCK_SLOT, clock(p, Kind.PET));
        inv.setItem(COSTUME_CLOCK_SLOT, clock(p, Kind.COSTUME));
    }

    /** Lista = katalog dla wszystkich: prawdziwe ikony, opisy, bez zakladania. */
    private void renderList(Player p, Inventory inv, Kind kind) {
        fillFrame(inv);
        List<Cosmetic> defs = new ArrayList<>(plugin.defs(kind));
        Cosmetic active = plugin.activeOf(p, kind);
        for (int i = 0; i < defs.size() && i < LIST_SLOTS.size(); i++) {
            Cosmetic c = defs.get(i);
            inv.setItem(LIST_SLOTS.get(i), catalogIcon(c, active != null && active.id.equals(c.id)));
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.displayName(Text.c("&cPowrót"));
        back.setItemMeta(m);
        inv.setItem(BACK_SLOT, back);
    }

    private void fillFrame(Inventory inv) {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, pane(LAYOUT[row].charAt(col)));
            }
        }
    }

    private ItemStack pane(char ch) {
        Material mat = switch (ch) {
            case 'Y' -> Material.YELLOW_STAINED_GLASS_PANE;
            case 'O' -> Material.ORANGE_STAINED_GLASS_PANE;
            case 'L' -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    // ---- sloty aktywnego zwierzatka / kostiumu

    private ItemStack slotItem(Player p, Kind kind) {
        Cosmetic active = plugin.activeOf(p, kind);
        if (active != null) {
            return activeIcon(p, active);
        }
        if (kind == Kind.PET) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            if (PET_PLACEHOLDER != null) {
                m.setItemModel(PET_PLACEHOLDER);
            }
            m.displayName(Text.c("&cBrak aktywnego peta"));
            m.lore(List.of(
                    Text.c("&7Weź item zwierzątka do ręki"),
                    Text.c("&7i kliknij &fPPM&7, aby je założyć.")));
            it.setItemMeta(m);
            return it;
        }
        ItemStack it = new ItemStack(Material.ARMOR_STAND);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c("&cBrak aktywnego kostiumu"));
        m.lore(List.of(
                Text.c("&7Weź item kostiumu do ręki"),
                Text.c("&7i kliknij &fPPM&7, aby go założyć.")));
        it.setItemMeta(m);
        return it;
    }

    /** Ikona aktywnego zwierzatka/kostiumu w slocie (z data wygasniecia). */
    private ItemStack activeIcon(Player p, Cosmetic c) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (c.itemModel != null) {
            m.setItemModel(c.itemModel);
        }
        m.displayName(Text.c(c.name));

        long exp = plugin.expiryOf(p, c.kind);
        String noun = c.kind == Kind.PET ? "ZWIERZĄTKO" : "KOSTIUM";
        List<Component> lore = new ArrayList<>();
        for (String line : c.description) {
            lore.add(Text.c(line));
        }
        lore.add(Component.empty());
        if (exp == Long.MAX_VALUE) {
            lore.add(Text.c("&a✔ &f" + noun + " &aBEZ LIMITU CZASU"));
        } else {
            lore.add(Text.c("&c✘ &f" + noun + " WYGASA: &c" + plugin.format(exp)));
            lore.add(Text.c("&7Pozostało: &f" + plugin.remaining(exp)));
        }
        lore.add(Component.empty());
        lore.add(Text.c("&a✔ &a&lAKTYWNE"));
        lore.add(Text.c("&e&lKLIKNIJ &7- zdejmij (item wróci do ekwipunku)"));
        lore.add(Component.empty());
        lore.add(Text.c(c.rarity));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    // ---- ksiazki (katalog) i zegary (przedluzanie)

    private ItemStack listBook(Player p, Kind kind) {
        ItemStack it = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(kind == Kind.PET ? "&a&lLISTA PETÓW" : "&6&lLISTA KOSTIUMÓW"));

        Cosmetic active = plugin.activeOf(p, kind);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.c("&7Kliknij, aby zobaczyć wszystkie"));
        lore.add(Text.c(kind == Kind.PET ? "&7dostępne zwierzątka." : "&7dostępne kostiumy."));
        lore.add(Component.empty());
        lore.add(Text.c("&7Dostępnych: &f" + plugin.defs(kind).size()));
        lore.add(Text.c("&7Aktywne: " + (active != null ? "&f" + active.name : "&cbrak")));
        if (active != null) {
            long exp = plugin.expiryOf(p, kind);
            lore.add(Text.c("&7Wygasa: &f" + plugin.format(exp) + " &8(" + plugin.remaining(exp) + ")"));
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack clock(Player p, Kind kind) {
        ItemStack it = new ItemStack(Material.CLOCK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(kind == Kind.PET ? "&e&lPRZEDŁUŻ PETA" : "&e&lPRZEDŁUŻ KOSTIUM"));

        List<Component> lore = new ArrayList<>();
        Cosmetic active = plugin.activeOf(p, kind);
        if (active == null) {
            lore.add(Text.c("&c✘ &fNie masz aktywnego " + (kind == Kind.PET ? "peta" : "kostiumu")));
        } else {
            long exp = plugin.expiryOf(p, kind);
            lore.add(Text.c("&7Aktywne: &f" + active.name));
            if (exp == Long.MAX_VALUE) {
                lore.add(Text.c("&aDziała bez limitu czasu."));
            } else {
                lore.add(Text.c("&7Wygasa: &f" + plugin.format(exp)));
                lore.add(Text.c("&7Pozostało: &f" + plugin.remaining(exp)));
            }
            lore.add(Component.empty());
            Map<Integer, Integer> scrolls = plugin.scrollCounts(p);
            if (scrolls.isEmpty()) {
                lore.add(Text.c("&c✘ &fBrak &6Zwojów przedłużenia &fw ekwipunku."));
            } else {
                lore.add(Text.c("&7Twoje zwoje:"));
                scrolls.forEach((days, count) ->
                        lore.add(Text.c("&8→ &f" + days + " dni &7x" + count)));
                lore.add(Component.empty());
                lore.add(Text.c("&e&lLPM &7- użyj najmniejszego zwoju"));
                lore.add(Text.c("&e&lPPM &7- użyj największego zwoju"));
            }
        }
        lore.add(Component.empty());
        lore.add(Text.c("&8Zwój możesz też przeciągnąć na item"));
        lore.add(Text.c("&8zwierzątka/kostiumu w ekwipunku."));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    // ---- ikony katalogu

    private ItemStack catalogIcon(Cosmetic c, boolean worn) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (c.itemModel != null) {
            m.setItemModel(c.itemModel);
        }
        m.displayName(Text.c(c.name));

        List<Component> lore = new ArrayList<>();
        for (String line : c.description) {
            lore.add(Text.c(line));
        }
        lore.add(Component.empty());
        lore.add(Text.c("&7Podgląd - tu nic się nie zakłada."));
        lore.add(Text.c("&7Item dostaniesz na serwerze - kliknij go &fPPM&7."));
        if (worn) {
            lore.add(Component.empty());
            lore.add(Text.c("&a✔ &aAKTUALNIE ZAŁOŻONE"));
        }
        lore.add(Component.empty());
        lore.add(Text.c(c.rarity));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (worn) {
            m.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ klikniecia

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (h.kind == null) {
            clickMain(p, h, e.getSlot(), e.getClick());
        } else if (e.getSlot() == BACK_SLOT) {
            click(p);
            openMain(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    private void clickMain(Player p, Holder h, int slot, ClickType click) {
        switch (slot) {
            case PET_SLOT -> unequip(p, h, Kind.PET);
            case COSTUME_SLOT -> unequip(p, h, Kind.COSTUME);
            case PET_LIST_SLOT -> {
                click(p);
                openList(p, Kind.PET);
            }
            case COSTUME_LIST_SLOT -> {
                click(p);
                openList(p, Kind.COSTUME);
            }
            case PET_CLOCK_SLOT -> {
                plugin.extendActive(p, Kind.PET, click.isRightClick());
                renderMain(p, h.inv);
            }
            case COSTUME_CLOCK_SLOT -> {
                plugin.extendActive(p, Kind.COSTUME, click.isRightClick());
                renderMain(p, h.inv);
            }
            default -> {
            }
        }
    }

    private void unequip(Player p, Holder h, Kind kind) {
        if (!plugin.hasActive(p, kind)) {
            plugin.msg(p, "&cNie masz aktywnego " + (kind == Kind.PET ? "peta" : "kostiumu") + ".");
        } else if (plugin.unequip(p, kind)) {
            click(p);
        }
        renderMain(p, h.inv);
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
