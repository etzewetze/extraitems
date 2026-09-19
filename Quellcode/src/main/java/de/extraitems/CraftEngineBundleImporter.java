package de.extraitems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Converts Nexo Maker CraftEngine exports into native ExtraItems definitions and a pack overlay. */
final class CraftEngineBundleImporter implements AutoCloseable {
    static final int MAX_ENTRIES = 4096;
    static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    static final String OVERLAY = "generated/imported-resourcepack";

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");

    private final Path dataRoot;
    private final Path generatedRoot;
    private final Path target;
    private final Path staging;
    private final Map<String, String> assetOwners = new LinkedHashMap<>();
    private boolean committed;

    private CraftEngineBundleImporter(Path dataRoot) throws IOException {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
        Files.createDirectories(this.dataRoot);
        Path realRoot = this.dataRoot.toRealPath();
        generatedRoot = this.dataRoot.resolve("generated").normalize();
        if (Files.isSymbolicLink(generatedRoot)) {
            throw new IOException("generated darf kein Symlink sein");
        }
        Files.createDirectories(generatedRoot);
        if (!generatedRoot.toRealPath().startsWith(realRoot)) {
            throw new IOException("generated verlässt den Pluginordner");
        }
        target = generatedRoot.resolve("imported-resourcepack");
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Der Import-Overlay darf kein Symlink sein");
        }
        staging = Files.createTempDirectory(generatedRoot, ".craftengine-import-");
    }

    static CraftEngineBundleImporter open(Path dataRoot) throws IOException {
        return new CraftEngineBundleImporter(dataRoot);
    }

    List<DefinitionFiles.Definition> importSource(Path source, String sourceLabel)
            throws IOException, InvalidConfigurationException {
        Map<String, byte[]> files = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                ? readDirectory(source) : readZip(source);
        Map<String, List<String>> configurations = configurationFiles(files.keySet());
        if (configurations.isEmpty()) {
            throw new IllegalArgumentException(sourceLabel
                    + ": Kein CraftEngine/resources-Paket mit configuration/*.yml gefunden");
        }

        List<DefinitionFiles.Definition> definitions = new ArrayList<>();
        for (Map.Entry<String, List<String>> bundle : configurations.entrySet()) {
            String root = bundle.getKey();
            String assetsPrefix = root.isEmpty() ? "resourcepack/assets/" : root + "/resourcepack/assets/";
            boolean hasAssets = false;
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                if (!file.getKey().startsWith(assetsPrefix)) continue;
                String relative = file.getKey().substring(assetsPrefix.length());
                if (relative.isEmpty()) continue;
                validateAssetPath(relative, sourceLabel);
                writeAsset("assets/" + relative, file.getValue(), sourceLabel + "!" + file.getKey());
                hasAssets = true;
            }
            if (!hasAssets) {
                throw new IllegalArgumentException(sourceLabel + ": resourcepack/assets fehlt für "
                        + (root.isEmpty() ? "das Exportpaket" : root));
            }
            for (String configuration : bundle.getValue()) {
                definitions.addAll(readItems(files.get(configuration), sourceLabel + "!" + configuration));
            }
        }
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException(sourceLabel + ": Der CraftEngine-Export enthält keine items-Definition");
        }
        return List.copyOf(definitions);
    }

    void commit() throws IOException {
        if (committed) throw new IllegalStateException("Import-Overlay wurde bereits veröffentlicht");
        Path backup = generatedRoot.resolve("imported-resourcepack.previous");
        ensureManaged(backup);
        deleteTree(backup);
        boolean movedOld = false;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target)) throw new IOException("Der Import-Overlay darf kein Symlink sein");
            move(target, backup);
            movedOld = true;
        }
        try {
            move(staging, target);
            committed = true;
        } catch (IOException error) {
            if (movedOld && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) move(backup, target);
            throw error;
        }
        deleteTree(backup);
    }

    @Override
    public void close() throws IOException {
        if (!committed) deleteTree(staging);
    }

    private List<DefinitionFiles.Definition> readItems(byte[] bytes, String source)
            throws InvalidConfigurationException, IOException {
        YamlConfiguration document = new YamlConfiguration();
        document.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        ConfigurationSection items = document.getConfigurationSection("items");
        if (items == null) return List.of(); // Category-only files are valid CraftEngine configuration files.

        List<DefinitionFiles.Definition> definitions = new ArrayList<>();
        for (String externalId : new TreeSet<>(items.getKeys(false))) {
            ConfigurationSection item = items.getConfigurationSection(externalId);
            if (item == null) throw new IllegalArgumentException(source + ": Ungültiges Item " + externalId);
            ResourceId itemId = resourceId(externalId, source + "#items." + externalId);
            if (itemId.namespace().equals("minecraft")) {
                throw new IllegalArgumentException(source + ": Der Item-Namespace minecraft ist reserviert: " + externalId);
            }
            String internalId = internalId(itemId);
            String material = required(item, "material", source + "#items." + externalId);
            String modelType = item.getString("model.type", "minecraft:model");
            if (!"minecraft:model".equals(modelType)) {
                throw new IllegalArgumentException(source + "#items." + externalId
                        + ": Nur model.type minecraft:model wird unterstützt");
            }
            ResourceId model = resourceId(required(item, "model.path", source + "#items." + externalId),
                    source + "#items." + externalId + ".model.path");

            ConfigurationSection generation = item.getConfigurationSection("model.generation");
            String geometry = "assets/" + model.namespace() + "/models/" + model.path() + ".json";
            if (generation != null && !assetOwners.containsKey(geometry)) {
                writeAsset(geometry, json(sectionMap(generation)).getBytes(StandardCharsets.UTF_8),
                        source + "#items." + externalId + ".model.generation");
            } else if (!model.namespace().equals("minecraft") && !assetOwners.containsKey(geometry)) {
                throw new IllegalArgumentException(source + "#items." + externalId
                        + ": Modell fehlt im Export und model.generation ist nicht gesetzt: " + model);
            }

            String itemModel = "assets/" + itemId.namespace() + "/items/" + itemId.path() + ".json";
            if (!assetOwners.containsKey(itemModel)) {
                String wrapper = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n"
                        + "    \"model\": \"" + model + "\"\n  }\n}\n";
                writeAsset(itemModel, wrapper.getBytes(StandardCharsets.UTF_8),
                        source + "#items." + externalId + ".generated-item-model");
            }

            YamlConfiguration converted = new YamlConfiguration();
            converted.set("type", "item");
            converted.set("id", internalId);
            converted.set("material", material);
            converted.set("name", item.getString("data.item_name", readableName(itemId.path())));
            converted.set("model", itemId.toString());
            List<String> lore = item.getStringList("data.lore");
            if (!lore.isEmpty()) converted.set("lore", lore);
            if (item.contains("data.enchantment_glint_override")) {
                converted.set("glint", item.getBoolean("data.enchantment_glint_override"));
            }
            definitions.add(new DefinitionFiles.Definition("item", internalId, converted,
                    source + "#items." + externalId));
        }
        return definitions;
    }

    private void writeAsset(String relative, byte[] bytes, String owner) throws IOException {
        validateEntryName(relative);
        if (!relative.startsWith("assets/")) {
            throw new IllegalArgumentException("Importdatei liegt außerhalb von assets/: " + relative);
        }
        String previous = assetOwners.get(relative);
        Path destination = staging.resolve(relative).normalize();
        if (!destination.startsWith(staging)) throw new IllegalArgumentException("Ungültiger Assetpfad: " + relative);
        if (previous != null) {
            if (!Arrays.equals(Files.readAllBytes(destination), bytes)) {
                throw new IllegalArgumentException("Asset-Konflikt " + relative + " zwischen " + previous + " und " + owner);
            }
            return;
        }
        Files.createDirectories(destination.getParent());
        Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
        assetOwners.put(relative, owner);
    }

    private static Map<String, List<String>> configurationFiles(Set<String> names) {
        Map<String, List<String>> result = new TreeMap<>();
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".yml") || lower.endsWith(".yaml"))) continue;
            int marker = name.indexOf("configuration/");
            if (marker < 0 || (marker > 0 && name.charAt(marker - 1) != '/')) continue;
            String root = marker == 0 ? "" : name.substring(0, marker - 1);
            if (name.substring(marker + "configuration/".length()).isEmpty()) continue;
            result.computeIfAbsent(root, ignored -> new ArrayList<>()).add(name);
        }
        result.values().forEach(Collections::sort);
        return result;
    }

    private static Map<String, byte[]> readZip(Path source) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (InputStream input = Files.newInputStream(source); ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String rawName = entry.getName();
                if (entry.isDirectory()) {
                    String directoryName = rawName.endsWith("/")
                            ? rawName.substring(0, rawName.length() - 1) : rawName;
                    validateEntryName(directoryName);
                    continue;
                }
                String name = validateEntryName(rawName);
                if (files.size() >= MAX_ENTRIES) throw new IOException("Import enthält mehr als " + MAX_ENTRIES + " Dateien");
                byte[] bytes = readLimited(zip, name);
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) throw new IOException("Import ist entpackt größer als 64 MiB");
                if (files.putIfAbsent(name, bytes) != null) throw new IOException("Doppelter ZIP-Eintrag: " + name);
            }
        } catch (ZipException error) {
            throw new IOException("Ungültige ZIP-Datei: " + source.getFileName(), error);
        }
        if (files.isEmpty()) throw new IOException("ZIP-Datei ist leer: " + source.getFileName());
        return files;
    }

    private static Map<String, byte[]> readDirectory(Path source) throws IOException {
        Path real = source.toRealPath();
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (var walk = Files.walk(source)) {
            for (Path file : walk.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symlinks im Importordner sind nicht erlaubt: " + file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                if (files.size() >= MAX_ENTRIES) throw new IOException("Import enthält mehr als " + MAX_ENTRIES + " Dateien");
                if (!file.toRealPath().startsWith(real)) throw new IOException("Importdatei verlässt den Importordner: " + file);
                long size = Files.size(file);
                if (size > MAX_FILE_BYTES) throw new IOException("Importdatei ist größer als 16 MiB: " + file.getFileName());
                total += size;
                if (total > MAX_TOTAL_BYTES) throw new IOException("Import ist größer als 64 MiB");
                String name = validateEntryName(source.relativize(file).toString().replace(File.separatorChar, '/'));
                files.put(name, Files.readAllBytes(file));
            }
        }
        if (files.isEmpty()) throw new IOException("Importordner ist leer: " + source.getFileName());
        return files;
    }

    private static byte[] readLimited(InputStream input, String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) >= 0;) {
            if (read == 0) continue;
            if ((long) output.size() + read > MAX_FILE_BYTES) {
                throw new IOException("Importdatei ist entpackt größer als 16 MiB: " + name);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String validateEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || name.startsWith("/") || name.contains(":")) {
            throw new IllegalArgumentException("Ungültiger Importpfad: " + name);
        }
        Path path = Path.of(name);
        if (path.isAbsolute()) throw new IllegalArgumentException("Absoluter Importpfad: " + name);
        for (Path part : path) {
            if (part.toString().equals(".") || part.toString().equals("..")) {
                throw new IllegalArgumentException("Importpfad verlässt das Paket: " + name);
            }
        }
        String normalized = path.normalize().toString().replace(File.separatorChar, '/');
        if (!normalized.equals(name)) throw new IllegalArgumentException("Nicht normalisierter Importpfad: " + name);
        return normalized;
    }

    private static void validateAssetPath(String path, String source) {
        validateEntryName(path);
        String[] parts = path.split("/", 2);
        if (parts.length != 2 || !NAMESPACE.matcher(parts[0]).matches()
                || !RESOURCE_PATH.matcher(parts[1]).matches()) {
            throw new IllegalArgumentException(source + ": Ungültiger Ressourcenpfad assets/" + path);
        }
    }

    private static ResourceId resourceId(String value, String source) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1) {
            throw new IllegalArgumentException(source + ": Ressourcen-ID benötigt namespace:pfad: " + value);
        }
        String namespace = value.substring(0, colon);
        String path = value.substring(colon + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !RESOURCE_PATH.matcher(path).matches()
                || path.startsWith("/") || path.endsWith("/") || path.contains("//")
                || Arrays.asList(path.split("/")).contains("..")) {
            throw new IllegalArgumentException(source + ": Ungültige Ressourcen-ID: " + value);
        }
        return new ResourceId(namespace, path);
    }

    private static String internalId(ResourceId id) {
        String result = (id.namespace() + "_" + id.path()).replaceAll("[./-]+", "_");
        ItemRegistry.checkId(result);
        return result;
    }

    private static String readableName(String path) {
        String leaf = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').replace('-', ' ');
        if (leaf.isEmpty()) return path;
        return Character.toUpperCase(leaf.charAt(0)) + leaf.substring(1);
    }

    private static String required(ConfigurationSection section, String key, String source) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(source + ": " + key + " fehlt");
        return value.trim();
    }

    private static Map<String, Object> sectionMap(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : new TreeSet<>(section.getKeys(false))) {
            Object value = section.get(key);
            values.put(key, value instanceof ConfigurationSection child ? sectionMap(child) : value);
        }
        return values;
    }

    private static String json(Object value) {
        StringBuilder out = new StringBuilder();
        appendJson(out, value, 0);
        return out.append('\n').toString();
    }

    private static void appendJson(StringBuilder out, Object value, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            quote(out, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            if (!map.isEmpty()) out.append('\n');
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append("  ".repeat(indent + 1));
                quote(out, String.valueOf(entry.getKey()));
                out.append(": ");
                appendJson(out, entry.getValue(), indent + 1);
                if (++index < map.size()) out.append(',');
                out.append('\n');
            }
            if (!map.isEmpty()) out.append("  ".repeat(indent));
            out.append('}');
        } else if (value instanceof Collection<?> collection) {
            out.append('[');
            int index = 0;
            for (Object element : collection) {
                if (index++ > 0) out.append(", ");
                appendJson(out, element, indent);
            }
            out.append(']');
        } else {
            quote(out, String.valueOf(value));
        }
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) out.append(String.format("\\u%04x", (int) character));
                    else out.append(character);
                }
            }
        }
        out.append('"');
    }

    private void ensureManaged(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(generatedRoot) || normalized.equals(generatedRoot)) {
            throw new IOException("Unsicherer generierter Pfad: " + path);
        }
    }

    private void deleteTree(Path root) throws IOException {
        ensureManaged(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(from, to);
        }
    }

    private record ResourceId(String namespace, String path) {
        @Override public String toString() { return namespace + ":" + path; }
    }
}
