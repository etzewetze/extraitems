package de.extraitems;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Vanilla structure-void carrier + two persistent vanilla entities per plant. No NMS. */
final class CropService implements Listener {
    private record ChunkKey(UUID world, int x, int z) {
        static ChunkKey of(Chunk c) { return new ChunkKey(c.getWorld().getUID(), c.getX(), c.getZ()); }
    }
    private static final class Plant {
        PlantRecord data;
        ItemDisplay display;
        Interaction interaction;
        Plant(PlantRecord data) { this.data = data; }
    }
    private static final class Loaded {
        final Chunk chunk;
        final Map<String, Plant> plants = new LinkedHashMap<>();
        boolean locked;
        Loaded(Chunk chunk) { this.chunk = chunk; }
    }
    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final NamespacedKey dataKey, positionKey, roleKey;
    private final Map<ChunkKey, Loaded> loaded = new HashMap<>();
    private final Set<UUID> activeActions = new HashSet<>();
    private BukkitTask ticker;
    private boolean protectionDispatch;

    CropService(ExtraItemsPlugin plugin, ItemRegistry items) {
        this.plugin = plugin; this.items = items;
        dataKey = new NamespacedKey(plugin, "plants_v1");
        positionKey = new NamespacedKey(plugin, "plant_position");
        roleKey = new NamespacedKey(plugin, "plant_role");
    }
    void start() {
        for (World world : Bukkit.getWorlds()) for (Chunk c : world.getLoadedChunks()) load(c);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }
    void stop() {
        if (ticker != null) ticker.cancel();
        loaded.values().forEach(this::save);
        loaded.clear(); // Persistent entities and chunk data intentionally remain in the world.
    }
    int count() { return loaded.values().stream().mapToInt(c -> c.plants.size()).sum(); }
    private Loaded at(Block block) {
        return loaded.get(new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4));
    }
    private String pos(Block b) { return b.getX() + "," + b.getY() + "," + b.getZ(); }
    private Plant plant(Block block) { var c = at(block); return c == null ? null : c.plants.get(pos(block)); }
    private Block block(Loaded c, Plant p) { return c.chunk.getWorld().getBlockAt(p.data.x(), p.data.y(), p.data.z()); }
    private boolean reserved(Block block) { return plant(block) != null || plant(block.getRelative(BlockFace.UP)) != null; }

    private void load(Chunk chunk) {
        if (!chunk.isLoaded() || loaded.containsKey(ChunkKey.of(chunk))) return;
        var c = new Loaded(chunk); loaded.put(ChunkKey.of(chunk), c);
        String raw = chunk.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        try {
            if (raw != null) for (String line : raw.lines().toList()) {
                if (line.isBlank()) continue;
                var p = PlantRecord.decode(line, chunk.getX(), chunk.getZ(), chunk.getWorld().getMinHeight(), chunk.getWorld().getMaxHeight());
                var def = items.crop(p.crop());
                if (def == null || p.stage() >= def.models().size()) throw new IllegalArgumentException("Pflanzendefinition fehlt/Modellanzahl geändert: " + p.crop());
                if (c.plants.putIfAbsent(p.position(), new Plant(p)) != null) throw new IllegalArgumentException("Doppelte Pflanzenposition");
            }
        } catch (RuntimeException e) {
            c.locked = true; c.plants.clear();
            plugin.getLogger().severe("Pflanzendaten in " + chunk.getWorld().getName() + " " + chunk.getX() + "/" + chunk.getZ()
                    + " nicht lesbar; Chunk wird nicht verändert: " + e.getMessage());
            return;
        }
        reconcile(c);
    }
    private void reconcile(Loaded c) {
        if (c.locked) return;
        for (Entity entity : c.chunk.getEntities()) {
            String position = entity.getPersistentDataContainer().get(positionKey, PersistentDataType.STRING);
            if (position == null) continue;
            Plant p = c.plants.get(position);
            String role = entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
            if (p != null && entity instanceof ItemDisplay display && "display".equals(role) && (p.display == null || !p.display.isValid())) p.display = display;
            else if (p != null && entity instanceof Interaction hit && "hit".equals(role) && (p.interaction == null || !p.interaction.isValid())) p.interaction = hit;
            else if (p == null || (entity != p.display && entity != p.interaction)) entity.remove();
        }
        for (Plant p : new ArrayList<>(c.plants.values())) {
            if (block(c,p).getType() != Material.STRUCTURE_VOID || block(c,p).getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) remove(c,p,false,false);
            else visual(c,p);
        }
    }
    private void visual(Loaded c, Plant p) {
        var def = items.crop(p.data.crop());
        // Display models are centered on (8,8,8); +0.5 aligns y=0 geometry with soil.
        Location loc = block(c,p).getLocation().add(.5, .5 - .0625, .5);
        if (p.display == null || !p.display.isValid()) {
            p.display = c.chunk.getWorld().spawn(loc, ItemDisplay.class, e -> {
                tag(e, p, "display"); e.setPersistent(true); e.setInvulnerable(true); e.setGravity(false);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setDisplayWidth(1); e.setDisplayHeight(1.5f); e.setViewRange(0.8f);
            });
        }
        p.display.setItemStack(items.model(def.models().get(p.data.stage())));
        if (p.interaction == null || !p.interaction.isValid()) {
            p.interaction = c.chunk.getWorld().spawn(block(c,p).getLocation().add(.5, 0, .5), Interaction.class, e -> {
                tag(e, p, "hit"); e.setPersistent(true); e.setGravity(false); e.setResponsive(true);
                e.setInteractionWidth(.75f); e.setInteractionHeight(.9f);
            });
        }
    }
    private void tag(Entity entity, Plant p, String role) {
        entity.getPersistentDataContainer().set(positionKey, PersistentDataType.STRING, p.data.position());
        entity.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role);
    }
    private void save(Loaded c) {
        if (c.locked) return;
        var pdc = c.chunk.getPersistentDataContainer();
        if (c.plants.isEmpty()) pdc.remove(dataKey);
        else pdc.set(dataKey, PersistentDataType.STRING, String.join("\n", c.plants.values().stream().map(p -> p.data.encode()).toList()));
    }
    private void tick() {
        for (Loaded c : new ArrayList<>(loaded.values())) {
            if (c.locked || !c.chunk.isLoaded()) continue;
            boolean dirty = false;
            for (Plant p : new ArrayList<>(c.plants.values())) {
                Block b = block(c,p), soil = b.getRelative(BlockFace.DOWN);
                if (b.getType() != Material.STRUCTURE_VOID || soil.getType() != Material.FARMLAND) { remove(c,p,false,false); continue; }
                var def = items.crop(p.data.crop());
                boolean conditions = b.getLightLevel() >= def.light() && (!def.hydrated() || ((Farmland) soil.getBlockData()).getMoisture() > 0);
                var next = p.data.tick(def.secondsPerStage(), def.models().size()-1, conditions);
                boolean stageChanged = next.stage() != p.data.stage();
                if (!next.equals(p.data)) { p.data = next; dirty = true; }
                if (stageChanged || p.display == null || !p.display.isValid() || p.interaction == null || !p.interaction.isValid()) visual(c,p);
            }
            if (dirty) save(c);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkLoad(ChunkLoadEvent e) { Bukkit.getScheduler().runTask(plugin, () -> load(e.getChunk())); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void entitiesLoad(EntitiesLoadEvent e) { Bukkit.getScheduler().runTask(plugin, () -> { var c = loaded.get(ChunkKey.of(e.getChunk())); if (c != null && c.chunk.isLoaded()) reconcile(c); }); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent e) { var c = loaded.remove(ChunkKey.of(e.getChunk())); if (c != null) save(c); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void worldUnload(WorldUnloadEvent e) { loaded.entrySet().removeIf(entry -> entry.getKey().world().equals(e.getWorld().getUID())); }

    private boolean canAct(Player p) { return plugin.gate().ready(p) && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.CREATIVE); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void plantSeed(PlayerInteractEvent e) {
        if (protectionDispatch || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getHand() == null) return;
        var def = items.cropForSeed(e.getItem());
        if (def == null) return;
        e.setCancelled(true); // Do not use the seed's vanilla base item.
        Player player = e.getPlayer();
        if (!canAct(player)) return;
        if (!player.hasPermission(def.permission())) { plugin.message(player,"no-permission"); return; }
        Block soil = e.getClickedBlock();
        if (soil.getType() != Material.FARMLAND || e.getBlockFace() != BlockFace.UP) return;
        Block target = soil.getRelative(BlockFace.UP);
        if (target.getY() >= target.getWorld().getMaxHeight()) return;
        Loaded c = at(target);
        if (c == null) { load(target.getChunk()); c = at(target); }
        if (c.locked || !target.getType().isAir() || plant(target) != null || c.plants.size() >= plugin.getConfig().getInt("limits.plants-per-chunk",64)
                || count() >= plugin.getConfig().getInt("limits.loaded-plants-total",10000)) { plugin.message(player,"no-space"); return; }
        BlockState old = target.getState();
        ItemStack used = e.getItem().clone();
        boolean allowed = false;
        try {
            target.setType(Material.STRUCTURE_VOID, false);
            var place = new BlockPlaceEvent(target, old, soil, used, player, spawnAllowed(player,target), e.getHand());
            protectionDispatch = true; Bukkit.getPluginManager().callEvent(place);
            allowed = !place.isCancelled() && place.canBuild() && target.getType() == Material.STRUCTURE_VOID;
        } finally {
            protectionDispatch = false;
            if (!allowed) old.update(true,false);
        }
        if (!allowed) return;
        var p = new Plant(new PlantRecord(target.getX(),target.getY(),target.getZ(),def.id(),0,0));
        c.plants.put(p.data.position(), p);
        try { visual(c,p); save(c); }
        catch (RuntimeException error) { remove(c,p,false,false); old.update(true,false); throw error; }
        consume(player, e.getHand());
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 1f);
    }
    private Plant entityPlant(Entity entity) {
        String position = entity.getPersistentDataContainer().get(positionKey, PersistentDataType.STRING);
        var c = at(entity.getLocation().getBlock());
        return c == null || position == null ? null : c.plants.get(position);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        Plant p = entityPlant(e.getRightClicked());
        if (p == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !canAct(player) || !activeActions.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> activeActions.remove(player.getUniqueId()));
        Loaded c = at(e.getRightClicked().getLocation().getBlock());
        var def = items.crop(p.data.crop());
        boolean meal = player.getInventory().getItemInMainHand().getType() == Material.BONE_MEAL;
        if (meal && p.data.stage() < def.models().size()-1) {
            if (!def.bonemeal()) return;
            if (!player.hasPermission(def.permission())) { plugin.message(player,"no-permission"); return; }
            if (!protectedBreak(player, block(c,p))) return;
            p.data = p.data.stage(p.data.stage()+1); visual(c,p); save(c); consume(player,EquipmentSlot.HAND);
            block(c,p).getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block(c,p).getLocation().add(.5,.5,.5),5,.25,.25,.25);
        } else if (p.data.stage() == def.models().size()-1 && protectedBreak(player,block(c,p))) {
            // Persist state before emitting drops, so repeated clicks cannot harvest twice.
            p.data = p.data.stage(def.regrowStage()); visual(c,p); save(c);
            dropProduce(c,p); block(c,p).getWorld().playSound(block(c,p).getLocation(),Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES,1,1);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        Plant p = entityPlant(e.getEntity());
        if (p == null) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent by) || !(by.getDamager() instanceof Player player) || !canAct(player)) return;
        Loaded c = at(e.getEntity().getLocation().getBlock());
        if (protectedBreak(player,block(c,p))) remove(c,p,player.getGameMode()!=GameMode.CREATIVE,true);
    }
    private boolean protectedBreak(Player player, Block block) {
        if (!spawnAllowed(player,block)) return false;
        var event = new BlockBreakEvent(block,player);
        try { protectionDispatch = true; Bukkit.getPluginManager().callEvent(event); return !event.isCancelled(); }
        finally { protectionDispatch = false; }
    }
    private void dropProduce(Loaded c, Plant p) {
        var def = items.crop(p.data.crop());
        block(c,p).getWorld().dropItemNaturally(block(c,p).getLocation().add(.5,.3,.5), items.create(def.produce(),ThreadLocalRandom.current().nextInt(def.minDrop(),def.maxDrop()+1)));
    }
    private void remove(Loaded c, Plant p, boolean drop, boolean produce) {
        if (c.plants.remove(p.data.position()) == null) return;
        if (p.display != null) p.display.remove();
        if (p.interaction != null) p.interaction.remove();
        Block b = block(c,p);
        if (b.getType() == Material.STRUCTURE_VOID) b.setType(Material.AIR,false);
        save(c);
        if (drop) {
            var def = items.crop(p.data.crop());
            b.getWorld().dropItemNaturally(b.getLocation().add(.5,.3,.5),items.create(def.seed(),1));
            if (produce && p.data.stage() == def.models().size()-1) dropProduce(c,p);
        }
    }
    private boolean spawnAllowed(Player player, Block block) {
        int radius = Bukkit.getSpawnRadius();
        if (radius <= 0 || player.isOp() || Bukkit.getOperators().isEmpty() || !block.getWorld().equals(Bukkit.getWorlds().getFirst())) return true;
        Location spawn = block.getWorld().getSpawnLocation();
        return Math.max(Math.abs(block.getX()-spawn.getBlockX()), Math.abs(block.getZ()-spawn.getBlockZ())) > radius;
    }
    private void consume(Player p, EquipmentSlot hand) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack stack = p.getInventory().getItem(hand); stack.setAmount(stack.getAmount()-1); p.getInventory().setItem(hand,stack);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (protectionDispatch) return;
        Plant p = plant(e.getBlock());
        if (p != null) {
            e.setCancelled(true);
            if (canAct(e.getPlayer()) && protectedBreak(e.getPlayer(),e.getBlock())) remove(at(e.getBlock()),p,e.getPlayer().getGameMode()!=GameMode.CREATIVE,true);
        } else if (plant(e.getBlock().getRelative(BlockFace.UP)) != null) {
            e.setCancelled(true); e.getPlayer().sendMessage("§eBitte zuerst die Pflanze abbauen.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (protectionDispatch) return;
        if (reserved(e.getBlockPlaced()) || (e instanceof BlockMultiPlaceEvent multi && multi.getReplacedBlockStates().stream().anyMatch(s -> reserved(s.getBlock())))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fade(BlockFadeEvent e) { if (reserved(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluid(BlockFromToEvent e) { if (reserved(e.getToBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void change(EntityChangeBlockEvent e) { if (reserved(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent e) { if (reserved(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { if (reserved(e.getBlock().getRelative(e.getDirection())) || e.getBlocks().stream().anyMatch(b -> reserved(b) || reserved(b.getRelative(e.getDirection())))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(b -> reserved(b) || reserved(b.getRelative(e.getDirection().getOppositeFace())))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) { e.blockList().removeIf(this::reserved); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explodeBlock(BlockExplodeEvent e) { e.blockList().removeIf(this::reserved); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void tree(StructureGrowEvent e) { if (e.getBlocks().stream().anyMatch(s -> reserved(s.getBlock()))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fertilize(BlockFertilizeEvent e) { if (e.getBlocks().stream().anyMatch(s -> reserved(s.getBlock()))) e.setCancelled(true); }
}
