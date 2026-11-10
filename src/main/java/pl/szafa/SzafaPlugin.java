package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import pl.szafa.Cosmetic.Kind;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class SzafaPlugin extends JavaPlugin {

    public static final long DAY_MS = 86_400_000L;
    public static final long HOUR_MS = 3_600_000L;

    private final Map<String, Cosmetic> pets = new LinkedHashMap<>();
    private final Map<String, Cosmetic> costumes = new LinkedHashMap<>();

    private DataStore data;
    private Items items;
    private PetManager petManager;
    private CostumeManager costumeManager;
    private HeadManager headManager;
    private Menus menus;
    private DateTimeFormatter dateFormat;
    private boolean showCostumeToSelf = true;
    private float petScale = 0.5f;
    private float petYawOffset = 0f;
    private boolean petNametag = true;
    private float costumeHeadYawOffset = 0f;
    private boolean hideHelmet = true;
    private int joinWarningHours = 24;
    private int maintenanceTick;

    @Override
    public void onEnable() {
        migrateConfig();
        items = new Items(this);
        loadDefinitions();

        data = new DataStore(this);
        petManager = new PetManager(this);
        costumeManager = new CostumeManager(this);
        headManager = new HeadManager(this);
        menus = new Menus(this);

        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);

        getCommand("szafa").setExecutor(new SzafaCommand(this));
        AdminCommand admin = new AdminCommand(this);
        getCommand("szafaadmin").setExecutor(admin);
        getCommand("szafaadmin").setTabCompleter(admin);

        Bukkit.getScheduler().runTaskTimer(this, petManager::tick, 20L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, headManager::tick, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::maintenance, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.removeAll();
        }
        if (headManager != null) {
            headManager.removeAll();
        }
        if (data != null) {
            data.flushNow();
        }
    }

    // ------------------------------------------------------------------ config

    /**
     * Gdy w pluginie jest nowsza wersja konfiguracji (config-version), stary config.yml jest
     * zapisywany jako config.backup-vX.yml i wgrywany jest nowy. Dzieki temu po aktualizacji
     * pluginu/packa nie zostaja stare nazwy modeli ani stare ustawienia.
     */
    private void migrateConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (file.exists()) {
            int current = YamlConfiguration.loadConfiguration(file).getInt("config-version", 0);
            int wanted = 0;
            try (InputStream in = getResource("config.yml")) {
                if (in != null) {
                    wanted = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8)).getInt("config-version", 0);
                }
            } catch (IOException ignored) {
                // brak domyslnego configu w jarze - zostawiamy bez zmian
            }
            if (current < wanted) {
                File backup = new File(getDataFolder(), "config.backup-v" + current + ".yml");
                try {
                    Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().warning("Stara konfiguracja (v" + current + ") zapisana jako "
                            + backup.getName() + ". Wgrano nowa (v" + wanted + ").");
                } catch (IOException ex) {
                    getLogger().warning("Nie udalo sie zrobic kopii starego configu: " + ex.getMessage());
                }
            }
        }
        saveDefaultConfig();
    }

    public void loadDefinitions() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        ZoneId zone;
        try {
            zone = ZoneId.of(cfg.getString("timezone", "Europe/Warsaw"));
        } catch (Exception ex) {
            zone = ZoneId.systemDefault();
        }
        dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(zone);
        showCostumeToSelf = cfg.getBoolean("show-costume-to-self", true);
        petScale = (float) cfg.getDouble("pet-scale", 0.5);
        petYawOffset = (float) cfg.getDouble("pet-yaw-offset", 0.0);
        petNametag = cfg.getBoolean("pet-nametag", true);
        costumeHeadYawOffset = (float) cfg.getDouble("costume-head-yaw-offset", 0.0);
        hideHelmet = cfg.getBoolean("hide-helmet-with-costume", true);
        joinWarningHours = cfg.getInt("join-warning-hours", 24);

        pets.clear();
        costumes.clear();
        loadSection(cfg.getConfigurationSection("pets"), Kind.PET, pets);
        loadSection(cfg.getConfigurationSection("costumes"), Kind.COSTUME, costumes);
        items.reload(cfg.getConfigurationSection("scroll"));
        if (costumeManager != null) {
            costumeManager.clearCache();
        }
    }

    private void loadSection(ConfigurationSection root, Kind kind, Map<String, Cosmetic> out) {
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) {
                out.put(id, Cosmetic.load(kind, id, s, getLogger()));
            }
        }
    }

    public void reloadAll() {
        loadDefinitions();
        petManager.removeAll();
        headManager.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            costumeManager.refresh(p);
        }
    }

    // ------------------------------------------------------------------ accessors

    public DataStore data() {
        return data;
    }

    public Items items() {
        return items;
    }

    public PetManager petManager() {
        return petManager;
    }

    public CostumeManager costumeManager() {
        return costumeManager;
    }

    public HeadManager headManager() {
        return headManager;
    }

    public Menus menus() {
        return menus;
    }

    public float costumeHeadYawOffset() {
        return costumeHeadYawOffset;
    }

    public boolean hideHelmet() {
        return hideHelmet;
    }

    public boolean showCostumeToSelf() {
        return showCostumeToSelf;
    }

    public float petScale() {
        return petScale;
    }

    public float petYawOffset() {
        return petYawOffset;
    }

    public boolean petNametag() {
        return petNametag;
    }

    public Collection<Cosmetic> defs(Kind kind) {
        return (kind == Kind.PET ? pets : costumes).values();
    }

    public Cosmetic def(Kind kind, String id) {
        if (id == null) {
            return null;
        }
        return (kind == Kind.PET ? pets : costumes).get(id);
    }

    public String format(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "BEZ LIMITU";
        }
        return dateFormat.format(Instant.ofEpochMilli(millis));
    }

    /** Pozostaly czas w ladnej formie: "6d 23h", "5h 12m", "12m". */
    public String remaining(long expiry) {
        if (expiry == Long.MAX_VALUE) {
            return "bez limitu";
        }
        long diff = expiry - System.currentTimeMillis();
        if (diff <= 0) {
            return "wygasło";
        }
        long days = diff / DAY_MS;
        long hours = (diff % DAY_MS) / HOUR_MS;
        long minutes = (diff % HOUR_MS) / 60_000L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    public static String noun(Kind kind) {
        return kind == Kind.PET ? "zwierzątko" : "kostium";
    }

    public static String activeId(PlayerData d, Kind kind) {
        return kind == Kind.PET ? d.petId : d.costumeId;
    }

    public static long activeExpiry(PlayerData d, Kind kind) {
        return kind == Kind.PET ? d.petExpiry : d.costumeExpiry;
    }

    private static void setActive(PlayerData d, Kind kind, String id, long expiry) {
        if (kind == Kind.PET) {
            d.petId = id;
            d.petExpiry = expiry;
        } else {
            d.costumeId = id;
            d.costumeExpiry = expiry;
        }
    }

    public void msg(CommandSender to, String text) {
        to.sendMessage(Text.c(Text.PREFIX + text));
    }

    // ------------------------------------------------------------------ stan gracza

    /** Czy gracz ma cokolwiek zapisane jako aktywne (takze wygasle). */
    public boolean hasActive(Player p, Kind kind) {
        return activeId(data.get(p.getUniqueId()), kind) != null;
    }

    /** Aktywne i niewygasniete zwierzatko / kostium gracza (albo null). */
    public Cosmetic activeOf(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        String id = activeId(d, kind);
        if (id == null) {
            return null;
        }
        if (activeExpiry(d, kind) <= System.currentTimeMillis()) {
            return null;
        }
        return def(kind, id);
    }

    public long expiryOf(Player p, Kind kind) {
        return activeExpiry(data.get(p.getUniqueId()), kind);
    }

    public List<Cosmetic> activeList(Player p) {
        List<Cosmetic> out = new ArrayList<>(2);
        Cosmetic pet = activeOf(p, Kind.PET);
        Cosmetic costume = activeOf(p, Kind.COSTUME);
        if (pet != null) {
            out.add(pet);
        }
        if (costume != null) {
            out.add(costume);
        }
        return out;
    }

    public List<Bonuses> bonusesOf(Player p) {
        List<Bonuses> out = new ArrayList<>(2);
        for (Cosmetic c : activeList(p)) {
            out.add(c.bonuses);
        }
        return out;
    }

    // ------------------------------------------------------------------ zakladanie / zdejmowanie

    /** Zaklada zwierzatko/kostium z podana data wygasniecia. Poprzednie wraca do ekwipunku jako item. */
    public void equip(Player p, Cosmetic c, long expiry) {
        PlayerData d = data.get(p.getUniqueId());
        Cosmetic old = activeOf(p, c.kind);
        if (old != null) {
            clearEffects(p, old.bonuses);
            giveItem(p, items.activatedItem(old, activeExpiry(d, c.kind)));
            msg(p, "&7Poprzednie " + noun(c.kind) + " wróciło do ekwipunku.");
        }
        setActive(d, c.kind, c.id, expiry);
        if (c.kind == Kind.PET) {
            petManager.remove(p);
        }
        data.markDirty();
        applyEffects(p);
        if (c.kind == Kind.COSTUME) {
            costumeManager.refresh(p);
        }
    }

    /**
     * Zdejmuje zwierzatko/kostium. Item wraca do ekwipunku z ta sama data wygasniecia
     * (czas plynie nadal). Zwraca false, gdy nic nie zdjeto (np. brak miejsca).
     */
    public boolean unequip(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        String id = activeId(d, kind);
        if (id == null) {
            return false;
        }
        Cosmetic c = def(kind, id);
        long exp = activeExpiry(d, kind);
        boolean valid = c != null && exp > System.currentTimeMillis();
        if (valid && p.getInventory().firstEmpty() == -1) {
            msg(p, "&cBrak miejsca w ekwipunku - zrób miejsce, aby zdjąć " + noun(kind) + ".");
            return false;
        }
        if (c != null) {
            clearEffects(p, c.bonuses);
        }
        setActive(d, kind, null, 0L);
        if (kind == Kind.PET) {
            petManager.remove(p);
        } else {
            headManager.remove(p);
            costumeManager.restore(p);
        }
        data.markDirty();
        if (valid) {
            giveItem(p, items.activatedItem(c, exp));
            msg(p, "Zdjęto: &f" + c.name + "&7. Item wrócił do ekwipunku - czas wygaśnięcia nadal płynie (do &f"
                    + format(exp) + "&7).");
        }
        return true;
    }

    /** Czysci aktywne bez zwracania itemu (wygasniecie / admin). */
    private void clearActive(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        Cosmetic c = def(kind, activeId(d, kind));
        if (c != null) {
            clearEffects(p, c.bonuses);
        }
        setActive(d, kind, null, 0L);
        if (kind == Kind.PET) {
            petManager.remove(p);
        } else {
            headManager.remove(p);
            costumeManager.restore(p);
        }
        data.markDirty();
    }

    // ------------------------------------------------------------------ uzycie przedmiotow

    /** Prawy klik z itemem pluginu w rece. */
    public void useItem(Player p, Items.Parsed pr) {
        switch (pr.type()) {
            case Items.TYPE_PET -> useCosmeticItem(p, Kind.PET, pr);
            case Items.TYPE_COSTUME -> useCosmeticItem(p, Kind.COSTUME, pr);
            case Items.TYPE_SCROLL -> {
                msg(p, "Zwój przeciągnij na item zwierzątka/kostiumu w ekwipunku albo kliknij &6zegar &7w &f/szafa&7.");
                menus.openMain(p);
            }
            default -> {
            }
        }
    }

    private void useCosmeticItem(Player p, Kind kind, Items.Parsed pr) {
        Cosmetic c = def(kind, pr.id());
        if (c == null) {
            msg(p, "&cTen przedmiot jest nieznany (usunięty z konfiguracji).");
            return;
        }
        long now = System.currentTimeMillis();
        long exp;
        if (pr.expiry() >= 0) {
            exp = pr.expiry();
            if (exp != Long.MAX_VALUE && exp <= now) {
                msg(p, "&cTen item już wygasł (" + format(exp) + ").");
                return;
            }
        } else {
            exp = pr.days() <= 0 ? Long.MAX_VALUE : now + pr.days() * DAY_MS;
        }
        consumeOneFromMainHand(p);
        equip(p, c, exp);
        msg(p, "&aZałożono: &f" + c.name + " &7- wygasa: &f" + format(exp) + " &8(" + remaining(exp) + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }

    private void consumeOneFromMainHand(Player p) {
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
            p.getInventory().setItemInMainHand(inHand);
        }
    }

    /** Zliczone zwoje w ekwipunku: dni -> ilosc (posortowane rosnaco). */
    public Map<Integer, Integer> scrollCounts(Player p) {
        Map<Integer, Integer> out = new TreeMap<>();
        for (ItemStack s : p.getInventory().getStorageContents()) {
            Items.Parsed pr = items.parse(s);
            if (pr != null && pr.type().equals(Items.TYPE_SCROLL) && pr.days() > 0) {
                out.merge(pr.days(), s.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    /** Przedluza AKTYWNE zwierzatko/kostium zwojem z ekwipunku (najmniejszym albo najwiekszym). */
    public boolean extendActive(Player p, Kind kind, boolean largest) {
        String what = kind == Kind.PET ? "peta" : "kostiumu";
        Cosmetic c = activeOf(p, kind);
        if (c == null) {
            msg(p, "&cNie masz aktywnego " + what + ".");
            return false;
        }
        PlayerData d = data.get(p.getUniqueId());
        long exp = activeExpiry(d, kind);
        if (exp == Long.MAX_VALUE) {
            msg(p, "&cTo " + noun(kind) + " działa bez limitu czasu - nie trzeba przedłużać.");
            return false;
        }

        int bestSlot = -1;
        int bestDays = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            Items.Parsed pr = items.parse(contents[i]);
            if (pr == null || !pr.type().equals(Items.TYPE_SCROLL) || pr.days() <= 0) {
                continue;
            }
            if (bestSlot < 0 || (largest ? pr.days() > bestDays : pr.days() < bestDays)) {
                bestSlot = i;
                bestDays = pr.days();
            }
        }
        if (bestSlot < 0) {
            msg(p, "&cNie masz &6Zwoju przedłużenia &cw ekwipunku.");
            return false;
        }

        ItemStack s = p.getInventory().getItem(bestSlot);
        if (s == null || s.getAmount() <= 1) {
            p.getInventory().setItem(bestSlot, null);
        } else {
            s.setAmount(s.getAmount() - 1);
            p.getInventory().setItem(bestSlot, s);
        }

        long newExp = exp + bestDays * DAY_MS;
        setActive(d, kind, c.id, newExp);
        data.markDirty();
        msg(p, "&aPrzedłużono &f" + c.name + " &ao &f" + bestDays + " dni&a. Nowa data wygaśnięcia: &f"
                + format(newExp) + " &8(" + remaining(newExp) + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        return true;
    }

    /**
     * Zwoj z kursora upuszczony na item zwierzatka/kostiumu w ekwipunku.
     * Zwraca nowy item (z przesunieta data) albo null, gdy sie nie da.
     */
    public ItemStack scrollOnItem(Player p, Items.Parsed scroll, Items.Parsed target) {
        Kind kind = target.type().equals(Items.TYPE_PET) ? Kind.PET : Kind.COSTUME;
        Cosmetic c = def(kind, target.id());
        if (c == null) {
            msg(p, "&cTen item jest nieznany.");
            return null;
        }
        long add = scroll.days() * DAY_MS;
        ItemStack result;
        long newExp = -1L;
        if (target.expiry() >= 0) {
            if (target.expiry() == Long.MAX_VALUE) {
                msg(p, "&cTo " + noun(kind) + " działa bez limitu czasu.");
                return null;
            }
            if (target.expiry() <= System.currentTimeMillis()) {
                msg(p, "&cTen item już wygasł - nie da się go przedłużyć.");
                return null;
            }
            newExp = target.expiry() + add;
            result = items.activatedItem(c, newExp);
        } else {
            if (target.days() <= 0) {
                msg(p, "&cTo " + noun(kind) + " działa bez limitu czasu.");
                return null;
            }
            result = items.cosmeticItem(c, target.days() + scroll.days(), 1);
        }
        msg(p, "&aPrzedłużono &f" + c.name + " &ao &f" + scroll.days() + " dni"
                + (newExp >= 0 ? "&a. Nowa data wygaśnięcia: &f" + format(newExp) : "&a."));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        return result;
    }

    // ------------------------------------------------------------------ admin

    /** Zaklada od razu (bez itemu) - tylko dla graczy online. days == null = bez limitu. */
    public long grantNow(Player p, Cosmetic c, Integer days) {
        long exp = days == null ? Long.MAX_VALUE : System.currentTimeMillis() + days * DAY_MS;
        equip(p, c, exp);
        return exp;
    }

    /** Zabiera aktywne zwierzatko/kostium (bez zwrotu itemu). */
    public boolean takeActive(UUID uuid, Kind kind) {
        PlayerData d = data.get(uuid);
        if (activeId(d, kind) == null) {
            return false;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            clearActive(online, kind);
        } else {
            setActive(d, kind, null, 0L);
            data.markDirty();
        }
        return true;
    }

    /** Daje item do ekwipunku, nadmiar wyrzuca pod nogi. */
    public void giveItem(Player target, ItemStack item) {
        Map<Integer, ItemStack> left = target.getInventory().addItem(item);
        Location loc = target.getLocation();
        for (ItemStack rest : left.values()) {
            target.getWorld().dropItemNaturally(loc, rest);
        }
    }

    // ------------------------------------------------------------------ efekty i wygasanie

    public void applyEffects(Player p) {
        for (Cosmetic c : activeList(p)) {
            for (Bonuses.EffectSpec s : c.bonuses.effects) {
                p.addPotionEffect(new PotionEffect(s.type(), 100, s.amplifier(), true, false, true));
            }
        }
    }

    public void clearEffects(Player p, Bonuses b) {
        for (Bonuses.EffectSpec s : b.effects) {
            PotionEffect cur = p.getPotionEffect(s.type());
            if (cur != null && cur.getAmplifier() == s.amplifier() && cur.getDuration() <= 120) {
                p.removePotionEffect(s.type());
            }
        }
    }

    /** Po wejsciu: ostrzega, ze cos wkrotce wygasa. */
    public void joinCheck(Player p) {
        if (joinWarningHours <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Kind kind : Kind.values()) {
            Cosmetic c = activeOf(p, kind);
            long exp = expiryOf(p, kind);
            if (c != null && exp != Long.MAX_VALUE && exp - now < joinWarningHours * HOUR_MS) {
                msg(p, "&eTwoje " + noun(kind) + " &f" + c.name + " &ewygaśnie za &f" + remaining(exp)
                        + "&e. Użyj &6Zwoju przedłużenia&e.");
            }
        }
    }

    private void maintenance() {
        maintenanceTick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            checkExpiry(p);
            if (maintenanceTick % 2 == 0) {
                applyEffects(p);
            }
            if (maintenanceTick % 2 == 0) {
                costumeManager.refresh(p);
            }
        }
        if (maintenanceTick % 10 == 0) {
            data.flushAsyncIfDirty();
        }
    }

    private void checkExpiry(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        for (Kind kind : Kind.values()) {
            String id = activeId(d, kind);
            if (id == null) {
                continue;
            }
            if (activeExpiry(d, kind) <= now) {
                Cosmetic c = def(kind, id);
                clearActive(p, kind);
                msg(p, "&cTwoje " + noun(kind) + (c != null ? " &f" + c.name : "") + " &cwygasło.");
            }
        }
    }
}
