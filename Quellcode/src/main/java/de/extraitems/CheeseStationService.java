package de.extraitems;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Barrel-backed cheese stations: real inventories make vanilla hoppers reliable. */
final class CheeseStationService implements Listener {
    private static final int INPUT = 10, PROGRESS = 13, CHEESE = 15, BUCKET = 16;
    private static final List<Integer> PROGRESS_BAR = List.of(2, 3, 4, 5, 6);
    private static final Set<Integer> OPEN_SLOTS = Set.of(INPUT, PROGRESS, CHEESE, BUCKET);
    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final NamespacedKey stationKey, readyKey, fillerKey, displayKey;
    private final Map<String, Block> machines = new HashMap<>();
    private BukkitTask ticker;

    CheeseStationService(ExtraItemsPlugin plugin, ItemRegistry items) {
        this.plugin = plugin; this.items = items;
        stationKey = new NamespacedKey(plugin, "station_id");
        readyKey = new NamespacedKey(plugin, "station_ready_at");
        fillerKey = new NamespacedKey(plugin, "station_filler");
        displayKey = new NamespacedKey(plugin, "station_display");
    }

    void start() {
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) discover(chunk);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    void stop() {
        if (ticker != null) ticker.cancel();
        machines.clear();
    }

    int count() { return machines.size(); }

