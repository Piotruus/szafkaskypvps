package pl.szafa;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.concurrent.ThreadLocalRandom;

public final class Listeners implements Listener {

    private final SzafaPlugin plugin;

    public Listeners(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    // ------------------------------------------------------------------ kostiumy (widocznosc)

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(20L, () -> plugin.costumeManager().refresh(p));
        later(60L, () -> plugin.joinCheck(p));
    }

    /** Prawy klik z itemem zwierzatka / kostiumu / zwoju w rece. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseItem(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Items.Parsed parsed = plugin.items().parse(e.getItem());
        if (parsed == null) {
            return;
        }
        e.setCancelled(true);
        plugin.useItem(e.getPlayer(), parsed);
    }

    /** Zwoj z kursora upuszczony (LPM) na item zwierzatka/kostiumu w ekwipunku. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onScrollOnItem(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getClick() != ClickType.LEFT) {
            return;
        }
        ItemStack cursor = e.getCursor();
        Items.Parsed scroll = plugin.items().parse(cursor);
        if (scroll == null || !scroll.type().equals(Items.TYPE_SCROLL) || scroll.days() <= 0) {
            return;
        }
        ItemStack target = e.getCurrentItem();
        Items.Parsed tg = plugin.items().parse(target);
        if (tg == null || tg.type().equals(Items.TYPE_SCROLL)) {
            return;
        }
        e.setCancelled(true);
        if (target.getAmount() != 1) {
            plugin.msg(p, "&cPrzedłużać można tylko pojedynczy item (rozdziel stos).");
            return;
        }
        ItemStack updated = plugin.scrollOnItem(p, scroll, tg);
        if (updated == null) {
            return;
        }
        e.setCurrentItem(updated);
        if (cursor.getAmount() <= 1) {
            p.setItemOnCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            p.setItemOnCursor(cursor);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.petManager().remove(e.getPlayer());
        plugin.headManager().remove(e.getPlayer());
        plugin.costumeManager().forget(e.getPlayer());
    }

    private static boolean isOwnInventory(InventoryType type) {
        return type == InventoryType.CRAFTING || type == InventoryType.CREATIVE;
    }

    /** Klik w wlasny ekwipunek: pokazujemy prawdziwa zbroje, zeby zdejmowanie/zakladanie dzialalo bez bugow. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOwnInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isOwnInventory(e.getView().getType())
                && plugin.activeOf(p, Cosmetic.Kind.COSTUME) != null) {
            plugin.costumeManager().suspendSelf(p);
        }
    }

    /** Zamkniecie ekwipunku: wracamy do widoku kostiumu. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && isOwnInventory(e.getView().getType())) {
            later(2L, () -> plugin.costumeManager().resumeSelf(p));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        later(5L, () -> plugin.costumeManager().refresh(p));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        later(5L, () -> plugin.costumeManager().refresh(p));
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();
        later(1L, () -> plugin.costumeManager().refresh(p));
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent e) {
        if (e.getEntity() instanceof Player target
                && plugin.activeOf(target, Cosmetic.Kind.COSTUME) != null) {
            Player viewer = e.getPlayer();
            later(2L, () -> plugin.costumeManager().sendTo(viewer, target));
        }
    }

    // ------------------------------------------------------------------ bonusy: obrazenia

    private Player resolveDamager(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** Zadawane obrazenia (zwierzatko + kostium). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent e) {
        Player damager = resolveDamager(e.getDamager());
        if (damager == null) {
            return;
        }
        boolean inEnd = damager.getWorld().getEnvironment() == World.Environment.THE_END;
        boolean vsPlayer = e.getEntity() instanceof Player victim && !victim.equals(damager);

        double percent = 0;
        for (Bonuses b : plugin.bonusesOf(damager)) {
            percent += (inEnd && b.endDamagePercent > 0) ? b.endDamagePercent : b.damagePercent;
            if (vsPlayer) {
                percent += b.pvpDamagePercent;
            }
        }
        if (percent != 0) {
            e.setDamage(e.getDamage() * (1.0 + percent / 100.0));
        }
    }

    /** Otrzymywane obrazenia (upadek i redukcja ogolna). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.SUICIDE
                || cause == EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        double reduction = 0;
        for (Bonuses b : plugin.bonusesOf(victim)) {
            reduction += (cause == EntityDamageEvent.DamageCause.FALL)
                    ? b.fallDamageReduction
                    : b.damageReduction;
        }
        if (reduction <= 0) {
            return;
        }
        if (reduction >= 100) {
            e.setCancelled(true);
        } else {
            e.setDamage(e.getDamage() * (1.0 - reduction / 100.0));
        }
    }

    // ------------------------------------------------------------------ bonusy: zabicia i perly

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        for (Bonuses b : plugin.bonusesOf(killer)) {
            if (e.getEntity() instanceof Enderman && b.pearlDropMultiplier > 1) {
                for (ItemStack drop : e.getDrops()) {
                    if (drop.getType() == Material.ENDER_PEARL) {
                        int amount = (int) Math.round(drop.getAmount() * b.pearlDropMultiplier);
                        drop.setAmount(Math.min(64, Math.max(1, amount)));
                    }
                }
            }
            if (e.getEntity() instanceof Player && !e.getEntity().equals(killer)) {
                for (Bonuses.EffectSpec s : b.killEffects) {
                    killer.addPotionEffect(new PotionEffect(s.type(), s.ticks(), s.amplifier(), false, true, true));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlThrow(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl)
                || !(pearl.getShooter() instanceof Player p)
                || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        double chance = 0;
        for (Bonuses b : plugin.bonusesOf(p)) {
            chance += b.pearlRefundChance;
        }
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() * 100.0 < chance) {
            later(1L, () -> {
                if (p.isOnline()) {
                    p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                    plugin.msg(p, "&dZwierzątko oddało Ci perłę!");
                }
            });
        }
    }
}
