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
    PackGate(ExtraItemsPlugin plugin, PackService pack) { this.plugin = plugin; this.pack = pack; }
    boolean ready(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.state() == PackSession.State.LOADED;
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) { request(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
    void request(Player player) {
        var pending = sessions.get(player.getUniqueId());
        if (pending != null && pending.state() == PackSession.State.WAITING) {
            player.sendMessage("§eDas Ressourcenpaket wird bereits angefordert. Bitte den Download bestätigen.");
            return; // A command must not extend the join deadline indefinitely.
        }
        PackSession session = new PackSession(UUID.randomUUID());
        var previous = sessions.put(player.getUniqueId(), session);
        if (previous != null) player.removeResourcePack(previous.id);
        if (!pack.ready() || !plugin.operational()) {
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) kick(player, "pack-unavailable"); });
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!current(player, session)) return;
            try {
                player.addResourcePack(session.id, pack.url(), pack.hash(), plugin.getConfig().getString("resource-pack.prompt"), true);
            } catch (RuntimeException e) { kick(player, "pack-error"); }
        }, 10L);
        long timeout = Math.max(15, Math.min(600, plugin.getConfig().getInt("resource-pack.timeout-seconds", 120))) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (current(player, session) && session.timeout()) kick(player, "pack-timeout");
        }, timeout);
    }
    private boolean current(Player player, PackSession session) { return player.isOnline() && sessions.get(player.getUniqueId()) == session; }
    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.accept(event.getID(), event.getStatus().name())) return;
        if (session.state() == PackSession.State.LOADED) {
            plugin.message(event.getPlayer(), "pack-loaded");
            if (plugin.items() != null) event.getPlayer().discoverRecipes(plugin.items().recipeKeys());
        } else kick(event.getPlayer(), event.getStatus().name().equals("DECLINED") ? "pack-required" : "pack-error");
    }
    private void kick(Player p, String key) { p.kickPlayer(plugin.text(key)); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (!ready(e.getPlayer()) && e.getTo() != null && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ())) {
            var at = e.getFrom().clone(); at.setYaw(e.getTo().getYaw()); at.setPitch(e.getTo().getPitch()); e.setTo(at);
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
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && !ready(p)) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player p && !ready(p)) e.setCancelled(true);
    }
}
