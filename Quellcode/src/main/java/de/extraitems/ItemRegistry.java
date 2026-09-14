package de.extraitems;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** Only namespaced PDC identifies an item. Display names never grant identity. */
public final class ItemRegistry {
    public record Crop(String id, String seed, String produce, String permission,
                       List<NamespacedKey> models, int secondsPerStage, int regrowStage,
                       int light, boolean hydrated, boolean bonemeal, int minDrop, int maxDrop) {}
    public record RecipeSpec(NamespacedKey key, String result, int amount, String permission, List<String> ingredients) {}
    private final ExtraItemsPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, ItemStack> templates = new LinkedHashMap<>();
    private final Map<String, Crop> crops = new LinkedHashMap<>();
    private final Map<NamespacedKey, RecipeSpec> recipes = new LinkedHashMap<>();

    public ItemRegistry(ExtraItemsPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "item_id");
        var yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "items.yml"));
        var items = required(yaml, "items");
        for (String id : items.getKeys(false)) {
            checkId(id);
            var c = required(items, id);
            var material = Material.matchMaterial(c.getString("material", ""));
            if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("Ungültiges Material: " + id);
            var item = new ItemStack(material);
            var meta = item.getItemMeta();
            meta.setDisplayName(color(c.getString("name", id)));
            meta.setLore(c.getStringList("lore").stream().map(ItemRegistry::color).toList());
            meta.setItemModel(key(c.getString("model", "extraitems:" + id)));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
            if (c.isConfigurationSection("food")) {
                if (!material.isEdible()) throw new IllegalArgumentException("food benötigt essbares Basismaterial: " + id);
                FoodComponent food = meta.getFood();
                food.setNutrition(range(c.getInt("food.nutrition", 4), 0, 20, id));
                float saturation = (float) c.getDouble("food.saturation", 0.3);
                if (!Float.isFinite(saturation) || saturation < 0 || saturation > 20) throw new IllegalArgumentException("Ungültige Sättigung: " + id);
                food.setSaturation(saturation);
                meta.setFood(food);
            }
            item.setItemMeta(meta);
            templates.put(id, item);
        }
        var cropConfig = required(yaml, "crops");
        Set<String> seeds = new HashSet<>();
        for (String id : cropConfig.getKeys(false)) {
            checkId(id);
            var c = required(cropConfig, id);
            String seed = requireItem(c.getString("seed", "")), produce = requireItem(c.getString("produce", ""));
            if (!seeds.add(seed)) throw new IllegalArgumentException("Samen mehrfach zugeordnet: " + seed);
            var models = c.getStringList("models").stream().map(ItemRegistry::key).toList();
            if (models.size() < 2 || models.size() > 16) throw new IllegalArgumentException("2–16 Wachstumsmodelle erforderlich: " + id);
            int min = range(c.getInt("harvest-min", 1), 1, 64, id);
            crops.put(id, new Crop(id, seed, produce, permission(c.getString("plant-permission", "extraitems.plant." + id)), models,
                    range(c.getInt("seconds-per-stage", 180), 1, 86400, id), range(c.getInt("regrow-stage", 0), 0, models.size()-2, id),
                    range(c.getInt("minimum-light", 9), 0, 15, id), c.getBoolean("require-hydrated-farmland", true),
                    c.getBoolean("bonemeal", true), min, range(c.getInt("harvest-max", 3), min, 64, id)));
        }
        var recipeConfig = required(yaml, "recipes");
        for (String id : recipeConfig.getKeys(false)) {
            checkId(id);
            var c = required(recipeConfig, id);
            String result = requireItem(c.getString("result", ""));
            var ingredients = c.getStringList("ingredients");
            if (ingredients.isEmpty() || ingredients.size() > 9) throw new IllegalArgumentException("Rezept benötigt 1–9 Zutaten: " + id);
            for (String ingredient : ingredients) choice(ingredient);
            var recipeKey = new NamespacedKey(plugin, id);
            recipes.put(recipeKey, new RecipeSpec(recipeKey, result, range(c.getInt("amount", 1), 1, templates.get(result).getMaxStackSize(), id),
                    permission(c.getString("permission", "extraitems.craft." + result)), List.copyOf(ingredients)));
        }
    }

    public void registerRecipes() {
        for (var spec : recipes.values()) {
            var recipe = new ShapelessRecipe(spec.key(), create(spec.result(), spec.amount()));
            for (String ingredient : spec.ingredients()) recipe.addIngredient(choice(ingredient));
            if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Rezept-ID bereits belegt: " + spec.key());
        }
    }
    public void unregisterRecipes() { recipes.keySet().forEach(Bukkit::removeRecipe); }
    private RecipeChoice choice(String id) {
        if (id.startsWith("extraitems:")) return new RecipeChoice.ExactChoice(create(requireItem(id.substring(11)), 1));
        if (!id.startsWith("minecraft:")) throw new IllegalArgumentException("Zutat benötigt minecraft: oder extraitems: " + id);
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isItem() || m.isAir()) throw new IllegalArgumentException("Unbekannte Zutat: " + id);
        return new RecipeChoice.MaterialChoice(m);
    }
    public String id(ItemStack item) {
        return item == null || item.getType().isAir() || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }
    public String ingredientId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String custom = id(item);
        return custom == null ? item.getType().getKey().toString() : "extraitems:" + custom;
    }
    public ItemStack create(String id, int amount) {
        ItemStack result = templates.get(requireItem(id)).clone();
        result.setAmount(range(amount, 1, result.getMaxStackSize(), id));
        return result;
    }
    public ItemStack model(NamespacedKey key) {
        var item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta(); meta.setItemModel(key); item.setItemMeta(meta); return item;
    }
    public Set<String> ids() { return Collections.unmodifiableSet(templates.keySet()); }
    public Crop crop(String id) { return crops.get(id); }
    public Crop cropForSeed(ItemStack item) {
        String id = id(item);
        return id == null ? null : crops.values().stream().filter(c -> c.seed().equals(id)).findFirst().orElse(null);
    }
    public RecipeSpec recipe(Recipe recipe) { return recipe instanceof Keyed k ? recipes.get(k.getKey()) : null; }
    public Set<NamespacedKey> recipeKeys() { return Collections.unmodifiableSet(recipes.keySet()); }
    private String requireItem(String id) {
        if (!templates.containsKey(id)) throw new IllegalArgumentException("Unbekannte Item-ID: " + id);
        return id;
    }
    static String permission(String value) {
        if (value == null || value.isBlank() || !value.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Ungültiges Recht: " + value);
        return value;
    }
    static void checkId(String id) { if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Ungültige ID: " + id); }
    static int range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + ": Wert außerhalb " + min + "–" + max);
        return value;
    }
    static NamespacedKey key(String value) {
        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null) throw new IllegalArgumentException("Ungültiger Modellschlüssel: " + value);
        return key;
    }
    static ConfigurationSection required(ConfigurationSection c, String path) {
        var result = c.getConfigurationSection(path);
        if (result == null) throw new IllegalArgumentException("Konfigurationsabschnitt fehlt: " + path);
        return result;
    }
    static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
}