    private String position(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private ItemRegistry.Station station(Block block) {
        if (!(block.getState() instanceof Barrel barrel)) return null;
        String id = barrel.getPersistentDataContainer().get(stationKey, PersistentDataType.STRING);
        return id == null ? null : items.station(id);
    }

    private ItemRegistry.Station station(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Barrel barrel ? station(barrel.getBlock()) : null;
    }

    private void discover(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Barrel barrel && station(barrel.getBlock()) != null) register(barrel.getBlock());
        }
    }

    private void register(Block block) {
        machines.put(position(block), block);
        normalize(block);
        ensureDisplay(block);
    }

    private void normalize(Block block) {
        if (!(block.getState() instanceof Barrel barrel)) return;
        Inventory inventory = barrel.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (OPEN_SLOTS.contains(slot)) continue;
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) inventory.setItem(slot, filler());
        }
        long ready = barrel.getPersistentDataContainer().getOrDefault(readyKey, PersistentDataType.LONG, 0L);
        renderGui(inventory, ready, station(block));
    }

    private ItemStack filler() { return decoration(Material.BLACK_STAINED_GLASS_PANE, "§8Käsestation", List.of()); }

    private ItemStack decoration(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(fillerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack progress(long readyAt, ItemRegistry.Station station) {
        if (readyAt <= 0 || station == null) return decoration(Material.LIME_DYE, "§a§lBereit",
                List.of("§7Lege links einen Milcheimer ein.", "§7Rechts erscheinen Käse und Eimer."));
        long seconds = Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
        int percent = progressPercent(readyAt, station.processSeconds());
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(seconds == 0 ? "§6§lAusgabe wird vorbereitet" : "§e§lKäse reift: " + percent + "%");
        meta.setLore(List.of("§7Verbleibend: §f" + seconds + " Sekunden", "§8Fortschritt wird jede Sekunde aktualisiert."));
        meta.getPersistentDataContainer().set(fillerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private int progressPercent(long readyAt, int processSeconds) {
        if (readyAt <= 0) return 0;
        long remaining = Math.max(0, readyAt - System.currentTimeMillis());
        return Math.max(0, Math.min(100, (int) (100 - remaining * 100 / (processSeconds * 1000L))));
    }

    private void renderGui(Inventory inventory, long readyAt, ItemRegistry.Station station) {
        int percent = station == null ? 0 : progressPercent(readyAt, station.processSeconds());
        int lit = readyAt <= 0 ? 0 : (percent + 19) / 20;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (OPEN_SLOTS.contains(slot)) continue;
            ItemStack current = inventory.getItem(slot);
            if (current != null && !current.getType().isAir() && !filler(current)) continue;
            if (PROGRESS_BAR.contains(slot)) {
                int segment = PROGRESS_BAR.indexOf(slot) + 1;
                inventory.setItem(slot, decoration(segment <= lit ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                        readyAt <= 0 ? "§7Fortschritt" : "§aFortschritt: " + percent + "%", List.of()));
            } else if (slot == 9 || slot == 11) {
                inventory.setItem(slot, decoration(Material.CYAN_STAINED_GLASS_PANE, "§bEingang",
                        List.of("§7Milcheimer in Feld 11")));
            } else if (slot == 14 || slot == 17) {
                inventory.setItem(slot, decoration(Material.YELLOW_STAINED_GLASS_PANE, "§eAusgänge",
                        List.of("§7Käserad und leerer Eimer")));
            } else inventory.setItem(slot, filler());
        }
        inventory.setItem(PROGRESS, progress(readyAt, station));
    }

    private boolean filler(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(fillerKey, PersistentDataType.BYTE);
    }

    private void ensureDisplay(Block block) {
        String position = position(block);
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(.5, 1.15, .5), .7, .7, .7)) {
            if (position.equals(entity.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING))) return;
        }
        block.getWorld().spawn(block.getLocation().add(.5, 1.15, .5), ItemDisplay.class, display -> {
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, position);
            display.setItemStack(items.create("cheese_station", 1));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true); display.setInvulnerable(true); display.setGravity(false);
            display.setDisplayWidth(1); display.setDisplayHeight(1); display.setViewRange(.8f);
        });
    }

    private void removeDisplay(Block block) {
        String position = position(block);
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(.5, 1.15, .5), 1, 1, 1)) {
            if (position.equals(entity.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING))) entity.remove();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Block block : new ArrayList<>(machines.values())) {
            ItemRegistry.Station definition = station(block);
            if (definition == null) { machines.remove(position(block)); removeDisplay(block); continue; }
            if (!(block.getState() instanceof Barrel barrel)) continue;
            Inventory inventory = barrel.getInventory();
            pullMilk(block, inventory, definition.input());
            pushOutput(block, inventory, CHEESE);
            pushOutput(block, inventory, BUCKET);
            long ready = barrel.getPersistentDataContainer().getOrDefault(readyKey, PersistentDataType.LONG, 0L);
            if (ready <= 0 && has(inventory.getItem(INPUT), definition.input())) {
                takeOne(inventory, INPUT);
                ready = now + definition.processSeconds() * 1000L;
                barrel.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG, ready);
                barrel.update(true, false);
                block.getWorld().playSound(block.getLocation(), Sound.ITEM_BUCKET_EMPTY, .7f, .9f);
            } else if (ready > 0 && now >= ready && fits(inventory.getItem(CHEESE), items.create(definition.output(), 1))
                    && fits(inventory.getItem(BUCKET), new ItemStack(definition.byproduct()))) {
                add(inventory, CHEESE, items.create(definition.output(), 1));
                add(inventory, BUCKET, new ItemStack(definition.byproduct()));
                ready = 0;
                barrel.getPersistentDataContainer().remove(readyKey);
                barrel.update(true, false);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, .9f, .8f);
                block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(.5, 1.1, .5), 8, .25, .1, .25, .01);
            }
            renderGui(inventory, ready, definition);
            ensureDisplay(block);
        }
    }

    private boolean has(ItemStack item, Material material) { return item != null && item.getType() == material && item.getAmount() > 0; }
    private void takeOne(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getAmount() <= 1) inventory.setItem(slot, null);
        else { item.setAmount(item.getAmount() - 1); inventory.setItem(slot, item); }
    }
    private boolean fits(ItemStack current, ItemStack added) {
        return current == null || current.getType().isAir()
                || (current.isSimilar(added) && current.getAmount() < current.getMaxStackSize());
    }
    private void add(Inventory inventory, int slot, ItemStack added) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) inventory.setItem(slot, added);
        else { current.setAmount(current.getAmount() + added.getAmount()); inventory.setItem(slot, current); }
    }

    private void pullMilk(Block station, Inventory target, Material input) {
        ItemStack current = target.getItem(INPUT);
        if (current != null && !current.getType().isAir()) return;
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (!(station.getRelative(face).getState() instanceof Hopper hopper)) continue;
            Inventory source = hopper.getInventory();
            for (int slot = 0; slot < source.getSize(); slot++) {
                ItemStack stack = source.getItem(slot);
                if (stack != null && stack.getType() == input) {
                    ItemStack one = stack.clone(); one.setAmount(1); target.setItem(INPUT, one);
                    if (stack.getAmount() <= 1) source.setItem(slot, null);
                    else { stack.setAmount(stack.getAmount() - 1); source.setItem(slot, stack); }
                    return;
                }
            }
        }
    }

    private void pushOutput(Block station, Inventory source, int slot) {
        if (!(station.getRelative(BlockFace.DOWN).getState() instanceof Hopper hopper)) return;
        ItemStack stack = source.getItem(slot);
        if (stack == null || stack.getType().isAir()) return;
        ItemStack one = stack.clone(); one.setAmount(1);
        if (!hopper.getInventory().addItem(one).isEmpty()) return;
        if (stack.getAmount() <= 1) source.setItem(slot, null);
        else { stack.setAmount(stack.getAmount() - 1); source.setItem(slot, stack); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        ItemRegistry.Station definition = items.stationForItem(event.getItemInHand());
        if (definition == null || !(event.getBlockPlaced().getState() instanceof Barrel barrel)) return;
        barrel.getPersistentDataContainer().set(stationKey, PersistentDataType.STRING, definition.id());
        barrel.setCustomName("§3§lKäsestation §8• §fKäserei");
        barrel.update(true, false);
        register(event.getBlockPlaced());
        event.getBlockPlaced().getWorld().playSound(event.getBlockPlaced().getLocation(), Sound.BLOCK_BARREL_OPEN, .8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void use(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemRegistry.Station definition = station(event.getClickedBlock());
        if (definition != null && !event.getPlayer().hasPermission(definition.permission())) {
            event.setCancelled(true); plugin.message(event.getPlayer(), "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        ItemRegistry.Station definition = station(event.getView().getTopInventory());
        if (definition == null) return;
        if (event.isShiftClick() || event.getClick().isKeyboardClick()
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) { event.setCancelled(true); return; }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        if (slot == PROGRESS || !OPEN_SLOTS.contains(slot)) { event.setCancelled(true); return; }
        ItemStack cursor = event.getCursor();
        if ((slot == CHEESE || slot == BUCKET) && cursor != null && !cursor.getType().isAir()) event.setCancelled(true);
        if (slot == INPUT && cursor != null && !cursor.getType().isAir() && cursor.getType() != definition.input()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        ItemRegistry.Station definition = station(event.getView().getTopInventory());
        if (definition == null) return;
        int size = event.getView().getTopInventory().getSize();
        boolean top = event.getRawSlots().stream().anyMatch(slot -> slot < size);
        if (top && (event.getRawSlots().stream().anyMatch(slot -> slot < size && slot != INPUT)
                || event.getOldCursor().getType() != definition.input())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent event) {
        ItemRegistry.Station destination = station(event.getDestination());
        if (destination != null) event.setCancelled(true); // tick() routes input to the dedicated slot.
        ItemRegistry.Station source = station(event.getSource());
        if (source != null) event.setCancelled(true); // tick() routes only the two output slots downward.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakStation(BlockBreakEvent event) {
        if (station(event.getBlock()) == null || !(event.getBlock().getState() instanceof Barrel barrel)) return;
        event.setDropItems(false);
        for (int slot : List.of(INPUT, CHEESE, BUCKET)) {
            ItemStack item = barrel.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir() && !filler(item)) event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
        }
        barrel.getInventory().clear();
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), items.create("cheese_station", 1));
        }
        machines.remove(position(event.getBlock()));
        removeDisplay(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkLoad(ChunkLoadEvent event) { Bukkit.getScheduler().runTask(plugin, () -> discover(event.getChunk())); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) { cleanExplosion(event.blockList()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityExplode(EntityExplodeEvent event) { cleanExplosion(event.blockList()); }
    private void cleanExplosion(List<Block> blocks) {
        for (Block block : blocks) if (station(block) != null) {
            machines.remove(position(block)); removeDisplay(block);
        }
    }
}
