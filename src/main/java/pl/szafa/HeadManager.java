package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.szafa.Cosmetic.Kind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Glowa kostiumu jako osobny model (ItemDisplay) przyklejony do glowy gracza.
 * Nie zajmuje slotu helmu - gracz moze normalnie nosic zbroje.
 */
public final class HeadManager {

    private static final class Entry {
        final String costumeId;
        final ItemDisplay display;

        Entry(String costumeId, ItemDisplay display) {
            this.costumeId = costumeId;
            this.display = display;
        }
    }

    private final SzafaPlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public HeadManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Cosmetic c = plugin.activeOf(p, Kind.COSTUME);
            Entry en = entries.get(p.getUniqueId());

            boolean show = c != null && c.headModel != null && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;
            if (!show) {
                if (en != null) {
                    remove(p);
                }
                continue;
            }
            if (en != null && (!en.costumeId.equals(c.id) || !en.display.isValid())) {
                remove(p);
                en = null;
            }

            Location target = headLocation(p);
            if (en == null) {
                entries.put(p.getUniqueId(), new Entry(c.id, spawn(c, target)));
            } else {
                en.display.teleport(target);
            }
        }
        entries.entrySet().removeIf(e -> {
            if (Bukkit.getPlayer(e.getKey()) == null) {
                e.getValue().display.remove();
                return true;
            }
            return false;
        });
    }

    /** Srodek glowy gracza (uwzglednia przysiad, plywanie itp. przez pozycje oczu). */
    private Location headLocation(Player p) {
        Location eye = p.getEyeLocation();
        Location l = eye.clone().add(0, -0.07, 0);
        l.setYaw(eye.getYaw() + plugin.costumeHeadYawOffset());
        l.setPitch(eye.getPitch());
        return l;
    }

    private ItemDisplay spawn(Cosmetic c, Location loc) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(c.headModel);
        stack.setItemMeta(meta);

        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // model glowy ma 18 jednostek (9 px): skala 0.5 => 0.5625 bloku, tyle co glowa gracza z nakladka
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }

    public void remove(Player p) {
        Entry en = entries.remove(p.getUniqueId());
        if (en != null) {
            en.display.remove();
        }
    }

    public void removeAll() {
        for (Entry en : entries.values()) {
            en.display.remove();
        }
        entries.clear();
    }
}
