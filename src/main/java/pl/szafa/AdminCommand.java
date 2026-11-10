package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /szafaadmin give   <gracz> <pet|kostium> <id> <dni|perm> [ilosc]  - daje ITEM do ekwipunku
 * /szafaadmin scroll <gracz> <dni> [ilosc]                          - daje Zwoj przedluzenia
 * /szafaadmin grant  <gracz> <pet|kostium> <id> <dni|perm>          - zaklada od razu (bez itemu)
 * /szafaadmin take   <gracz> <pet|kostium>                          - zabiera aktywne (bez zwrotu itemu)
 * /szafaadmin list   <gracz>
 * /szafaadmin reload
 * Dziala tez z konsoli (np. nagrody ze skrzynek).
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final SzafaPlugin plugin;

    public AdminCommand(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    private static Kind parseKind(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "pet", "zwierzatko", "zwierzątko" -> Kind.PET;
            case "kostium", "costume" -> Kind.COSTUME;
            default -> null;
        };
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private void usage(CommandSender s) {
        plugin.msg(s, "&e/szafaadmin give <gracz> <pet|kostium> <id> <dni|perm> [ilość]");
        plugin.msg(s, "&e/szafaadmin scroll <gracz> <dni> [ilość]");
        plugin.msg(s, "&e/szafaadmin grant <gracz> <pet|kostium> <id> <dni|perm>");
        plugin.msg(s, "&e/szafaadmin take <gracz> <pet|kostium>");
        plugin.msg(s, "&e/szafaadmin list <gracz>");
        plugin.msg(s, "&e/szafaadmin reload");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("szafa.admin")) {
            plugin.msg(sender, "&cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.msg(sender, "&aPrzeładowano konfigurację.");
            }
            case "list" -> list(sender, args);
            case "give" -> give(sender, args);
            case "scroll" -> scroll(sender, args);
            case "grant" -> grant(sender, args);
            case "take" -> take(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------ podkomendy

    private void list(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            plugin.msg(sender, "&cNie znaleziono gracza.");
            return;
        }
        PlayerData d = plugin.data().get(target.getUniqueId());
        plugin.msg(sender, "&fAktywne u gracza &e" + args[1] + "&f:");
        for (Kind kind : Kind.values()) {
            String id = SzafaPlugin.activeId(d, kind);
            if (id == null) {
                plugin.msg(sender, "&8- " + SzafaPlugin.noun(kind) + ": brak");
            } else {
                long exp = SzafaPlugin.activeExpiry(d, kind);
                plugin.msg(sender, "&8- " + SzafaPlugin.noun(kind) + ": &f" + id + " &7do &f"
                        + plugin.format(exp) + " &8(" + plugin.remaining(exp) + ")");
            }
        }
    }

    /** Zwraca dni (0 = bez limitu) albo -1 przy bledzie. */
    private int parseDays(CommandSender sender, String raw) {
        if (raw.equalsIgnoreCase("perm")) {
            return 0;
        }
        try {
            int d = Integer.parseInt(raw);
            if (d > 0) {
                return d;
            }
        } catch (NumberFormatException ignored) {
            // spadamy do komunikatu
        }
        plugin.msg(sender, "&cPodaj liczbę dni (większą od 0) albo 'perm'.");
        return -1;
    }

    private int parseAmount(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(args[index])));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 5) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Kind kind = parseKind(args[2]);
        if (target == null) {
            plugin.msg(sender, "&cGracz musi być online (item trafia do ekwipunku).");
            return;
        }
        if (kind == null) {
            plugin.msg(sender, "&cTyp: pet albo kostium.");
            return;
        }
        Cosmetic c = plugin.def(kind, args[3]);
        if (c == null) {
            plugin.msg(sender, "&cNie ma takiego id: &f" + args[3]);
            return;
        }
        int days = parseDays(sender, args[4]);
        if (days < 0) {
            return;
        }
        int amount = parseAmount(args, 5);
        plugin.giveItem(target, plugin.items().cosmeticItem(c, days, amount));
        plugin.msg(sender, "&aDano &f" + amount + "x " + c.name + " &agraczowi &f" + target.getName()
                + " &7(" + (days == 0 ? "bez limitu" : days + " dni od pierwszego założenia") + ")");
        plugin.msg(target, "&aOtrzymałeś item: &f" + c.name + "&a. Weź go do ręki i kliknij &fPPM&a.");
    }

    private void scroll(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.msg(sender, "&cGracz musi być online.");
            return;
        }
        int days = parseDays(sender, args[2]);
        if (days <= 0) {
            if (days == 0) {
                plugin.msg(sender, "&cZwój musi mieć konkretną liczbę dni.");
            }
            return;
        }
        int amount = parseAmount(args, 3);
        plugin.giveItem(target, plugin.items().scrollItem(days, amount));
        plugin.msg(sender, "&aDano &f" + amount + "x Zwój przedłużenia (" + days + " dni) &agraczowi &f"
                + target.getName() + "&a.");
        plugin.msg(target, "&aOtrzymałeś &6Zwój przedłużenia &7(" + days + " dni)&a. "
                + "Przeciągnij go na item albo użyj w &f/szafa&a.");
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length < 5) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Kind kind = parseKind(args[2]);
        if (target == null) {
            plugin.msg(sender, "&cGracz musi być online.");
            return;
        }
        if (kind == null) {
            plugin.msg(sender, "&cTyp: pet albo kostium.");
            return;
        }
        Cosmetic c = plugin.def(kind, args[3]);
        if (c == null) {
            plugin.msg(sender, "&cNie ma takiego id: &f" + args[3]);
            return;
        }
        int days = parseDays(sender, args[4]);
        if (days < 0) {
            return;
        }
        long exp = plugin.grantNow(target, c, days == 0 ? null : days);
        plugin.msg(sender, "&aZałożono graczowi &f" + target.getName() + " &a" + c.name
                + " &7(do " + plugin.format(exp) + ")");
        plugin.msg(target, "&aZałożono: &f" + c.name + " &7(do " + plugin.format(exp) + ")");
    }

    private void take(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        Kind kind = parseKind(args[2]);
        if (target == null || kind == null) {
            plugin.msg(sender, "&cZły gracz lub typ (pet / kostium).");
            return;
        }
        boolean had = plugin.takeActive(target.getUniqueId(), kind);
        plugin.msg(sender, had ? "&aZabrano aktywne " + SzafaPlugin.noun(kind) + "."
                : "&cGracz nie miał aktywnego " + SzafaPlugin.noun(kind) + ".");
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("szafa.admin")) {
            return out;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean withId = sub.equals("give") || sub.equals("grant");
        switch (args.length) {
            case 1 -> out.addAll(List.of("give", "scroll", "grant", "take", "list", "reload"));
            case 2 -> {
                if (!sub.equals("reload")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        out.add(p.getName());
                    }
                }
            }
            case 3 -> {
                if (withId || sub.equals("take")) {
                    out.addAll(List.of("pet", "kostium"));
                } else if (sub.equals("scroll")) {
                    out.addAll(List.of("7", "14", "30"));
                }
            }
            case 4 -> {
                Kind kind = parseKind(args[2]);
                if (withId && kind != null) {
                    for (Cosmetic c : plugin.defs(kind)) {
                        out.add(c.id);
                    }
                }
            }
            case 5 -> {
                if (withId) {
                    out.addAll(List.of("7", "14", "30", "perm"));
                }
            }
            default -> {
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
