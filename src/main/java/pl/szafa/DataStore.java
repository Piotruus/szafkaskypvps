package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Prosty zapis do data.yml (tylko aktywne zwierzatko/kostium gracza). */
public final class DataStore {

    private final SzafaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private boolean dirty;

    public DataStore(SzafaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            PlayerData d = new PlayerData();
            d.petId = s.getString("pet.id");
            d.petExpiry = s.getLong("pet.expiry");
            d.costumeId = s.getString("costume.id");
            d.costumeExpiry = s.getLong("costume.expiry");
            players.put(id, d);
        }
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    public void markDirty() {
        dirty = true;
    }

    public void flushAsyncIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        String text = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(text));
    }

    public void flushNow() {
        dirty = false;
        write(snapshot());
    }

    private String snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            PlayerData d = e.getValue();
            if (d.petId == null && d.costumeId == null) {
                continue;
            }
            String base = "players." + e.getKey();
            if (d.petId != null) {
                yaml.set(base + ".pet.id", d.petId);
                yaml.set(base + ".pet.expiry", d.petExpiry);
            }
            if (d.costumeId != null) {
                yaml.set(base + ".costume.id", d.costumeId);
                yaml.set(base + ".costume.expiry", d.costumeExpiry);
            }
        }
        return yaml.saveToString();
    }

    private synchronized void write(String text) {
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udalo sie zapisac data.yml: " + ex.getMessage());
        }
    }
}
