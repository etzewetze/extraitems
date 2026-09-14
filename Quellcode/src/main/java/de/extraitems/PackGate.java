package de.extraitems;

import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import java.util.*;

final class PackGate implements Listener {
    private final ExtraItemsPlugin plugin;
    private final PackService pack;
    private final Map<UUID, PackSession> sessions = new HashMap<>();
    private final Map<UUID, String> joiningAddresses = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();

    PackGate(ExtraItemsPlugin plugin, PackService pack) {
        this.plugin = plugin;
        this.pack = pack;
    }

    boolean ready(Player player) {
        if (bypass.contains(player.getUniqueId()) || !pack.required()) return true;
        PackSession session = sessions.get(player.getUniqueId());
        return session != null && session.state() == PackSession.State.LOADED;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void login(PlayerLoginEvent event) {
        joiningAddresses.put(event.getPlayer().getUniqueId(), event.getHostname());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) { request(event.getPlayer()); }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        joiningAddresses.remove(id);
        bypass.remove(id);
    }

    void request(Player player) {
        bypass.remove(player.getUniqueId());
        PackSession pending = sessions.get(player.getUniqueId());
        if (pending != null && pending.state() == PackSession.State.WAITING) {
            player.sendMessage("§eDas Ressourcenpaket wird bereits angefordert. Bitte den Download bestätigen.");
            return;
        }

        PackSession session = new PackSession(UUID.randomUUID());
        PackSession previous = sessions.put(player.getUniqueId(), session);
        if (previous != null) player.removeResourcePack(previous.id);

        if (!plugin.operational() || !pack.ready()) {
            emergencyOrKick(player);
            return;
        }
        if (!pack.deliveryEnabled()) {
            bypass.add(player.getUniqueId());
            plugin.message(player, "pack-optional");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!current(player, session)) return;
            try {
                player.addResourcePack(session.id, pack.url(joiningAddresses.get(player.getUniqueId())),
                        pack.hash(), plugin.getConfig().getString("resource-pack.prompt"), pack.required());
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Pack-URL für " + player.getName() + " fehlgeschlagen: " + error.getMessage());
                emergencyOrKick(player);
            }
        }, 10L);

        long timeout = Math.max(15, Math.min(600,
                plugin.getConfig().getInt("resource-pack.timeout-seconds", 120))) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (current(player, session) && session.timeout()) {
                if (pack.required()) kick(player, "pack-timeout");
                else allowOptional(player);
            }
        }, timeout);
    }

    String resolvedUrl(Player player) {
        return pack.url(joiningAddresses.get(player.getUniqueId()));
    }

    private void emergencyOrKick(Player player) {
        boolean adminBypass = plugin.getConfig().getBoolean(
                "resource-pack.allow-admin-bypass-on-error", true)
                && player.hasPermission("extraitems.admin");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (adminBypass) {
                bypass.add(player.getUniqueId());
                plugin.message(player, "pack-admin-bypass");
            } else if (pack.required()) {
                kick(player, "pack-unavailable");
            } else {
                allowOptional(player);
            }
        });
    }

    private void allowOptional(Player player) {
        bypass.add(player.getUniqueId());
        plugin.message(player, "pack-optional");
    }

    private boolean current(Player player, PackSession session) {
        return player.isOnline() && sessions.get(player.getUniqueId()) == session;
    }

    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        PackSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.accept(event.getID(), event.getStatus().name())) return;
        if (session.state() == PackSession.State.LOADED) {
            plugin.message(event.getPlayer(), "pack-loaded");
            if (plugin.items() != null) event.getPlayer().discoverRecipes(plugin.items().recipeKeys());
        } else if (pack.required()) {
            kick(event.getPlayer(), event.getStatus().name().equals("DECLINED") ? "pack-required" : "pack-error");
        } else {
            allowOptional(event.getPlayer());
        }
    }

    private void kick(Player player, String key) { player.kickPlayer(plugin.text(key)); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (!ready(event.getPlayer()) && event.getTo() != null
                && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ())) {
            var at = event.getFrom().clone();
            at.setYaw(event.getTo().getYaw());
            at.setPitch(event.getTo().getPitch());
            event.setTo(at);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEvent e) { if (!ready(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void interactEntity(PlayerInteractEntityEvent e) { if (!ready(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void place(BlockPlaceEvent e) { if (!ready(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e) { if (!ready(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && !ready(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p && !ready(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if (!ready(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && !ready(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void consume(PlayerItemConsumeEvent e) { if (!ready(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !ready(player)) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player player && !ready(player)) {
            event.setCancelled(true);
        }
    }
}
