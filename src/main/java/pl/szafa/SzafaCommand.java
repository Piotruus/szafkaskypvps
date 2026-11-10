package pl.szafa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SzafaCommand implements CommandExecutor {

    private final SzafaPlugin plugin;

    public SzafaCommand(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }
        plugin.menus().openMain(p);
        return true;
    }
}
