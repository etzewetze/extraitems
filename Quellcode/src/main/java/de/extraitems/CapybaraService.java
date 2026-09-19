package de.extraitems;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Persistent, vanilla-client capybaras: an invisible pig supplies hitbox and passive animal AI,
 * while a synchronized item display supplies the custom adult/baby model.
 */
final class CapybaraService implements Listener {
    private static final Set<Material> BLOCKED_PIG_ITEMS = Set.of(
            Material.CARROT, Material.POTATO, Material.BEETROOT, Material.SADDLE);

    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final ItemRegistry.CustomEntity definition;
    private final NamespacedKey entityIdKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey visualOwnerKey;
    private final Map<UUID, Pig> capybaras = new HashMap<>();
    private final Map<UUID, ItemDisplay> visuals = new HashMap<>();
    private final Map<UUID, String> renderedModels = new HashMap<>();
    private final Map<UUID, Long> restingUntil = new HashMap<>();
    private final Set<UUID> activeFeeds = new HashSet<>();
    private BukkitTask visualTask;
    private BukkitTask behaviourTask;
    private BukkitTask spawnTask;

    CapybaraService(ExtraItemsPlugin plugin, ItemRegistry items, ItemRegistry.CustomEntity definition) {
        this.plugin = plugin;
        this.items = items;
        this.definition = definition;
        this.entityIdKey = new NamespacedKey(plugin, "entity_id");
        this.variantKey = new NamespacedKey(plugin, "capybara_variant");
        this.visualOwnerKey = new NamespacedKey(plugin, "capybara_visual_owner");
    }

