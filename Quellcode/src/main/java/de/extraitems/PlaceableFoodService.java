package de.extraitems;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Cake-like custom food: persistent display/hitbox, ten portions, no item drop on break. */
final class PlaceableFoodService implements Listener {
    private static final class Food {
        final Block block;
        final ItemRegistry.PlaceableFood definition;
        int bites;
        ItemDisplay display;
        Interaction interaction;
        Food(Block block, ItemRegistry.PlaceableFood definition, int bites) {
            this.block = block; this.definition = definition; this.bites = bites;
        }
    }

    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final NamespacedKey idKey, positionKey, roleKey, bitesKey;
    private final Map<String, Food> foods = new HashMap<>();
    private final Set<UUID> active = new HashSet<>();
    private BukkitTask ticker;
    private boolean protectionDispatch;

    PlaceableFoodService(ExtraItemsPlugin plugin, ItemRegistry items) {
        this.plugin = plugin; this.items = items;
        idKey = new NamespacedKey(plugin, "food_id");
        positionKey = new NamespacedKey(plugin, "food_position");
        roleKey = new NamespacedKey(plugin, "food_role");
        bitesKey = new NamespacedKey(plugin, "food_bites");
    }

    void start() {
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) discover(chunk);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }
    void stop() { if (ticker != null) ticker.cancel(); foods.clear(); }
    int count() { return foods.size(); }

    private String position(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void discover(Chunk chunk) {
        Map<String, List<Entity>> groups = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            String pos = entity.getPersistentDataContainer().get(positionKey, PersistentDataType.STRING);
            if (pos != null) groups.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(entity);
        }
        for (var entry : groups.entrySet()) {
            Entity source = entry.getValue().getFirst();
            String id = source.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
            Integer bites = source.getPersistentDataContainer().get(bitesKey, PersistentDataType.INTEGER);
            ItemRegistry.PlaceableFood definition = items.placeableFood(id);
            if (definition == null || bites == null || bites < 0 || bites >= definition.models().size()) {
                entry.getValue().forEach(Entity::remove); continue;
            }
            Block block = source.getLocation().getBlock();
            if (block.getType() != Material.STRUCTURE_VOID) { entry.getValue().forEach(Entity::remove); continue; }
            Food food = foods.computeIfAbsent(entry.getKey(), ignored -> new Food(block, definition, bites));
            for (Entity entity : entry.getValue()) {
                String role = entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
                if (entity instanceof ItemDisplay display && "display".equals(role) && food.display == null) food.display = display;
                else if (entity instanceof Interaction hit && "hit".equals(role) && food.interaction == null) food.interaction = hit;
                else entity.remove();
            }
            visual(food);
        }
    }

    private void visual(Food food) {
        Location model = food.block.getLocation().add(.5, .5 - .0625, .5);
        if (food.display == null || !food.display.isValid()) {
            food.display = food.block.getWorld().spawn(model, ItemDisplay.class, display -> {
                tag(display, food, "display"); display.setPersistent(true); display.setInvulnerable(true); display.setGravity(false);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setDisplayWidth(1); display.setDisplayHeight(.6f); display.setViewRange(.8f);
            });
        }
        food.display.setItemStack(items.model(food.definition.models().get(food.bites)));
        if (food.interaction == null || !food.interaction.isValid()) {
            food.interaction = food.block.getWorld().spawn(food.block.getLocation().add(.5, 0, .5), Interaction.class, hit -> {
                tag(hit, food, "hit"); hit.setPersistent(true); hit.setGravity(false); hit.setResponsive(true);
                hit.setInteractionWidth(.9f); hit.setInteractionHeight(.5f);
            });
        }
        tag(food.display, food, "display"); tag(food.interaction, food, "hit");
    }

    private void tag(Entity entity, Food food, String role) {
        var pdc = entity.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.STRING, food.definition.id());
        pdc.set(positionKey, PersistentDataType.STRING, position(food.block));
        pdc.set(roleKey, PersistentDataType.STRING, role);
        pdc.set(bitesKey, PersistentDataType.INTEGER, food.bites);
    }

    private Food food(Entity entity) {
        String pos = entity.getPersistentDataContainer().get(positionKey, PersistentDataType.STRING);
        return pos == null ? null : foods.get(pos);
    }

    private void tick() {
        for (Food food : new ArrayList<>(foods.values())) {
            if (food.block.getType() != Material.STRUCTURE_VOID || !food.block.getRelative(BlockFace.DOWN).getType().isSolid()) remove(food);
            else visual(food);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(PlayerInteractEvent event) {
        if (protectionDispatch || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getBlockFace() != BlockFace.UP || event.getHand() == null) return;
        ItemRegistry.PlaceableFood definition = items.placeableFoodForItem(event.getItem());
        if (definition == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.gate().ready(player)) return;
        if (!player.hasPermission(definition.permission())) { plugin.message(player, "no-permission"); return; }
        Block support = event.getClickedBlock(), target = support.getRelative(BlockFace.UP);
        if (!support.getType().isSolid() || !target.getType().isAir() || target.getY() >= target.getWorld().getMaxHeight()) {
            plugin.message(player, "no-space"); return;
        }
        String pos = position(target);
        if (foods.containsKey(pos)) return;
        BlockState old = target.getState();
        boolean allowed = false;
        try {
            target.setType(Material.STRUCTURE_VOID, false);
            BlockPlaceEvent place = new BlockPlaceEvent(target, old, support, event.getItem(), player, true, event.getHand());
            protectionDispatch = true; Bukkit.getPluginManager().callEvent(place);
            allowed = !place.isCancelled() && place.canBuild() && target.getType() == Material.STRUCTURE_VOID;
        } finally {
            protectionDispatch = false;
            if (!allowed) old.update(true, false);
        }
        if (!allowed) return;
        Food food = new Food(target, definition, 0);
        foods.put(pos, food); visual(food);
        consume(player, event.getHand());
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CAKE_ADD_CANDLE, .8f, .9f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void eat(PlayerInteractEntityEvent event) {
        Food food = food(event.getRightClicked());
        if (food == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !plugin.gate().ready(player) || player.getFoodLevel() >= 20
                || !active.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> active.remove(player.getUniqueId()));
        if (food.definition.emptyHandOnly() && !player.getInventory().getItemInMainHand().getType().isAir()) return;
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + food.definition.nutrition()));
        player.setSaturation(Math.min(20f, player.getSaturation() + food.definition.saturation()));
        food.block.getWorld().playSound(food.block.getLocation(), Sound.ENTITY_GENERIC_EAT, .8f, .95f);
        food.block.getWorld().spawnParticle(Particle.ITEM, food.block.getLocation().add(.5,.35,.5), 8, .25,.15,.25,.02,
                items.create("cheese_slice", 1));
        food.bites++;
        if (food.bites >= food.definition.models().size()) {
            remove(food); player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, .7f, 1.1f);
        } else visual(food);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        Food food = food(event.getEntity());
        if (food == null) return;
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent by) || !(by.getDamager() instanceof Player player)) return;
        BlockBreakEvent breakEvent = new BlockBreakEvent(food.block, player);
        try { protectionDispatch = true; Bukkit.getPluginManager().callEvent(breakEvent); }
        finally { protectionDispatch = false; }
        if (!breakEvent.isCancelled()) remove(food); // Cake behaviour: breaking destroys it without a drop.
    }

    private void remove(Food food) {
        if (foods.remove(position(food.block)) == null) return;
        if (food.display != null) food.display.remove();
        if (food.interaction != null) food.interaction.remove();
        if (food.block.getType() == Material.STRUCTURE_VOID) food.block.setType(Material.AIR, false);
    }

    private void consume(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack held = hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (held.getAmount() <= 1) held = new ItemStack(Material.AIR); else held.setAmount(held.getAmount() - 1);
        if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(held); else player.getInventory().setItemInOffHand(held);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkLoad(ChunkLoadEvent event) { Bukkit.getScheduler().runTask(plugin, () -> discover(event.getChunk())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent event) {
        UUID world = event.getWorld().getUID(); int x = event.getChunk().getX(), z = event.getChunk().getZ();
        foods.entrySet().removeIf(entry -> entry.getValue().block.getWorld().getUID().equals(world)
                && (entry.getValue().block.getX() >> 4) == x && (entry.getValue().block.getZ() >> 4) == z);
    }
}
