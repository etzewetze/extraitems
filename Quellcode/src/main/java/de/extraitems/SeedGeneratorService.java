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

/** Barrel-backed, hopper-compatible drying machine for produce-to-seed conversions. */
final class SeedGeneratorService implements Listener {
    private static final int INPUT = 10, PROGRESS = 13, OUTPUT = 16;
    private static final List<Integer> PROGRESS_BAR = List.of(2, 3, 4, 5, 6);
    private static final Set<Integer> OPEN_SLOTS = Set.of(INPUT, PROGRESS, OUTPUT);
    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final NamespacedKey generatorKey, readyKey, inputKey, fillerKey, displayKey;
    private final Map<String, Block> machines = new HashMap<>();
    private BukkitTask ticker;

    SeedGeneratorService(ExtraItemsPlugin plugin, ItemRegistry items) {
        this.plugin = plugin;
        this.items = items;
        generatorKey = new NamespacedKey(plugin, "seed_generator_id");
        readyKey = new NamespacedKey(plugin, "seed_generator_ready_at");
        inputKey = new NamespacedKey(plugin, "seed_generator_input");
        fillerKey = new NamespacedKey(plugin, "seed_generator_filler");
        displayKey = new NamespacedKey(plugin, "seed_generator_display");
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

    private ItemRegistry.SeedGenerator generator(Block block) {
        if (!(block.getState() instanceof Barrel barrel)) return null;
        String id = barrel.getPersistentDataContainer().get(generatorKey, PersistentDataType.STRING);
        return id == null ? null : items.seedGenerator(id);
    }

    private ItemRegistry.SeedGenerator generator(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Barrel barrel
                ? generator(barrel.getBlock()) : null;
    }

    private void discover(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Barrel barrel && generator(barrel.getBlock()) != null) register(barrel.getBlock());
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
        renderGui(inventory, ready, generator(block));
    }

    private ItemStack filler() {
        return decoration(Material.BLACK_STAINED_GLASS_PANE, "§8Samengenerator", List.of());
    }

