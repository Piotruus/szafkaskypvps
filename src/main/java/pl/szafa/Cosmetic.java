package pl.szafa;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.logging.Logger;

/** Definicja zwierzatka albo kostiumu. */
public final class Cosmetic {

    public enum Kind {PET, COSTUME}

    public final Kind kind;
    public final String id;
    public final String name;
    public final String rarity;
    public final List<String> description;
    /** Model ikony (pet: model glowy, kostium: ikona napiersnika). */
    public final NamespacedKey itemModel;
    /** Tylko zwierzatko: pelny model (cale zwierzatko) latajacy za graczem. */
    public final NamespacedKey worldModel;
    /** Tylko kostium: wyglad zbroi na postaci. */
    public final NamespacedKey armorModel;
    /** Tylko kostium (opcjonalnie): model 3D na glowie (rogi, ryj, uszy...). */
    public final NamespacedKey headModel;
    public final Bonuses bonuses;

    private Cosmetic(Kind kind, String id, ConfigurationSection s, Logger log) {
        this.kind = kind;
        this.id = id;
        this.name = s.getString("name", id);
        this.rarity = s.getString("rarity", "");
        this.description = s.getStringList("description");
        String modelKey = kind == Kind.PET ? "model" : "icon-model";
        this.itemModel = key(s.getString(modelKey, ""));
        this.worldModel = kind == Kind.PET ? key(s.getString("world-model", "")) : null;
        this.armorModel = kind == Kind.COSTUME ? key(s.getString("armor-model", "")) : null;
        this.headModel = kind == Kind.COSTUME ? key(s.getString("head-model", "")) : null;
        this.bonuses = Bonuses.parse(s.getConfigurationSection("bonuses"), log);
        if (itemModel == null) {
            log.warning(kind + " " + id + ": brak poprawnego '" + modelKey + "'");
        }
        if (kind == Kind.COSTUME && armorModel == null) {
            log.warning("KOSTIUM " + id + ": brak poprawnego 'armor-model'");
        }
    }

    private static NamespacedKey key(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NamespacedKey.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static Cosmetic load(Kind kind, String id, ConfigurationSection s, Logger log) {
        return new Cosmetic(kind, id, s, log);
    }

    public String noun() {
        return kind == Kind.PET ? "zwierzątko" : "kostium";
    }
}
