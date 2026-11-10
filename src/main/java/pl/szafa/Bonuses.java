package pl.szafa;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Bonusy zwierzatka albo kostiumu (czytane z config.yml). */
public final class Bonuses {

    public record EffectSpec(PotionEffectType type, int amplifier, int ticks) {
    }

    public final double damagePercent;
    public final double endDamagePercent;
    public final double pvpDamagePercent;
    public final double pearlDropMultiplier;
    public final double pearlRefundChance;
    public final double fallDamageReduction;
    public final double damageReduction;
    public final List<EffectSpec> effects;
    public final List<EffectSpec> killEffects;

    private Bonuses(ConfigurationSection s, Logger log) {
        if (s == null) {
            damagePercent = 0;
            endDamagePercent = 0;
            pvpDamagePercent = 0;
            pearlDropMultiplier = 1;
            pearlRefundChance = 0;
            fallDamageReduction = 0;
            damageReduction = 0;
            effects = List.of();
            killEffects = List.of();
            return;
        }
        damagePercent = s.getDouble("damage-percent", 0);
        endDamagePercent = s.getDouble("end-damage-percent", 0);
        pvpDamagePercent = s.getDouble("pvp-damage-percent", 0);
        pearlDropMultiplier = s.getDouble("pearl-drop-multiplier", 1);
        pearlRefundChance = s.getDouble("pearl-refund-chance", 0);
        fallDamageReduction = s.getDouble("fall-damage-reduction", 0);
        damageReduction = s.getDouble("damage-reduction", 0);
        effects = parseEffects(s.getStringList("effects"), log);
        killEffects = parseEffects(s.getStringList("kill-effects"), log);
    }

    public static Bonuses parse(ConfigurationSection s, Logger log) {
        return new Bonuses(s, log);
    }

    private static List<EffectSpec> parseEffects(List<String> raw, Logger log) {
        List<EffectSpec> out = new ArrayList<>();
        for (String line : raw) {
            String[] p = line.split(":");
            try {
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(
                        NamespacedKey.minecraft(p[0].trim().toLowerCase(Locale.ROOT)));
                if (type == null) {
                    log.warning("Nieznany efekt: " + line);
                    continue;
                }
                int amp = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
                int ticks = p.length > 2 ? Integer.parseInt(p[2].trim()) : 100;
                out.add(new EffectSpec(type, amp, ticks));
            } catch (NumberFormatException ex) {
                log.warning("Zly format efektu: " + line);
            }
        }
        return out;
    }
}
