package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Modern permission nodes explicitly deny legacy grants. */
public final class PermissionService {
    public boolean has(CommandSender sender, String modern, String legacy) {
        if (sender == null) return false;
        if (sender.isPermissionSet(modern) && !sender.hasPermission(modern)) return false;
        return sender.hasPermission(modern) || (legacy != null && sender.hasPermission(legacy));
    }

    public boolean has(CommandSender sender, String modern) { return has(sender, modern, null); }
    public boolean isPlayer(CommandSender sender) { return sender instanceof Player; }
}