    private ItemStack decoration(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(fillerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack progress(long readyAt, ItemRegistry.SeedGenerator generator, boolean outputBlocked) {
        if (readyAt <= 0 || generator == null) return decoration(Material.WHEAT_SEEDS, "§a§lBereit",
                List.of("§7Lege Tomate, Salat oder Zwiebel ein.", "§7Das Gemüse bleibt bis zum Abschluss liegen."));
        if (outputBlocked) return decoration(Material.RED_DYE, "§c§lAusgang voll",
                List.of("§7Nimm zuerst die fertigen Samen heraus.", "§7Das Gemüse bleibt im Eingang erhalten."));
        long seconds = Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
        int percent = progressPercent(readyAt, generator.processSeconds());
        return decoration(Material.CLOCK, seconds == 0 ? "§6§lSamen werden ausgegeben"
                        : "§e§lTrocknung: " + percent + "%",
                List.of("§7Verbleibend: §f" + seconds + " Sekunden",
                        "§7Herausnehmen oder Austauschen bricht ab."));
    }

    private int progressPercent(long readyAt, int processSeconds) {
        if (readyAt <= 0) return 0;
        long remaining = Math.max(0, readyAt - System.currentTimeMillis());
        return Math.max(0, Math.min(100, (int) (100 - remaining * 100 / (processSeconds * 1000L))));
    }

    private void renderGui(Inventory inventory, long readyAt, ItemRegistry.SeedGenerator generator) {
        int percent = generator == null ? 0 : progressPercent(readyAt, generator.processSeconds());
        int lit = readyAt <= 0 ? 0 : (percent + 19) / 20;
        boolean outputBlocked = false;
        String active = activeInput(inventory);
        ItemRegistry.SeedConversion conversion = generator == null ? null : generator.conversion(active);
        if (readyAt > 0 && readyAt <= System.currentTimeMillis() && conversion != null) {
            outputBlocked = !fits(inventory.getItem(OUTPUT), items.create(conversion.output(), conversion.amount()));
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (OPEN_SLOTS.contains(slot)) continue;
            ItemStack current = inventory.getItem(slot);
            if (current != null && !current.getType().isAir() && !filler(current)) continue;
            if (PROGRESS_BAR.contains(slot)) {
                int segment = PROGRESS_BAR.indexOf(slot) + 1;
                inventory.setItem(slot, decoration(segment <= lit ? Material.LIME_STAINED_GLASS_PANE
                                : Material.GRAY_STAINED_GLASS_PANE,
                        readyAt <= 0 ? "§7Fortschritt" : "§aFortschritt: " + percent + "%", List.of()));
            } else if (slot == 9 || slot == 11) {
                inventory.setItem(slot, decoration(Material.GREEN_STAINED_GLASS_PANE, "§aTrocknungseingang",
                        List.of("§7Tomate, Salat oder Zwiebel")));
            } else if (slot == 15 || slot == 17) {
                inventory.setItem(slot, decoration(Material.YELLOW_STAINED_GLASS_PANE, "§eSamenausgang",
                        List.of("§7Fertige Samen erscheinen rechts.")));
            } else inventory.setItem(slot, filler());
        }
        inventory.setItem(PROGRESS, progress(readyAt, generator, outputBlocked));
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
        ItemRegistry.SeedGenerator definition = generator(block);
        if (definition == null) return;
        block.getWorld().spawn(block.getLocation().add(.5, 1.15, .5), ItemDisplay.class, display -> {
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, position);
            display.setItemStack(items.create(definition.item(), 1));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setDisplayWidth(1);
            display.setDisplayHeight(1);
            display.setViewRange(.8f);
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
            ItemRegistry.SeedGenerator definition = generator(block);
            if (definition == null) {
                machines.remove(position(block));
                removeDisplay(block);
                continue;
            }
            Inventory inventory = liveInventory(block);
            pullInput(block, inventory, definition);
            inventory = liveInventory(block);

            Cycle cycle = currentCycle(block);
            String currentInput = activeInput(inventory);
            String expectedInput = cycle.inputId() == null ? currentInput : cycle.inputId();
            ItemRegistry.SeedConversion conversion = definition.conversion(expectedInput);
            boolean inputPresent = conversion != null && expectedInput.equals(currentInput);
            ItemStack result = conversion == null ? null : items.create(conversion.output(), conversion.amount());
            boolean outputFits = result != null && fits(inventory.getItem(OUTPUT), result);

            MachineCycle.Decision decision = MachineCycle.decide(cycle.readyAt(), now, inputPresent, outputFits);
            long ready = cycle.readyAt();
            if (decision == MachineCycle.Decision.START) {
                ready = now + definition.processSeconds() * 1000L;
                saveCycle(block, ready, currentInput);
                inventory = liveInventory(block);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, .7f, 1.2f);
            } else if (decision == MachineCycle.Decision.CANCEL) {
                clearCycle(block);
                inventory = liveInventory(block);
                ready = 0;
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, .5f, 1.5f);
            } else if (decision == MachineCycle.Decision.COMPLETE) {
                takeOne(inventory, INPUT);
                add(inventory, OUTPUT, Objects.requireNonNull(result));
                clearCycle(block);
                inventory = liveInventory(block);
                ready = 0;
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_COMPOSTER_READY, .9f, 1.1f);
                block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(.5, 1.1, .5),
                        6, .2, .1, .2, .01);
            }

