package de.extraitems;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class ExtraItemsPlugin extends JavaPlugin implements TabExecutor {
    private PackService pack;
    private PackGate gate;
    private ItemRegistry items;
    private CropService crops;
    private boolean operational;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "items.yml").exists()) saveResource("items.yml", false);
        pack = new PackService(this);
        gate = new PackGate(this, pack);
        getServer().getPluginManager().registerEvents(gate,this);
        Objects.requireNonNull(getCommand("extraitems")).setExecutor(this);
        Objects.requireNonNull(getCommand("extraitems")).setTabCompleter(this);
        pack.start();
        try {
            items = new ItemRegistry(this);
            items.registerRecipes();
            crops = new CropService(this,items);
            getServer().getPluginManager().registerEvents(crops,this);
            getServer().getPluginManager().registerEvents(new RecipeListener(this,items),this);
            crops.start();
            operational = true;
            getLogger().info("ExtraItems 0.1.0 bereit. Server " + Bukkit.getBukkitVersion() + "; Java " + Runtime.version().feature());
        } catch (RuntimeException e) {
            getLogger().log(java.util.logging.Level.SEVERE,"Konfiguration/Initialisierung fehlgeschlagen; Beitritt bleibt gesperrt. Fehler korrigieren und Server neu starten.",e);
            if (items != null) items.unregisterRecipes();
            if (crops != null) crops.stop();
        }
        for (Player player : Bukkit.getOnlinePlayers()) gate.request(player);
    }
    @Override public void onDisable() {
        operational = false;
        if (crops != null) crops.stop();
        if (items != null) items.unregisterRecipes();
        if (pack != null) pack.close();
    }
    boolean operational() { return operational; }
    PackGate gate() { return gate; }
    public ItemRegistry items() { return items; }
    String text(String key) { return ItemRegistry.color(getConfig().getString("messages."+key,key)); }
    void message(CommandSender sender, String key) { sender.sendMessage(text(key)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("pack")) {
            if (sender instanceof Player player) gate.request(player);
            else sender.sendMessage("Dieser Befehl ist für Spieler.");
            return true;
        }
        if (!sender.hasPermission("extraitems.admin")) { message(sender,"no-permission"); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§aExtraItems 0.1.0 | " + Bukkit.getBukkitVersion() + " | Java " + Runtime.version().feature());
            sender.sendMessage("§7Inhalte: " + (operational ? "bereit" : "FEHLER") + " | Pack: " + (pack.ready() ? "bereit" : pack.error()));
            sender.sendMessage("§7SHA-1: " + pack.sha1());
            sender.sendMessage("§7Geladene Pflanzen: " + (crops == null ? 0 : crops.count()));
            return true;
        }
        if (args.length >= 3 && args.length <= 4 && args[0].equalsIgnoreCase("give")) {
            if (!operational) { sender.sendMessage("§cExtraItems ist nicht initialisiert. Siehe Serverkonsole."); return true; }
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage("§cSpieler ist nicht online."); return true; }
            try {
                int amount = args.length == 4 ? Integer.parseInt(args[3]) : 1;
                var stack = items.create(args[2],amount);
                var remaining = player.getInventory().addItem(stack);
                int leftover = remaining.values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                sender.sendMessage("§a" + (amount-leftover) + " × " + args[2] + " an " + player.getName() + " vergeben." + (leftover > 0 ? " §eInventar voll; " + leftover + " nicht vergeben." : ""));
            } catch (IllegalArgumentException e) { sender.sendMessage("§c" + e.getMessage() + " | Items: " + String.join(", ",items.ids())); }
            return true;
        }
        sender.sendMessage("§e/ei give <Spieler> <Item> [1–64] | /ei pack | /ei status");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(sender.hasPermission("extraitems.admin") ? List.of("give","pack","status") : List.of("pack"));
        else if (sender.hasPermission("extraitems.admin") && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            if (args.length == 3 && items != null) options.addAll(items.ids());
            if (args.length == 4) options.addAll(List.of("1","16","64"));
        }
        String prefix = args.length == 0 ? "" : args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