    void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) discover(chunk);
        }
        visualTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisuals, 1L, 2L);
        behaviourTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBehaviour, 20L, 20L);
        long interval = definition.spawnIntervalSeconds() * 20L;
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::attemptNaturalSpawns, interval, interval);
    }

    void stop() {
        if (visualTask != null) visualTask.cancel();
        if (behaviourTask != null) behaviourTask.cancel();
        if (spawnTask != null) spawnTask.cancel();
        for (Pig pig : capybaras.values()) if (pig.isValid()) pig.setAI(true);
        capybaras.clear();
        visuals.clear();
        renderedModels.clear();
        restingUntil.clear();
        activeFeeds.clear();
    }

    int count() {
        return (int) capybaras.values().stream().filter(Entity::isValid).count();
    }

    int spawnAt(Location origin, int amount, boolean baby) {
        if (origin.getWorld() == null) return 0;
        int spawned = 0;
        for (int index = 0; index < amount; index++) {
            double angle = amount == 1 ? 0 : Math.PI * 2 * index / amount;
            Location location = origin.clone().add(Math.cos(angle) * Math.min(2, amount - 1), 0,
                    Math.sin(angle) * Math.min(2, amount - 1));
            if (spawnOne(location, baby) != null) spawned++;
        }
        return spawned;
    }

    private Pig spawnOne(Location location, boolean baby) {
        World world = location.getWorld();
        if (world == null) return null;
        int variant = ThreadLocalRandom.current().nextInt(definition.adultModels().size());
        Pig pig = world.spawn(location, Pig.class, entity -> {
            mark(entity, variant);
            if (baby) entity.setAge(-definition.babyGrowthTicks());
        });
        capybaras.put(pig.getUniqueId(), pig);
        ensureVisual(pig);
        return pig;
    }

    private void mark(Pig pig, int variant) {
        PersistentDataContainer data = pig.getPersistentDataContainer();
        data.set(entityIdKey, PersistentDataType.STRING, definition.id());
        data.set(variantKey, PersistentDataType.INTEGER,
                Math.floorMod(variant, definition.adultModels().size()));
        pig.setPersistent(true);
        pig.setRemoveWhenFarAway(false);
        pig.setInvisible(true);
        pig.setSilent(true);
        pig.setCanPickupItems(false);
        pig.setSaddle(false);
        pig.setCollidable(true);
        pig.setAI(true);
    }

    private boolean isCapybara(Entity entity) {
        return entity instanceof Pig && definition.id().equals(entity.getPersistentDataContainer()
                .get(entityIdKey, PersistentDataType.STRING));
    }

    private int variant(Pig pig) {
        Integer stored = pig.getPersistentDataContainer().get(variantKey, PersistentDataType.INTEGER);
        int variant = stored == null ? ThreadLocalRandom.current().nextInt(definition.adultModels().size())
                : Math.floorMod(stored, definition.adultModels().size());
        pig.getPersistentDataContainer().set(variantKey, PersistentDataType.INTEGER, variant);
        return variant;
    }

    private void discover(Chunk chunk) {
        if (!chunk.isLoaded()) return;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ItemDisplay display)) continue;
            String id = display.getPersistentDataContainer().get(entityIdKey, PersistentDataType.STRING);
            String rawOwner = display.getPersistentDataContainer().get(visualOwnerKey, PersistentDataType.STRING);
            if (!definition.id().equals(id) || rawOwner == null) continue;
            try {
                UUID owner = UUID.fromString(rawOwner);
                ItemDisplay existing = visuals.putIfAbsent(owner, display);
                if (existing != null && existing != display) display.remove();
            } catch (IllegalArgumentException ignored) {
                display.remove();
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Pig pig) || !isCapybara(pig)) continue;
            mark(pig, variant(pig));
            capybaras.put(pig.getUniqueId(), pig);
            ensureVisual(pig);
        }
    }

    private void ensureVisual(Pig pig) {
        if (!pig.isValid()) return;
        UUID owner = pig.getUniqueId();
        ItemDisplay display = visuals.get(owner);
        if (display == null || !display.isValid() || !display.getWorld().equals(pig.getWorld())) {
            if (display != null && display.isValid()) display.remove();
            Location location = visualLocation(pig);
            display = pig.getWorld().spawn(location, ItemDisplay.class, visual -> {
                PersistentDataContainer data = visual.getPersistentDataContainer();
                data.set(entityIdKey, PersistentDataType.STRING, definition.id());
                data.set(visualOwnerKey, PersistentDataType.STRING, owner.toString());
                visual.setPersistent(true);
                visual.setInvulnerable(true);
                visual.setGravity(false);
                visual.setSilent(true);
                visual.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                visual.setTeleportDuration(2);
                visual.setInterpolationDuration(2);
                visual.setViewRange(1.25f);
                visual.setDisplayWidth(2.0f);
                visual.setDisplayHeight(1.5f);
            });
            visuals.put(owner, display);
            renderedModels.remove(owner);
        }
        int variant = variant(pig);
        NamespacedKey model = pig.isAdult()
                ? definition.adultModels().get(variant) : definition.babyModels().get(variant);
        String state = model.toString();
        if (!state.equals(renderedModels.get(owner))) {
            display.setItemStack(items.model(model));
            renderedModels.put(owner, state);
        }
    }

    private Location visualLocation(Pig pig) {
        Location location = pig.getLocation().clone().add(0, .5, 0);
        location.setPitch(0);
        return location;
    }

    private void updateVisuals() {
        Iterator<Map.Entry<UUID, Pig>> iterator = capybaras.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pig> entry = iterator.next();
            Pig pig = entry.getValue();
            if (!pig.isValid()) {
                iterator.remove();
                visuals.remove(entry.getKey());
                renderedModels.remove(entry.getKey());
                restingUntil.remove(entry.getKey());
                continue;
            }
            ensureVisual(pig);
            ItemDisplay display = visuals.get(entry.getKey());
            if (display != null && display.isValid()) display.teleport(visualLocation(pig));
        }
    }

    private void updateBehaviour() {
        long now = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Pig pig : new ArrayList<>(capybaras.values())) {
            if (!pig.isValid()) continue;
            UUID id = pig.getUniqueId();
            long restEnd = restingUntil.getOrDefault(id, 0L);
            boolean inWater = pig.isInWater();
            if (inWater || restEnd <= now) {
                if (!pig.hasAI()) pig.setAI(true);
                restingUntil.remove(id);
            }

            Player food = nearestBerryHolder(pig, 10);
            if (food != null) {
                wake(pig);
                moveToward(pig, food.getLocation(), .12);
            } else if (pig.hasAI() && !inWater) {
                Pig companion = nearestCompanion(pig, 16);
                if (companion != null && companion.getLocation().distanceSquared(pig.getLocation()) > 36) {
                    moveToward(pig, companion.getLocation(), .045);
                } else if (random.nextInt(10) == 0) {
                    Location water = nearestWater(pig, 8, 3);
                    if (water != null) moveToward(pig, water, .055);
                }
            }

            if (inWater) {
                Vector velocity = pig.getVelocity();
                pig.setVelocity(new Vector(velocity.getX(), Math.max(velocity.getY(), .055), velocity.getZ()));
                pig.setSwimming(true);
            }
            if (pig.hasAI() && pig.isAdult() && !pig.isLoveMode() && !pig.isLeashed()
                    && pig.isOnGround() && random.nextDouble() < .005) {
                pig.setAI(false);
                restingUntil.put(id, now + random.nextLong(4000, 10001));
            }
            if (random.nextInt(220) == 0) {
                pig.getWorld().playSound(pig.getLocation(), Sound.ENTITY_PIG_AMBIENT, .45f, .72f);
            }
        }
    }

    private Player nearestBerryHolder(Pig pig, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player player : pig.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || !holds(player, definition.breedItem())) continue;
            double distance = player.getLocation().distanceSquared(pig.getLocation());
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Pig nearestCompanion(Pig source, double radius) {
        Pig best = null;
        double bestDistance = radius * radius;
        for (Pig candidate : capybaras.values()) {
            if (candidate == source || !candidate.isValid() || !candidate.getWorld().equals(source.getWorld())) continue;
            double distance = candidate.getLocation().distanceSquared(source.getLocation());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void moveToward(Pig pig, Location target, double speed) {
        Vector direction = target.toVector().subtract(pig.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < 1) return;
        Vector push = direction.normalize().multiply(speed);
        Vector current = pig.getVelocity();
        pig.setVelocity(new Vector(push.getX(), current.getY(), push.getZ()));
    }

    private Location nearestWater(Pig pig, int horizontal, int vertical) {
        Location center = pig.getLocation();
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = -vertical; y <= vertical; y++) {
            for (int x = -horizontal; x <= horizontal; x++) {
                for (int z = -horizontal; z <= horizontal; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (!block.isLiquid()) continue;
                    double distance = block.getLocation().distanceSquared(center);
                    if (distance < bestDistance) {
                        best = block.getLocation().add(.5, .5, .5);
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private void attemptNaturalSpawns() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        int attempts = Math.min(players.size(), 4);
        for (int index = 0; index < attempts; index++) attemptNaturalSpawn(players.get(index));
    }

    private void attemptNaturalSpawn(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= definition.spawnChance()) return;
        World world = player.getWorld();
        int worldCount = loadedIn(world);
        if (worldCount >= definition.maxLoadedPerWorld()) return;

        double angle = random.nextDouble(Math.PI * 2);
        int distance = random.nextInt(definition.spawnDistanceMin(), definition.spawnDistanceMax() + 1);
        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        Location center = surface(world, x, z);
        if (center == null || nearbyCount(center, 24) >= definition.maxNearby()) return;

        int available = Math.min(definition.maxLoadedPerWorld() - worldCount,
                definition.maxNearby() - nearbyCount(center, 24));
        int group = CapybaraPolicy.groupSize(definition.groupMin(), definition.groupMax(), available, random.nextInt());
        for (int index = 0; index < group; index++) {
            Location location = surface(world, x + random.nextInt(-3, 4), z + random.nextInt(-3, 4));
            if (location == null) continue;
            boolean baby = group >= 3 && index == group - 1 && random.nextDouble() < .35;
            spawnOne(location, baby);
        }
    }

    private Location surface(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block ground = world.getBlockAt(x, y, z);
        Location location = new Location(world, x + .5, y + 1, z + .5);
        if (!definition.spawnBiomes().contains(biomeKey(ground))
                || !ground.getType().isSolid() || ground.isLiquid()
                || !location.getBlock().isPassable() || location.getBlock().isLiquid()
                || !location.clone().add(0, 1, 0).getBlock().isPassable()
                || !world.getWorldBorder().isInside(location)) return null;
        return location;
    }

    /**
     * Kept behind one compatibility boundary because Keyed#getKeyOrThrow is not present in the
     * pinned 26.3 alpha API while Keyed#getKey still exists on every supported server line.
     */
    @SuppressWarnings("deprecation")
    private static String biomeKey(Block block) {
        return block.getBiome().getKey().toString();
    }

    private int loadedIn(World world) {
        return (int) capybaras.values().stream()
                .filter(pig -> pig.isValid() && pig.getWorld().equals(world)).count();
    }

    private int nearbyCount(Location location, double radius) {
        double squared = radius * radius;
        return (int) capybaras.values().stream().filter(pig -> pig.isValid()
                && pig.getWorld().equals(location.getWorld())
                && pig.getLocation().distanceSquared(location) <= squared).count();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        if (!isCapybara(event.getRightClicked())) return;
        Player player = event.getPlayer();
        Pig pig = (Pig) event.getRightClicked();
        EquipmentSlot hand = event.getHand();
        ItemStack stack = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        Material held = stack.getType();
        if (held != definition.breedItem()) {
            if (BLOCKED_PIG_ITEMS.contains(held)) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!activeFeeds.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> activeFeeds.remove(player.getUniqueId()));
        if (!plugin.gate().ready(player)) return;
        if (!player.hasPermission(definition.breedPermission())) {
            plugin.message(player, "no-permission");
            return;
        }

        boolean fed = false;
        if (!pig.isAdult()) {
            int next = CapybaraPolicy.fedBabyAge(pig.getAge(), definition.feedGrowthTicks());
            if (next != pig.getAge()) {
                pig.setAge(next);
                fed = true;
            }
        } else if (pig.canBreed() && !pig.isLoveMode()) {
            pig.setLoveModeTicks(600);
            pig.setBreedCause(player.getUniqueId());
            fed = true;
        }
        if (!fed) {
            plugin.message(player, "capybara-cooldown");
            return;
        }
        consume(player, hand);
        wake(pig);
        pig.getWorld().spawnParticle(Particle.HEART, pig.getLocation().add(0, .9, 0), 7, .35, .25, .35, .02);
        pig.getWorld().playSound(pig.getLocation(), Sound.ENTITY_GENERIC_EAT, .8f, .85f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breed(EntityBreedEvent event) {
        boolean mother = isCapybara(event.getMother());
        boolean father = isCapybara(event.getFather());
        if (!mother && !father) return;
        if (!mother || !father || !(event.getEntity() instanceof Pig child)) {
            event.setCancelled(true);
            return;
        }
        Pig motherPig = (Pig) event.getMother();
        Pig fatherPig = (Pig) event.getFather();
        int inherited = CapybaraPolicy.inheritedVariant(variant(motherPig), variant(fatherPig),
                definition.adultModels().size(), ThreadLocalRandom.current().nextInt());
        mark(child, inherited);
        child.setAge(-definition.babyGrowthTicks());
        capybaras.put(child.getUniqueId(), child);
        event.setExperience(1);
        Bukkit.getScheduler().runTask(plugin, () -> ensureVisual(child));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!isCapybara(event.getEntity())) return;
        Pig pig = (Pig) event.getEntity();
        wake(pig);
        pig.getWorld().playSound(pig.getLocation(), Sound.ENTITY_PIG_HURT, .65f, .72f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (!isCapybara(event.getEntity())) return;
        UUID owner = event.getEntity().getUniqueId();
        event.getDrops().clear();
        event.setDroppedExp(0);
        removeVisual(owner);
        capybaras.remove(owner);
        restingUntil.remove(owner);
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(), Sound.ENTITY_PIG_DEATH, .7f, .7f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void transform(EntityTransformEvent event) {
        if (isCapybara(event.getEntity())) event.setCancelled(true);
    }

    private void wake(Pig pig) {
        restingUntil.remove(pig.getUniqueId());
        if (!pig.hasAI()) pig.setAI(true);
    }

    private boolean holds(Player player, Material material) {
        return player.getInventory().getItemInMainHand().getType() == material
                || player.getInventory().getItemInOffHand().getType() == material;
    }

    private void consume(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack held = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (held.getAmount() <= 1) held = new ItemStack(Material.AIR);
        else held.setAmount(held.getAmount() - 1);
        if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(held);
        else player.getInventory().setItemInOffHand(held);
    }

    private void removeVisual(UUID owner) {
        ItemDisplay display = visuals.remove(owner);
        if (display != null && display.isValid()) display.remove();
        renderedModels.remove(owner);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> discover(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entitiesLoad(EntitiesLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> discover(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isCapybara(entity)) {
                UUID id = entity.getUniqueId();
                capybaras.remove(id);
                renderedModels.remove(id);
                restingUntil.remove(id);
            } else if (entity instanceof ItemDisplay display) {
                String raw = display.getPersistentDataContainer().get(visualOwnerKey, PersistentDataType.STRING);
                if (raw == null) continue;
                try {
                    UUID owner = UUID.fromString(raw);
                    if (visuals.get(owner) == display) visuals.remove(owner);
                } catch (IllegalArgumentException ignored) {
                    // Invalid tags are removed on the next discovery pass.
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void worldUnload(WorldUnloadEvent event) {
        UUID world = event.getWorld().getUID();
        capybaras.entrySet().removeIf(entry -> entry.getValue().getWorld().getUID().equals(world));
        visuals.entrySet().removeIf(entry -> entry.getValue().getWorld().getUID().equals(world));
    }
}
