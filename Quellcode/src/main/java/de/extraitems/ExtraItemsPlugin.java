package de.extraitems;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class ExtraItemsPlugin extends JavaPlugin implements TabExecutor, Listener {
    private PackService pack;
    private PackGate gate;
    private ItemRegistry items;
    private CropService crops;
    private ToolService tools;
    private CheeseStationService stations;
    private SeedGeneratorService seedGenerators;
    private PlaceableFoodService placeableFoods;
    private CapybaraService capybaras;
    private BukkitTask externalRecipeTask;
    private boolean operational;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        pack = new PackService(this);
        gate = new PackGate(this, pack);
        getServer().getPluginManager().registerEvents(gate, this);
        Objects.requireNonNull(getCommand("extraitems")).setExecutor(this);
        Objects.requireNonNull(getCommand("extraitems")).setTabCompleter(this);

        try {
            DefinitionFiles.prepare(this);
            items = new ItemRegistry(this);
            pack.start();
            items.registerRecipes();
            tools = new ToolService(this, items);
            crops = new CropService(this, items);
            stations = new CheeseStationService(this, items);
            seedGenerators = new SeedGeneratorService(this, items);
            placeableFoods = new PlaceableFoodService(this, items);
            ItemRegistry.CustomEntity capybara = items.entity("capybara");
            if (capybara != null) capybaras = new CapybaraService(this, items, capybara);
            getServer().getPluginManager().registerEvents(tools, this);
            getServer().getPluginManager().registerEvents(crops, this);
            getServer().getPluginManager().registerEvents(stations, this);
            getServer().getPluginManager().registerEvents(seedGenerators, this);
            getServer().getPluginManager().registerEvents(placeableFoods, this);
            if (capybaras != null) getServer().getPluginManager().registerEvents(capybaras, this);
            getServer().getPluginManager().registerEvents(new RecipeListener(this, items, tools), this);
            crops.start();
            stations.start();
            seedGenerators.start();
            placeableFoods.start();
            if (capybaras != null) capybaras.start();
            operational = true;
            scheduleExternalRecipeRefresh();
            getLogger().info("ExtraItems 0.8.0 bereit. Server " + Bukkit.getBukkitVersion()
                    + "; Java " + Runtime.version().feature()
                    + "; Definitionen " + items.sourceCount()
                    + "; Integrationen " + items.externalStatus());
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Konfiguration/Initialisierung fehlgeschlagen. Administratoren können den Notfallzugang nutzen.",
                    error);
            if (items != null) items.unregisterRecipes();
            if (crops != null) crops.stop();
            if (stations != null) stations.stop();
            if (seedGenerators != null) seedGenerators.stop();
            if (placeableFoods != null) placeableFoods.stop();
            if (capybaras != null) capybaras.stop();
        }

        for (Player player : Bukkit.getOnlinePlayers()) gate.request(player);
    }

    private void scheduleExternalRecipeRefresh() {
        if (items == null || !items.hasExternalReferences()) return;
        externalRecipeTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            private int attempts;
            @Override public void run() {
                if (items == null || items.externalReferencesReady() || ++attempts >= 300) {
                    if (items != null && !items.externalReferencesReady()) {
                        getLogger().warning("[Integrationen] Nicht alle externen Items waren nach fünf Minuten verfügbar.");
                    }
                    if (externalRecipeTask != null) externalRecipeTask.cancel();
                    externalRecipeTask = null;
                    return;
                }
                items.refreshRecipes();
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void providerEnabled(PluginEnableEvent event) {
        if (items != null && ExternalItemBridge.isProviderPlugin(event.getPlugin().getName())) {
            Bukkit.getScheduler().runTask(this, items::refreshRecipes);
        }
    }

    @EventHandler
    public void providerDisabled(PluginDisableEvent event) {
        if (items != null && ExternalItemBridge.isProviderPlugin(event.getPlugin().getName())) {
            items.refreshRecipes();
        }
    }

    @Override
    public void onDisable() {
        operational = false;
        if (externalRecipeTask != null) externalRecipeTask.cancel();
        if (capybaras != null) capybaras.stop();
        if (placeableFoods != null) placeableFoods.stop();
        if (seedGenerators != null) seedGenerators.stop();
        if (stations != null) stations.stop();
        if (crops != null) crops.stop();
        if (items != null) items.unregisterRecipes();
        if (pack != null) pack.close();
    }

    boolean operational() { return operational; }
    PackGate gate() { return gate; }
    public ItemRegistry items() { return items; }
    String text(String key) { return ItemRegistry.color(getConfig().getString("messages." + key, key)); }
    void message(CommandSender sender, String key) { sender.sendMessage(text(key)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("pack")) {
            if (sender instanceof Player player) gate.request(player);
            else sender.sendMessage("Dieser Befehl ist für Spieler.");
            return true;
        }
        if (!sender.hasPermission("extraitems.admin")) {
            message(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§aExtraItems 0.8.0 | " + Bukkit.getBukkitVersion()
                    + " | Java " + Runtime.version().feature());
            sender.sendMessage("§7Inhalte: " + (operational ? "bereit" : "FEHLER")
                    + " | Pack: " + (pack.ready() ? pack.modeName() + " bereit" : pack.error()));
            sender.sendMessage("§7SHA-1: " + pack.sha1());
            sender.sendMessage("§7Definitionen: " + (items == null ? 0 : items.sourceCount())
                    + " | Pflanzen: " + (crops == null ? 0 : crops.count())
                    + " | Käsestationen: " + (stations == null ? 0 : stations.count())
                    + " | Samengeneratoren: " + (seedGenerators == null ? 0 : seedGenerators.count())
                    + " | Käseräder: " + (placeableFoods == null ? 0 : placeableFoods.count())
                    + " | Capybaras (geladen): " + (capybaras == null ? 0 : capybaras.count()));
            if (capybaras != null) sender.sendMessage("§7Natürliche Capybara-Spawns: " + capybaras.naturalStatus());
            sender.sendMessage("§7Integrationen: " + (items == null ? "nicht initialisiert" : items.externalStatus()));
            if (sender instanceof Player player && pack.ready() && pack.deliveryEnabled()) {
                try {
                    sender.sendMessage("§7Deine Pack-URL: " + gate.resolvedUrl(player));
                } catch (RuntimeException error) {
                    sender.sendMessage("§cPack-URL: " + error.getMessage());
                }
            }
            return true;
        }
        if (args.length >= 2 && args.length <= 4 && args[0].equalsIgnoreCase("spawn")
                && args[1].equalsIgnoreCase("capybara")) {
            if (!operational || capybaras == null) {
                sender.sendMessage("§cDas Capybara-Modul ist nicht initialisiert.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cDieser Testbefehl muss im Spiel ausgeführt werden.");
                return true;
            }
            try {
                int amount = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
                if (amount < 1 || amount > 10) throw new NumberFormatException();
                boolean baby = args.length == 4 && args[3].equalsIgnoreCase("baby");
                if (args.length == 4 && !baby && !args[3].equalsIgnoreCase("adult")) {
                    throw new IllegalArgumentException("Alter muss adult oder baby sein.");
                }
                int spawned = capybaras.spawnAt(player.getLocation(), amount, baby);
                sender.sendMessage("§a" + spawned + " Capybara" + (spawned == 1 ? "" : "s")
                        + (baby ? " als Baby" : "") + " gespawnt.");
            } catch (NumberFormatException error) {
                sender.sendMessage("§cAnzahl muss zwischen 1 und 10 liegen.");
            } catch (IllegalArgumentException error) {
                sender.sendMessage("§c" + error.getMessage());
            }
            return true;
        }
        if (args.length >= 3 && args.length <= 4 && args[0].equalsIgnoreCase("give")) {
            if (!operational) {
                sender.sendMessage("§cExtraItems ist nicht initialisiert. Siehe Serverkonsole.");
                return true;
            }
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) {
                sender.sendMessage("§cSpieler ist nicht online.");
                return true;
            }
            try {
                int amount = args.length == 4 ? Integer.parseInt(args[3]) : 1;
                var stack = items.create(args[2], amount);
                var remaining = player.getInventory().addItem(stack);
                int leftover = remaining.values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                sender.sendMessage("§a" + (amount - leftover) + " × " + args[2] + " an "
                        + player.getName() + " vergeben."
                        + (leftover > 0 ? " §eInventar voll; " + leftover + " nicht vergeben." : ""));
            } catch (IllegalArgumentException error) {
                sender.sendMessage("§c" + error.getMessage() + " | Items: " + String.join(", ", items.ids()));
            }
            return true;
        }
        sender.sendMessage("§e/ei give <Spieler> <Item> [1–64] | /ei spawn capybara [1–10] [adult|baby] | /ei pack | /ei status");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(sender.hasPermission("extraitems.admin")
                    ? List.of("give", "spawn", "pack", "status") : List.of("pack"));
        } else if (sender.hasPermission("extraitems.admin") && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            if (args.length == 3 && items != null) options.addAll(items.ids());
            if (args.length == 4) options.addAll(List.of("1", "16", "64"));
        } else if (sender.hasPermission("extraitems.admin") && args[0].equalsIgnoreCase("spawn")) {
            if (args.length == 2) options.add("capybara");
            if (args.length == 3 && args[1].equalsIgnoreCase("capybara")) options.addAll(List.of("1", "2", "4"));
            if (args.length == 4 && args[1].equalsIgnoreCase("capybara")) options.addAll(List.of("adult", "baby"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
