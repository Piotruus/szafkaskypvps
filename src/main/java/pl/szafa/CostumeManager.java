package pl.szafa;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import pl.szafa.Cosmetic.Kind;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wirtualne kostiumy: ekwipunek gracza zostaje nietkniety. Innym graczom (i opcjonalnie
 * wlascicielowi) wysylamy falszywe pakiety wyposazenia z modelem zbroi z resource packa.
 *
 * Glowa kostiumu NIE jest helmem - to osobny model (HeadManager). Prawdziwy helm mozna
 * normalnie nosic; gdy kostium ma glowe, helm jest wizualnie ukryty.
 *
 * Dla wlasciciela falszywe itemy kopiuja prawdziwe (ta sama ikona i opis w ekwipunku),
 * a podczas otwartego ekwipunku wysylamy mu prawdziwe wyposazenie, zeby zdejmowanie
 * i zakladanie zbroi dzialalo bez bugow.
 */
public final class CostumeManager {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final NamespacedKey INVISIBLE = NamespacedKey.fromString("szafa:invisible");

    private final SzafaPlugin plugin;
    private final Map<String, Map<EquipmentSlot, ItemStack>> cacheOthers = new HashMap<>();
    private final Set<UUID> suspended = new HashSet<>();

    public CostumeManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearCache() {
        cacheOthers.clear();
    }

    // ------------------------------------------------------------------ budowanie itemow

    /** Falszywy item zbroi. base == null/air => niewidoczna ikona (nic nie widac w ekwipunku). */
    private ItemStack fake(ItemStack base, EquipmentSlot slot, NamespacedKey asset) {
        boolean empty = base == null || base.getType().isAir();
        ItemStack it;
        if (empty) {
            it = new ItemStack(Material.PAPER);
        } else {
            it = base.clone();
            it.setAmount(1);
        }
        ItemMeta meta = it.getItemMeta();
        if (empty) {
            if (INVISIBLE != null) {
                meta.setItemModel(INVISIBLE);
            }
            meta.setHideTooltip(true);
        }
        EquippableComponent eq = meta.getEquippable();
        eq.setSlot(slot);
        if (asset != null) {
            eq.setModel(asset);
        }
        meta.setEquippable(eq);
        it.setItemMeta(meta);
        return it;
    }

    /** Wyposazenie widziane przez innych graczy. */
    private Map<EquipmentSlot, ItemStack> forOthers(Cosmetic c) {
        return cacheOthers.computeIfAbsent(c.id, k -> {
            Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
            if (c.armorModel == null) {
                return map;
            }
            for (EquipmentSlot slot : SLOTS) {
                if (slot == EquipmentSlot.HEAD && c.headModel != null) {
                    if (plugin.hideHelmet()) {
                        map.put(slot, new ItemStack(Material.AIR));   // glowe rysuje model 3D, helm ukryty
                    }
                    continue;
                }
                map.put(slot, fake(null, slot, c.armorModel));
            }
            return map;
        });
    }

    /** Wyposazenie widziane przez wlasciciela: kopie prawdziwych itemow z nowym wygladem. */
    private Map<EquipmentSlot, ItemStack> forSelf(Player target, Cosmetic c) {
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        if (c.armorModel == null) {
            return map;
        }
        for (EquipmentSlot slot : SLOTS) {
            ItemStack real = target.getInventory().getItem(slot);
            boolean hasReal = real != null && !real.getType().isAir();
            if (slot == EquipmentSlot.HEAD && c.headModel != null) {
                if (plugin.hideHelmet() && hasReal) {
                    map.put(slot, fake(real, slot, INVISIBLE));
                }
                continue;
            }
            map.put(slot, fake(real, slot, c.armorModel));
        }
        return map;
    }

    private Map<EquipmentSlot, ItemStack> real(Player target) {
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack it = target.getInventory().getItem(slot);
            map.put(slot, it == null ? new ItemStack(Material.AIR) : it.clone());
        }
        return map;
    }

    // ------------------------------------------------------------------ wysylanie

    /** Wysyla kostium wszystkim, ktorzy widza gracza (i wlascicielowi, o ile nie ma otwartego ekwipunku). */
    public void refresh(Player target) {
        Cosmetic c = plugin.activeOf(target, Kind.COSTUME);
        if (c == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> others = forOthers(c);
        if (!others.isEmpty()) {
            for (Player viewer : target.getTrackedBy()) {
                viewer.sendEquipmentChange(target, others);
            }
        }
        if (plugin.showCostumeToSelf() && !suspended.contains(target.getUniqueId())) {
            Map<EquipmentSlot, ItemStack> self = forSelf(target, c);
            if (!self.isEmpty()) {
                target.sendEquipmentChange(target, self);
            }
        }
    }

    /** Wysyla kostium jednemu widzowi (gdy zaczyna widziec gracza). */
    public void sendTo(Player viewer, Player target) {
        Cosmetic c = plugin.activeOf(target, Kind.COSTUME);
        if (c == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> others = forOthers(c);
        if (!others.isEmpty()) {
            viewer.sendEquipmentChange(target, others);
        }
    }

    /** Przywraca prawdziwy wyglad zbroi po zdjeciu kostiumu. */
    public void restore(Player target) {
        suspended.remove(target.getUniqueId());
        Map<EquipmentSlot, ItemStack> real = real(target);
        for (Player viewer : target.getTrackedBy()) {
            viewer.sendEquipmentChange(target, real);
        }
        target.sendEquipmentChange(target, real);
    }

    // ------------------------------------------------------------------ ekwipunek wlasciciela

    /** Gracz klika w swoj ekwipunek: pokazujemy mu prawdziwe wyposazenie (bez duchow i kopii). */
    public void suspendSelf(Player p) {
        if (!suspended.add(p.getUniqueId())) {
            return;
        }
        p.sendEquipmentChange(p, real(p));
    }

    /** Gracz zamknal ekwipunek: wracamy do widoku kostiumu. */
    public void resumeSelf(Player p) {
        if (suspended.remove(p.getUniqueId())) {
            refresh(p);
        }
    }

    public void forget(Player p) {
        suspended.remove(p.getUniqueId());
    }
}
