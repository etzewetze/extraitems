package de.extraitems;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Loads modular content definitions. Only namespaced PDC identifies custom items. */
public final class ItemRegistry {
    public record Crop(String id, String seed, String produce, String permission,
                       List<NamespacedKey> models, int secondsPerStage, int regrowStage,
                       int light, boolean hydrated, boolean bonemeal, int minDrop, int maxDrop) {}
    public record Tool(String id, int baseUses, int usesPerUnbreakingLevel, String oldGoldBook,
                       double attackDamage, double attackSpeed) {}
    public record Station(String id, String item, String permission, Material input,
                          int processSeconds, String output, Material byproduct) {}
    public record SeedConversion(String input, String output, int amount) {}
    public record SeedGenerator(String id, String item, String permission, int processSeconds,
                                Map<String, SeedConversion> conversions) {
        SeedConversion conversion(String input) { return conversions.get(input); }
    }
    public record PlaceableFood(String id, String item, String permission, List<NamespacedKey> models,
                                int nutrition, float saturation, boolean emptyHandOnly) {}
    public record CustomEntity(String id, EntityType carrier, Material breedItem, String breedPermission,
                               List<NamespacedKey> adultModels, List<NamespacedKey> babyModels,
                               Set<String> spawnBiomes, int groupMin, int groupMax,
                               int spawnIntervalSeconds, double spawnChance,
                               int spawnDistanceMin, int spawnDistanceMax,
                               int maxLoadedPerWorld, int maxNearby,
                               int babyGrowthTicks, int feedGrowthTicks) {}
    public record RecipeSpec(NamespacedKey key, String result, int amount, String permission,
                             List<String> ingredients, List<String> shape, Map<Character, String> keys,
                             String tool, int toolDamage) {
        boolean shaped() { return !shape.isEmpty(); }
        boolean damagesTool() { return tool != null; }
    }

    private static final Set<String> FUTURE_TYPES = Set.of("potion", "effect", "gui", "tree", "ore");
    private static final Set<String> ACTIVE_TYPES = Set.of(
            "item", "tool", "crop", "recipe", "station", "seed_generator", "placeable_food", "entity");
    private final ExtraItemsPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, ItemStack> templates = new LinkedHashMap<>();
    private final Map<String, Crop> crops = new LinkedHashMap<>();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, SeedGenerator> seedGenerators = new LinkedHashMap<>();
    private final Map<String, PlaceableFood> placeableFoods = new LinkedHashMap<>();
    private final Map<String, CustomEntity> entities = new LinkedHashMap<>();
    private final Map<NamespacedKey, RecipeSpec> recipes = new LinkedHashMap<>();
    private final int sourceCount;
    private final ExternalItemBridge external;
    private boolean recipesRegistered;
    private boolean recipesComplete;
    private final Set<NamespacedKey> registeredRecipeKeys = new LinkedHashSet<>();

    public ItemRegistry(ExtraItemsPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "item_id");
        external = new ExternalItemBridge(plugin);
        List<DefinitionFiles.Definition> definitions = DefinitionFiles.load(plugin);
        sourceCount = definitions.size();

        loadType(definitions, "item", this::loadItem);
        loadType(definitions, "tool", this::loadTool);
        loadType(definitions, "crop", this::loadCrop);
        loadType(definitions, "station", this::loadStation);
        loadType(definitions, "seed_generator", this::loadSeedGenerator);
        loadType(definitions, "placeable_food", this::loadPlaceableFood);
        loadType(definitions, "entity", this::loadEntity);
        loadType(definitions, "recipe", this::loadRecipe);

