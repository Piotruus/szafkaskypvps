package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
 * Zwierzatko to ItemDisplay (glowa 3D albo caly model) z napisem nad soba (TextDisplay),
 * ktory plynnie leci obok i za graczem.
 */
public final class PetManager {

    private static final class Entry {
        final String petId;
        final ItemDisplay display;
        final TextDisplay label; // moze byc null (pet-nametag: false)

        Entry(String petId, ItemDisplay display, TextDisplay label) {
            this.petId = petId;
            this.display = display;
            this.label = label;
        }

        boolean valid() {
            return display.isValid() && (label == null || label.isValid());
        }

        void removeAll() {
            display.remove();
            if (label != null) {
                label.remove();
            }
        }
    }

    private final SzafaPlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private long ticks;

    public PetManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        ticks += 2;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Cosmetic pet = plugin.activeOf(p, Kind.PET);
            Entry en = entries.get(p.getUniqueId());

            boolean hidden = pet == null || p.isDead() || p.getGameMode() == GameMode.SPECTATOR;
            if (hidden) {
                if (en != null) {
                    remove(p);
                }
                continue;
            }
            if (en != null && (!en.petId.equals(pet.id) || !en.valid())) {
                remove(p);
                en = null;
            }

            Location target = followLocation(p);
            if (en == null) {
                entries.put(p.getUniqueId(), spawn(pet, target));
            } else {
                move(en, target);
            }
        }
        entries.entrySet().removeIf(e -> {
            if (Bukkit.getPlayer(e.getKey()) == null) {
                e.getValue().removeAll();
                return true;
            }
            return false;
        });
    }

    private double labelOffset() {
        return plugin.petScale() * 0.5 + 0.3;
    }

    /** Pozycja zwierzatka: po prawej, lekko z tylu, na wysokosci glowy, z delikatnym bujaniem. */
    private Location followLocation(Player p) {
        Location base = p.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double fwdX = -Math.sin(yaw);
        double fwdZ = Math.cos(yaw);
        double bob = Math.sin(ticks / 8.0) * 0.12;
        double height = (p.isSneaking() ? 1.75 : 2.0) + bob;
        Location l = base.clone().add(rightX * 0.95 - fwdX * 0.45, height, rightZ * 0.95 - fwdZ * 0.45);
        l.setYaw(base.getYaw() + plugin.petYawOffset());
        l.setPitch(0f);
        return l;
    }

    private Entry spawn(Cosmetic pet, Location loc) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (pet.worldModel != null) {
            meta.setItemModel(pet.worldModel);
        } else if (pet.itemModel != null) {
            meta.setItemModel(pet.itemModel);
        }
        stack.setItemMeta(meta);

        float scale = plugin.petScale();
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(3);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });

        TextDisplay label = null;
        if (plugin.petNametag()) {
            Location ll = loc.clone().add(0, labelOffset(), 0);
            label = loc.getWorld().spawn(ll, TextDisplay.class, t -> {
                t.text(Text.c(pet.name));
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setTeleportDuration(3);
                t.setPersistent(false);
                t.setInvulnerable(true);
            });
        }
        return new Entry(pet.id, display, label);
    }

    private void move(Entry en, Location target) {
        Location cur = en.display.getLocation();
        Location next;
        if (cur.getWorld() != target.getWorld() || cur.distanceSquared(target) > 100) {
            next = target;
        } else {
            next = cur.clone().add(
                    (target.getX() - cur.getX()) * 0.4,
                    (target.getY() - cur.getY()) * 0.4,
                    (target.getZ() - cur.getZ()) * 0.4);
            next.setYaw(target.getYaw());
            next.setPitch(0f);
        }
        en.display.teleport(next);
        if (en.label != null) {
            en.label.teleport(next.clone().add(0, labelOffset(), 0));
        }
    }

    public void remove(Player p) {
        Entry en = entries.remove(p.getUniqueId());
        if (en != null) {
            en.removeAll();
        }
    }

    public void removeAll() {
        for (Entry en : entries.values()) {
            en.removeAll();
        }
        entries.clear();
    }
}
