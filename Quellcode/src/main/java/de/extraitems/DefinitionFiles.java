package de.extraitems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Installs, migrates and securely resolves modular content definitions.
 * The index only lists files; every definition owns its own YAML file.
 */
final class DefinitionFiles {
    static final int SCHEMA_VERSION = 2;
    private static final String MANIFEST = "definition-files.txt";

    record Definition(String type, String id, YamlConfiguration config, String source) {}

    private DefinitionFiles() {}

    static void prepare(ExtraItemsPlugin plugin) throws IOException {
        Path index = plugin.getDataFolder().toPath().resolve("items.yml");
        if (!Files.exists(index)) plugin.saveResource("items.yml", false);
        migrateLegacy(index);
        installBundled(plugin);
    }

    static List<Definition> load(ExtraItemsPlugin plugin) {
        try {
            Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            YamlConfiguration index = read(root.resolve("items.yml"));
            int schema = index.getInt("schema-version", 0);
            if (schema != SCHEMA_VERSION) {
                throw new IllegalArgumentException("items.yml: schema-version muss " + SCHEMA_VERSION + " sein");
            }
            List<?> rawSources = index.getList("sources");
            if (rawSources == null || rawSources.isEmpty()) {
                throw new IllegalArgumentException("items.yml: sources muss mindestens eine Definitionsdatei enthalten");
            }

            List<Definition> result = new ArrayList<>();
            Set<String> files = new HashSet<>();
            Set<String> definitions = new HashSet<>();
            for (Object raw : rawSources) {
                if (!(raw instanceof String source) || source.isBlank()) {
                    throw new IllegalArgumentException("items.yml: Jeder sources-Eintrag muss ein nicht leerer Pfad sein");
                }
                Path file = resolve(root, source);
                String normalized = root.relativize(file).toString().replace(File.separatorChar, '/');
                if (!files.add(normalized)) throw new IllegalArgumentException("items.yml: Pfad doppelt eingetragen: " + normalized);

                YamlConfiguration yaml = read(file);
                if (!yaml.getBoolean("enabled", true)) continue;
                String type = requireScalar(yaml, "type", normalized).toLowerCase(Locale.ROOT);
                String id = requireScalar(yaml, "id", normalized);
                ItemRegistry.checkId(id);
                if (!definitions.add(type + ":" + id)) {
                    throw new IllegalArgumentException("Definition doppelt: " + type + ":" + id);
                }
                result.add(new Definition(type, id, yaml, normalized));
            }
            return List.copyOf(result);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalArgumentException("Item-Definitionen konnten nicht geladen werden: " + e.getMessage(), e);
        }
    }

    static Path resolve(Path root, String source) throws IOException {
        if (source.indexOf('\\') >= 0 || source.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Ungültiger Definitionspfad: " + source);
        }
        Path relative = Path.of(source);
        if (relative.isAbsolute() || !source.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            throw new IllegalArgumentException("Definitionspfad muss relativ sein und auf .yml enden: " + source);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Definitionspfad verlässt den Pluginordner: " + source);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Definitionsdatei fehlt oder ist kein normales File: " + source);
        }
        if (!target.toRealPath().startsWith(root.toRealPath())) {
            throw new IllegalArgumentException("Definitionspfad verlässt den Pluginordner: " + source);
        }
        return target;
    }

    private static String requireScalar(ConfigurationSection yaml, String key, String source) {
        String value = yaml.getString(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(source + ": " + key + " fehlt");
        return value.trim();
    }

    private static YamlConfiguration read(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return yaml;
    }

    private static void installBundled(ExtraItemsPlugin plugin) throws IOException {
        try (InputStream stream = plugin.getResource(MANIFEST)) {
            if (stream == null) throw new IOException(MANIFEST + " fehlt im JAR");
            for (String source : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                source = source.trim();
                if (source.isEmpty() || source.startsWith("#")) continue;
                Path target = plugin.getDataFolder().toPath().resolve(source).normalize();
                if (!target.startsWith(plugin.getDataFolder().toPath().normalize())) {
                    throw new IOException("Ungültiger Pfad im " + MANIFEST + ": " + source);
                }
                if (!Files.exists(target)) plugin.saveResource(source, false);
            }
        }
    }

    private static void migrateLegacy(Path indexFile) throws IOException {
        YamlConfiguration legacy;
        try {
            legacy = read(indexFile);
        } catch (InvalidConfigurationException e) {
            throw new IOException("items.yml ist kein gültiges YAML", e);
        }
        if (legacy.isList("sources")) return;
        boolean oldFormat = legacy.isConfigurationSection("items")
                || legacy.isConfigurationSection("crops")
                || legacy.isConfigurationSection("recipes");
        if (!oldFormat) return;

        Path backup = indexFile.resolveSibling("items.legacy.yml");
        if (!Files.exists(backup)) Files.copy(indexFile, backup, StandardCopyOption.COPY_ATTRIBUTES);

        List<String> sources = new ArrayList<>();
        migrateSection(indexFile.getParent(), legacy.getConfigurationSection("items"), "item",
                (id, section) -> "items/" + id + "/item.yml", sources);
        migrateSection(indexFile.getParent(), legacy.getConfigurationSection("crops"), "crop",
                (id, section) -> "items/" + id + "/crop.yml", sources);
        migrateSection(indexFile.getParent(), legacy.getConfigurationSection("recipes"), "recipe",
                (id, section) -> {
                    String result = section.getString("result", "recipes");
                    ItemRegistry.checkId(result);
                    return "items/" + result + "/recipes/" + id + ".yml";
                }, sources);

        YamlConfiguration index = new YamlConfiguration();
        index.set("schema-version", SCHEMA_VERSION);
        index.set("sources", sources);
        saveAtomic(indexFile, index);
    }

    private interface TargetPath {
        String path(String id, ConfigurationSection section);
    }

    private static void migrateSection(Path root, ConfigurationSection section, String type,
                                       TargetPath targetPath, List<String> sources) throws IOException {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ItemRegistry.checkId(id);
            ConfigurationSection definition = section.getConfigurationSection(id);
            if (definition == null) throw new IOException("Ungültiger Legacy-Abschnitt: " + type + "." + id);
            String source = targetPath.path(id, definition);
            Path target = root.resolve(source).normalize();
            Files.createDirectories(target.getParent());

            if (Files.exists(target)) {
                try {
                    YamlConfiguration existing = read(target);
                    if (!type.equalsIgnoreCase(existing.getString("type", ""))
                            || !id.equals(existing.getString("id", ""))) {
                        throw new IOException("Migration würde vorhandene Datei überschreiben: " + source);
                    }
                } catch (InvalidConfigurationException e) {
                    throw new IOException("Vorhandene Definitionsdatei ist ungültig: " + source, e);
                }
            } else {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("type", type);
                yaml.set("id", id);
                for (Map.Entry<String, Object> entry : definition.getValues(true).entrySet()) {
                    if (!(entry.getValue() instanceof ConfigurationSection)) yaml.set(entry.getKey(), entry.getValue());
                }
                saveAtomic(target, yaml);
            }
            sources.add(source);
        }
    }

    private static void saveAtomic(Path target, YamlConfiguration yaml) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        yaml.save(temporary.toFile());
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