            pushOutput(block, inventory);
            renderGui(inventory, ready, definition);
            ensureDisplay(block);
        }
    }

    private Inventory liveInventory(Block block) {
        if (!(block.getState() instanceof Barrel barrel)) {
            throw new IllegalStateException("Samengenerator ist kein Fass mehr: " + position(block));
        }
        return barrel.getInventory();
    }

    private record Cycle(long readyAt, String inputId) {}

    private Cycle currentCycle(Block block) {
        if (!(block.getState() instanceof Barrel barrel)) return new Cycle(0, null);
        return new Cycle(barrel.getPersistentDataContainer().getOrDefault(readyKey, PersistentDataType.LONG, 0L),
                barrel.getPersistentDataContainer().get(inputKey, PersistentDataType.STRING));
    }

    private void saveCycle(Block block, long readyAt, String inputId) {
        if (!(block.getState() instanceof Barrel barrel)) return;
        barrel.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG, readyAt);
        barrel.getPersistentDataContainer().set(inputKey, PersistentDataType.STRING, inputId);
        barrel.update(true, false);
    }

    private void clearCycle(Block block) {
        if (!(block.getState() instanceof Barrel barrel)) return;
        barrel.getPersistentDataContainer().remove(readyKey);
        barrel.getPersistentDataContainer().remove(inputKey);
        barrel.update(true, false);
    }

    private String activeInput(Inventory inventory) {
        ItemStack stack = inventory.getItem(INPUT);
        return stack == null || stack.getType().isAir() ? null : items.id(stack);
    }

    private void takeOne(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getAmount() <= 1) inventory.setItem(slot, null);
        else {
            item.setAmount(item.getAmount() - 1);
            inventory.setItem(slot, item);
        }
    }

    private boolean fits(ItemStack current, ItemStack added) {
        return current == null || current.getType().isAir()
                || (current.isSimilar(added) && current.getAmount() + added.getAmount() <= current.getMaxStackSize());
    }

    private void add(Inventory inventory, int slot, ItemStack added) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) inventory.setItem(slot, added);
        else {
            current.setAmount(current.getAmount() + added.getAmount());
            inventory.setItem(slot, current);
        }
    }

    private void pullInput(Block machine, Inventory target, ItemRegistry.SeedGenerator generator) {
        ItemStack current = target.getItem(INPUT);
        if (current != null && !current.getType().isAir()) return;
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST)) {
            if (!(machine.getRelative(face).getState() instanceof Hopper hopper)) continue;
            Inventory source = hopper.getInventory();
            for (int slot = 0; slot < source.getSize(); slot++) {
                ItemStack stack = source.getItem(slot);
                String id = items.id(stack);
                if (id != null && generator.conversion(id) != null) {
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    target.setItem(INPUT, one);
                    if (stack.getAmount() <= 1) source.setItem(slot, null);
                    else {
                        stack.setAmount(stack.getAmount() - 1);
                        source.setItem(slot, stack);
                    }
                    return;
                }
            }
        }
    }

    private void pushOutput(Block machine, Inventory source) {
        if (!(machine.getRelative(BlockFace.DOWN).getState() instanceof Hopper hopper)) return;
        ItemStack stack = source.getItem(OUTPUT);
        if (stack == null || stack.getType().isAir()) return;
        ItemStack one = stack.clone();
        one.setAmount(1);
        if (!hopper.getInventory().addItem(one).isEmpty()) return;
        if (stack.getAmount() <= 1) source.setItem(OUTPUT, null);
        else {
            stack.setAmount(stack.getAmount() - 1);
            source.setItem(OUTPUT, stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        ItemRegistry.SeedGenerator definition = items.seedGeneratorForItem(event.getItemInHand());
        if (definition == null || !(event.getBlockPlaced().getState() instanceof Barrel barrel)) return;
        barrel.getPersistentDataContainer().set(generatorKey, PersistentDataType.STRING, definition.id());
        barrel.setCustomName("§2§lSamengenerator §8• §fTrocknung");
        barrel.update(true, false);
        register(event.getBlockPlaced());
        event.getBlockPlaced().getWorld().playSound(event.getBlockPlaced().getLocation(),
                Sound.BLOCK_BARREL_OPEN, .8f, 1.3f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void use(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemRegistry.SeedGenerator definition = generator(event.getClickedBlock());
        if (definition != null && !event.getPlayer().hasPermission(definition.permission())) {
            event.setCancelled(true);
            plugin.message(event.getPlayer(), "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        ItemRegistry.SeedGenerator definition = generator(event.getView().getTopInventory());
        if (definition == null) return;
        if (event.isShiftClick() || event.getClick().isKeyboardClick()
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= size) return;
        int slot = event.getRawSlot();
        if (slot == PROGRESS || !OPEN_SLOTS.contains(slot)) {
            event.setCancelled(true);
            return;
        }
        ItemStack cursor = event.getCursor();
        if (slot == OUTPUT && cursor != null && !cursor.getType().isAir()) event.setCancelled(true);
        if (slot == INPUT && cursor != null && !cursor.getType().isAir()
                && definition.conversion(items.id(cursor)) == null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        ItemRegistry.SeedGenerator definition = generator(event.getView().getTopInventory());
        if (definition == null) return;
        int size = event.getView().getTopInventory().getSize();
        boolean top = event.getRawSlots().stream().anyMatch(slot -> slot < size);
        if (top && (event.getRawSlots().stream().anyMatch(slot -> slot < size && slot != INPUT)
                || definition.conversion(items.id(event.getOldCursor())) == null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent event) {
        if (generator(event.getDestination()) != null || generator(event.getSource()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakGenerator(BlockBreakEvent event) {
        ItemRegistry.SeedGenerator definition = generator(event.getBlock());
        if (definition == null || !(event.getBlock().getState() instanceof Barrel barrel)) return;
        event.setDropItems(false);
        for (int slot : List.of(INPUT, OUTPUT)) {
            ItemStack item = barrel.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir() && !filler(item)) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
            }
        }
        barrel.getInventory().clear();
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(),
                    items.create(definition.item(), 1));
        }
        machines.remove(position(event.getBlock()));
        removeDisplay(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> discover(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) { cleanExplosion(event.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityExplode(EntityExplodeEvent event) { cleanExplosion(event.blockList()); }

    private void cleanExplosion(List<Block> blocks) {
        for (Block block : blocks) if (generator(block) != null) {
            machines.remove(position(block));
            removeDisplay(block);
        }
    }
}