        for (DefinitionFiles.Definition definition : definitions) {
            String type = definition.type();
            if (!ACTIVE_TYPES.contains(type)) {
                String detail = FUTURE_TYPES.contains(type)
                        ? " ist für ein zukünftiges Modul reserviert, aber noch nicht implementiert"
                        : " ist unbekannt";
                throw new IllegalArgumentException(definition.source() + ": Typ '" + type + "'" + detail);
            }
        }
    }

    private void loadType(List<DefinitionFiles.Definition> definitions, String type,
                          java.util.function.Consumer<DefinitionFiles.Definition> loader) {
        definitions.stream().filter(d -> d.type().equals(type)).forEach(definition -> {
            try {
                loader.accept(definition);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(definition.source() + ": " + error.getMessage(), error);
            }
        });
    }

    private void loadItem(DefinitionFiles.Definition definition) {
        putTemplate(definition.id(), template(definition.id(), definition.config()));
    }

    private void loadTool(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        ItemStack item = template(id, c);
        int baseUses = range(c.getInt("base-uses", 192), 1, 100000, id);
        int bonus = range(c.getInt("uses-per-unbreaking-level", 64), 0, 100000, id);
        double attackDamage = finiteDouble(c.getDouble("attack-damage", 1.0), 0, 100, id);
        double attackSpeed = finiteDouble(c.getDouble("attack-speed", 4.0), 0.1, 20, id);
        String book = c.getString("old-but-gold-book", "old_but_gold_book");
        if (book != null && !book.isBlank()) requireItem(book);
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            throw new IllegalArgumentException("Werkzeugmaterial besitzt keine Haltbarkeit: " + id);
        }
        damageable.setMaxDamage(baseUses);
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        CombatAttributes.apply(item, plugin, id, attackDamage, attackSpeed);
        putTemplate(id, item);
        tools.put(id, new Tool(id, baseUses, bonus, book, attackDamage, attackSpeed));
    }

    private ItemStack template(String id, ConfigurationSection c) {
        Material material = Material.matchMaterial(c.getString("material", ""));
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException("Ungültiges Material für " + id);
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(c.getString("name", id)));
        meta.setLore(c.getStringList("lore").stream().map(ItemRegistry::color).toList());
        meta.setItemModel(key(c.getString("model", "extraitems:" + id)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        if (c.getBoolean("glint", false)) meta.setEnchantmentGlintOverride(true);
        if (c.isConfigurationSection("food")) {
            if (!material.isEdible()) throw new IllegalArgumentException("food benötigt ein essbares Basismaterial: " + id);
            FoodComponent food = meta.getFood();
            food.setNutrition(range(c.getInt("food.nutrition", 4), 0, 20, id));
            float saturation = finiteFloat(c.getDouble("food.saturation", 0.3), 0, 20, id);
            food.setSaturation(saturation);
            meta.setFood(food);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void putTemplate(String id, ItemStack item) {
        if (templates.putIfAbsent(id, item) != null) throw new IllegalArgumentException("Item-ID doppelt: " + id);
    }

    private void loadCrop(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        String seed = requireItem(c.getString("seed", ""));
        String produce = requireItem(c.getString("produce", ""));
        if (crops.values().stream().anyMatch(crop -> crop.seed().equals(seed))) {
            throw new IllegalArgumentException("Samen mehrfach zugeordnet: " + seed);
        }
        List<NamespacedKey> models = modelList(c, id, 2, 16);
        int min = range(c.getInt("harvest-min", 1), 1, 64, id);
        Crop crop = new Crop(id, seed, produce,
                permission(c.getString("plant-permission", "extraitems.plant." + id)), models,
                range(c.getInt("seconds-per-stage", 180), 1, 86400, id),
                range(c.getInt("regrow-stage", 0), 0, models.size() - 2, id),
                range(c.getInt("minimum-light", 9), 0, 15, id),
                c.getBoolean("require-hydrated-farmland", true),
                c.getBoolean("bonemeal", true), min,
                range(c.getInt("harvest-max", 3), min, 64, id));
        if (crops.putIfAbsent(id, crop) != null) throw new IllegalArgumentException("Pflanzen-ID doppelt: " + id);
    }

    private void loadStation(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        String item = requireItem(c.getString("item", id));
        Material input = Material.matchMaterial(c.getString("input-material", ""));
        Material byproduct = Material.matchMaterial(c.getString("byproduct-material", ""));
        if (input == null || !input.isItem() || input.isAir()) throw new IllegalArgumentException("Ungültiger Stationseingang: " + id);
        if (byproduct == null || !byproduct.isItem() || byproduct.isAir()) throw new IllegalArgumentException("Ungültiges Nebenprodukt: " + id);
        Station station = new Station(id, item,
                permission(c.getString("use-permission", "extraitems.use." + id)), input,
                range(c.getInt("process-seconds", 60), 1, 86400, id),
                requireItem(c.getString("output", "")), byproduct);
        if (stations.putIfAbsent(id, station) != null) throw new IllegalArgumentException("Stations-ID doppelt: " + id);
    }

    private void loadSeedGenerator(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        ConfigurationSection raw = c.getConfigurationSection("conversions");
        if (raw == null || raw.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("conversions benötigt mindestens einen Eintrag: " + id);
        }
        Map<String, SeedConversion> conversions = new LinkedHashMap<>();
        for (String inputId : raw.getKeys(false)) {
            String input = requireItem(inputId);
            ConfigurationSection conversion = raw.getConfigurationSection(inputId);
            if (conversion == null) throw new IllegalArgumentException("Ungültige Umwandlung: " + inputId);
            String output = requireItem(conversion.getString("output", ""));
            int amount = range(conversion.getInt("amount", 1), 1, stackLimit(output), inputId);
            conversions.put(input, new SeedConversion(input, output, amount));
        }
        SeedGenerator generator = new SeedGenerator(id, requireItem(c.getString("item", id)),
                permission(c.getString("use-permission", "extraitems.use." + id)),
                range(c.getInt("process-seconds", 30), 1, 86400, id),
                Collections.unmodifiableMap(conversions));
        if (seedGenerators.putIfAbsent(id, generator) != null) {
            throw new IllegalArgumentException("Samengenerator-ID doppelt: " + id);
        }
    }

    private void loadPlaceableFood(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        PlaceableFood food = new PlaceableFood(id, requireItem(c.getString("item", id)),
                permission(c.getString("place-permission", "extraitems.place." + id)),
                modelList(c, id, 1, 64),
                range(c.getInt("nutrition-per-portion", 2), 1, 20, id),
                finiteFloat(c.getDouble("saturation-per-portion", .3), 0, 20, id),
                c.getBoolean("empty-hand-only", true));
        if (placeableFoods.putIfAbsent(id, food) != null) throw new IllegalArgumentException("Platzierbares Essen doppelt: " + id);
    }

    private void loadEntity(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        EntityType carrier;
        try {
            carrier = EntityType.valueOf(c.getString("carrier", "PIG").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Ungültige Trägerentity: " + id, error);
        }
        if (carrier != EntityType.PIG) {
            throw new IllegalArgumentException("Dieses Modul unterstützt derzeit nur PIG als Trägerentity: " + id);
        }
        Material breedItem = Material.matchMaterial(c.getString("breed-material", ""));
        if (breedItem == null || !breedItem.isItem() || breedItem.isAir()) {
            throw new IllegalArgumentException("Ungültiges Zuchtitem: " + id);
        }
        List<NamespacedKey> adult = modelList(c, id, "adult-models", 1, 16);
        List<NamespacedKey> baby = modelList(c, id, "baby-models", 1, 16);
        if (adult.size() != baby.size()) {
            throw new IllegalArgumentException("adult-models und baby-models benötigen gleich viele Varianten: " + id);
        }
        LinkedHashSet<String> biomes = new LinkedHashSet<>();
        for (String raw : c.getStringList("spawn-biomes")) {
            NamespacedKey biome = key(raw);
            biomes.add(biome.toString());
        }
        if (biomes.isEmpty()) throw new IllegalArgumentException("spawn-biomes fehlt: " + id);
        int groupMin = range(c.getInt("group-min", 2), 1, 16, id);
        int distanceMin = range(c.getInt("spawn-distance-min", 24), 8, 128, id);
        CustomEntity entity = new CustomEntity(id, carrier, breedItem,
                permission(c.getString("breed-permission", "extraitems.breed." + id)),
                adult, baby, Collections.unmodifiableSet(biomes), groupMin,
                range(c.getInt("group-max", 4), groupMin, 16, id),
                range(c.getInt("spawn-interval-seconds", 30), 5, 3600, id),
                finiteDouble(c.getDouble("spawn-chance", .18), 0, 1, id),
                distanceMin, range(c.getInt("spawn-distance-max", 48), distanceMin, 256, id),
                range(c.getInt("max-loaded-per-world", 36), 1, 1000, id),
                range(c.getInt("max-near-player", 8), 1, 128, id),
                range(c.getInt("baby-growth-ticks", 24000), 1200, 240000, id),
                range(c.getInt("feed-growth-ticks", 2400), 20, 24000, id));
        if (entities.putIfAbsent(id, entity) != null) {
            throw new IllegalArgumentException("Entity-ID doppelt: " + id);
        }
    }

    private List<NamespacedKey> modelList(ConfigurationSection c, String id, int min, int max) {
        return modelList(c, id, "models", min, max);
    }

    private List<NamespacedKey> modelList(ConfigurationSection c, String id, String path, int min, int max) {
        List<NamespacedKey> models = c.getStringList(path).stream().map(ItemRegistry::key).toList();
        if (models.size() < min || models.size() > max) {
            throw new IllegalArgumentException(path + ": " + min + "–" + max + " Modelle erforderlich: " + id);
        }
        return models;
    }

    private void loadRecipe(DefinitionFiles.Definition definition) {
        String id = definition.id();
        ConfigurationSection c = definition.config();
        String result = requireRecipeItem(c.getString("result", ""));
        List<String> shape = List.copyOf(c.getStringList("shape"));
        Map<Character, String> keys = new LinkedHashMap<>();
        List<String> ingredients = new ArrayList<>();

        if (!shape.isEmpty()) {
            if (shape.size() > 3 || shape.stream().anyMatch(row -> row.isEmpty() || row.length() > 3)
                    || shape.stream().map(String::length).distinct().count() != 1) {
                throw new IllegalArgumentException("Rezeptform benötigt 1–3 gleich breite Zeilen: " + id);
            }
            ConfigurationSection rawKeys = c.getConfigurationSection("keys");
            if (rawKeys == null) throw new IllegalArgumentException("keys fehlt für geformtes Rezept: " + id);
            for (String raw : rawKeys.getKeys(false)) {
                if (raw.length() != 1 || raw.charAt(0) == ' ') throw new IllegalArgumentException("Ungültiges Rezeptzeichen: " + raw);
                String ingredient = rawKeys.getString(raw, "");
                validateChoice(ingredient);
                keys.put(raw.charAt(0), ingredient);
            }
            for (String row : shape) for (char symbol : row.toCharArray()) {
                if (symbol == ' ') continue;
                String ingredient = keys.get(symbol);
                if (ingredient == null) throw new IllegalArgumentException("Rezeptzeichen ohne keys-Eintrag: " + symbol);
                ingredients.add(ingredient);
            }
        } else {
            ingredients.addAll(c.getStringList("ingredients"));
            if (ingredients.isEmpty()) throw new IllegalArgumentException("Rezept benötigt Zutaten: " + id);
            for (String ingredient : ingredients) validateChoice(ingredient);
        }

        String tool = c.getString("tool");
        int toolDamage = 0;
        if (tool != null && !tool.isBlank()) {
            if (!shape.isEmpty()) throw new IllegalArgumentException("Werkzeugrezepte müssen formlos sein: " + id);
            requireTool(tool);
            toolDamage = range(c.getInt("tool-damage", 1), 1, 1000, id);
            ingredients.add("extraitems:" + tool);
        } else tool = null;
        if (ingredients.size() > 9) throw new IllegalArgumentException("Rezept benötigt höchstens 9 Zutaten: " + id);

        NamespacedKey recipeKey = new NamespacedKey(plugin, id);
        RecipeSpec spec = new RecipeSpec(recipeKey, result,
                range(c.getInt("amount", 1), 1, stackLimit(result), id),
                permission(c.getString("permission",
                        external.isReference(result) ? "extraitems.craft.external" : "extraitems.craft." + result)),
                List.copyOf(ingredients), shape, Collections.unmodifiableMap(keys), tool, toolDamage);
        if (recipes.putIfAbsent(recipeKey, spec) != null) throw new IllegalArgumentException("Rezept-ID bereits belegt: " + id);
    }

    public void registerRecipes() {
        registerRecipes(false);
    }

    private void registerRecipes(boolean force) {
        if (recipesRegistered && !force) return;
        if (force) {
            registeredRecipeKeys.forEach(Bukkit::removeRecipe);
            registeredRecipeKeys.clear();
        }
        boolean complete = true;
        for (RecipeSpec spec : recipes.values()) {
            try {
                Recipe recipe;
                if (spec.shaped()) {
                    ShapedRecipe shaped = new ShapedRecipe(spec.key(), create(spec.result(), spec.amount()));
                    shaped.shape(spec.shape().toArray(String[]::new));
                    spec.keys().forEach((symbol, ingredient) -> shaped.setIngredient(symbol, choice(ingredient, null)));
                    recipe = shaped;
                } else {
                    ShapelessRecipe shapeless = new ShapelessRecipe(spec.key(), create(spec.result(), spec.amount()));
                    for (String ingredient : spec.ingredients()) shapeless.addIngredient(choice(ingredient, spec.tool()));
                    recipe = shapeless;
                }
                if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Rezept-ID bereits belegt: " + spec.key());
                registeredRecipeKeys.add(spec.key());
            } catch (ExternalItemBridge.Unavailable unavailable) {
                complete = false;
                plugin.getLogger().warning("[Integrationen] Rezept " + spec.key()
                        + " wartet auf " + unavailable.getMessage());
            }
        }
        recipesRegistered = true;
        recipesComplete = complete;
    }

    public void refreshRecipes() {
        registerRecipes(true);
    }

    public void unregisterRecipes() {
        registeredRecipeKeys.forEach(Bukkit::removeRecipe);
        registeredRecipeKeys.clear();
        recipesRegistered = false;
        recipesComplete = false;
    }

    private RecipeChoice choice(String id, String flexibleTool) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Leere Rezeptzutat");
        if (id.equals("minecraft:#planks")) return new RecipeChoice.MaterialChoice(Tag.PLANKS);
        if (external.isReference(id)) {
            ItemStack resolved = external.resolve(id, 1);
            return new RecipeChoice.MaterialChoice(resolved.getType());
        }
        if (id.startsWith("extraitems:")) {
            String custom = requireItem(id.substring(11));
            return new RecipeChoice.MaterialChoice(templates.get(custom).getType());
        }
        if (!id.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Zutat benötigt minecraft:, extraitems: oder provider: " + id);
        }
        Material material = Material.matchMaterial(id);
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException("Unbekannte Zutat: " + id);
        }
        return new RecipeChoice.MaterialChoice(material);
    }

    private void validateChoice(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Leere Rezeptzutat");
        if (id.equals("minecraft:#planks")) return;
        if (external.isReference(id)) return;
        choice(id, null);
    }

    public String id(ItemStack item) {
        return item == null || item.getType().isAir() || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public String ingredientId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String custom = id(item);
        if (custom != null) return "extraitems:" + custom;
        String externalToken = external.stateToken(item);
        if (externalToken != null) return externalToken;
        return materialKey(item.getType());
    }

    public String ingredientId(ItemStack item, Collection<String> expected) {
        if (item == null || item.getType().isAir()) return null;
        for (String candidate : expected) {
            if (external.isReference(candidate) && external.matches(item, candidate)) {
                return candidate.trim().toLowerCase(Locale.ROOT);
            }
        }
        if (expected.contains("minecraft:#planks") && Tag.PLANKS.isTagged(item.getType())) {
            return "minecraft:#planks";
        }
        return ingredientId(item);
    }

    public ItemStack create(String id, int amount) {
        String token = requireRecipeItem(id);
        if (external.isReference(token)) return external.resolve(token, amount);
        ItemStack result = templates.get(token).clone();
        result.setAmount(range(amount, 1, result.getMaxStackSize(), id));
        return result;
    }

    private int stackLimit(String id) {
        if (external.isReference(id)) return 64;
        return templates.get(id).getMaxStackSize();
    }

    public ItemStack model(NamespacedKey key) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(key);
        item.setItemMeta(meta);
        return item;
    }

    public Set<String> ids() { return Collections.unmodifiableSet(templates.keySet()); }
    public int sourceCount() { return sourceCount; }
    public Crop crop(String id) { return crops.get(id); }
    public Collection<Station> stations() { return Collections.unmodifiableCollection(stations.values()); }
    public Collection<SeedGenerator> seedGenerators() { return Collections.unmodifiableCollection(seedGenerators.values()); }
    public Collection<PlaceableFood> placeableFoods() { return Collections.unmodifiableCollection(placeableFoods.values()); }
    public Collection<CustomEntity> entities() { return Collections.unmodifiableCollection(entities.values()); }
    public Tool tool(String id) { return tools.get(id); }
    public Tool tool(ItemStack item) { String id = id(item); return id == null ? null : tools.get(id); }
    public Station station(String id) { return stations.get(id); }
    public SeedGenerator seedGenerator(String id) { return seedGenerators.get(id); }
    public PlaceableFood placeableFood(String id) { return placeableFoods.get(id); }
    public CustomEntity entity(String id) { return entities.get(id); }

    public Crop cropForSeed(ItemStack item) {
        String id = id(item);
        return id == null ? null : crops.values().stream().filter(c -> c.seed().equals(id)).findFirst().orElse(null);
    }
    public Station stationForItem(ItemStack item) {
        String id = id(item);
        return id == null ? null : stations.values().stream().filter(s -> s.item().equals(id)).findFirst().orElse(null);
    }
    public SeedGenerator seedGeneratorForItem(ItemStack item) {
        String id = id(item);
        return id == null ? null : seedGenerators.values().stream()
                .filter(generator -> generator.item().equals(id)).findFirst().orElse(null);
    }
    public PlaceableFood placeableFoodForItem(ItemStack item) {
        String id = id(item);
        return id == null ? null : placeableFoods.values().stream().filter(f -> f.item().equals(id)).findFirst().orElse(null);
    }

    public RecipeSpec recipe(Recipe recipe) { return recipe instanceof Keyed keyed ? recipes.get(keyed.getKey()) : null; }

    /** Resolves registered recipes and repairs a missing Bukkit match for shapeless custom recipes. */
    public RecipeSpec recipe(Recipe recipe, ItemStack[] matrix) {
        RecipeSpec keyed = recipe(recipe);
        if (keyed != null) return keyed;
        boolean containsCustom = Arrays.stream(matrix).anyMatch(item -> id(item) != null || external.isKnownCustom(item));
        if (!containsCustom) return null;
        for (RecipeSpec candidate : recipes.values()) {
            if (candidate.shaped()) continue;
            List<String> actual = Arrays.stream(matrix)
                    .map(item -> ingredientId(item, candidate.ingredients()))
                    .toList();
            if (CraftPolicy.ingredientsMatch(candidate.ingredients(), actual)) return candidate;
        }
        return null;
    }
    public Set<NamespacedKey> recipeKeys() { return Collections.unmodifiableSet(recipes.keySet()); }

    ExternalItemBridge external() { return external; }
    boolean isExternalReference(String token) { return external.isReference(token); }
    String externalStatus() { return external.status(); }

    boolean hasExternalReferences() {
        for (RecipeSpec spec : recipes.values()) {
            if (external.isReference(spec.result())
                    || spec.ingredients().stream().anyMatch(external::isReference)) return true;
        }
        return false;
    }

    boolean externalReferencesReady() {
        for (RecipeSpec spec : recipes.values()) {
            List<String> values = new ArrayList<>(spec.ingredients());
            values.add(spec.result());
            for (String value : values) {
                if (!external.isReference(value)) continue;
                try {
                    external.resolve(value, 1);
                } catch (ExternalItemBridge.Unavailable error) {
                    return false;
                }
            }
        }
        return true;
    }

    private String requireRecipeItem(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Leeres Rezeptresultat");
        if (external.isReference(id)) return id.trim();
        if (id.startsWith("extraitems:")) return requireItem(id.substring(11));
        return requireItem(id);
    }

    private String requireItem(String id) {
        if (id == null || !templates.containsKey(id)) throw new IllegalArgumentException("Unbekannte Item-ID: " + id);
        return id;
    }

    private Tool requireTool(String id) {
        Tool tool = tools.get(id);
        if (tool == null) throw new IllegalArgumentException("Unbekannte Werkzeug-ID: " + id);
        return tool;
    }

    static String permission(String value) {
        if (value == null || value.isBlank() || !value.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Ungültiges Recht: " + value);
        return value;
    }
    static void checkId(String id) {
        if (id == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Ungültige ID: " + id);
    }
    static int range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + ": Wert außerhalb " + min + "–" + max);
        return value;
    }
    static float finiteFloat(double value, float min, float max, String name) {
        float result = (float) value;
        if (!Float.isFinite(result) || result < min || result > max) throw new IllegalArgumentException("Ungültiger Wert: " + name);
        return result;
    }
    static double finiteDouble(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Ungültiger Wert: " + name);
        }
        return value;
    }
    static NamespacedKey key(String value) {
        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null) throw new IllegalArgumentException("Ungültiger Modellschlüssel: " + value);
        return key;
    }
    /** Compatibility boundary for APIs predating Keyed#getKeyOrThrow. */
    @SuppressWarnings("deprecation")
    private static String materialKey(Material material) { return material.getKey().toString(); }
    static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
}
