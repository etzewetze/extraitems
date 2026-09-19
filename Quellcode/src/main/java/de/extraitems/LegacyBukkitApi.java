package de.extraitems;

import org.bukkit.ChatColor;
import org.bukkit.Nameable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Compatibility boundary for legacy String text methods.
 *
 * <p>Paper 26.3 marks these methods as deprecated in favour of Adventure components, while the
 * supported Spigot 1.21.11 API does not expose Paper's component overloads. Keeping the calls in
 * this one class prevents Paper-only linkage from leaking into the Java-21 release JAR.</p>
 */
@SuppressWarnings({"deprecation", "removal"})
final class LegacyBukkitApi {
    private LegacyBukkitApi() {}

    static void displayName(ItemMeta meta, String value) { meta.setDisplayName(value); }

    static List<String> lore(ItemMeta meta) { return meta.getLore(); }

    static void lore(ItemMeta meta, List<String> value) { meta.setLore(value); }

    static void customName(Nameable target, String value) { target.setCustomName(value); }

    static void kick(Player player, String message) { player.kickPlayer(message); }

    static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
}
