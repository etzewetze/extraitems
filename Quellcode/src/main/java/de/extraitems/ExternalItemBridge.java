package de.extraitems;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

final class ExternalItemBridge {
    static final Set<String> PROVIDERS = Set.of("nexo", "itemsadder", "oraxen", "craftengine");

    private record Adapter(String pluginName, String apiClass) {}
    private static final Map<String, Adapter> ADAPTERS = Map.of(
            "nexo", new Adapter("Nexo", "com.nexomc.nexo.api.NexoItems"),
            "itemsadder", new Adapter("ItemsAdder", "dev.lone.itemsadder.api.CustomStack"),
            "oraxen", new Adapter("Oraxen", "io.th0rgal.oraxen.api.OraxenItems"),
            "craftengine", new Adapter("CraftEngine", "net.momirealms.craftengine.bukkit.api.CraftEngineItems")
    );

    record Reference(String provider, String id) {
        Reference {
            provider = provider.toLowerCase(Locale.ROOT).trim();
            id = id.trim();
        }
        String token() { return provider + ":" + id; }
    }

    static final class Unavailable extends IllegalArgumentException {
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }

    private final ExtraItemsPlugin plugin;
    private final Set<String> warned = new HashSet<>();

    ExternalItemBridge(ExtraItemsPlugin plugin) { this.plugin = plugin; }

    static Reference parse(String token) {
        if (token == null) throw new IllegalArgumentException("Externe Item-ID fehlt");
        String value = token.trim();
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Externe Item-ID benötigt provider:id: " + token);
        }
        String provider = value.substring(0, separator).toLowerCase(Locale.ROOT);
        String id = value.substring(separator + 1).trim();
        if (!PROVIDERS.contains(provider) || id.isEmpty() || id.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unbekannter externer Item-Provider oder leere ID: " + token);
        }
        return new Reference(provider, id);
    }

    static boolean isReferenceToken(String token) {
        try { parse(token); return true; } catch (IllegalArgumentException ignored) { return false; }
    }
    boolean isReference(String token) { return isReferenceToken(token); }

    ItemStack resolve(String token, int amount) {
        Reference reference = parse(token);
        if (!providerEnabled(reference.provider())) {
            throw new Unavailable(reference.provider() + " ist in config.yml deaktiviert");
        }
        Plugin provider = provider(reference);
        if (provider == null) throw new Unavailable(reference.provider() + " ist nicht installiert");
        if (!provider.isEnabled()) throw new Unavailable(reference.provider() + " ist noch nicht bereit");
        try {
            Object value;
            Class<?> api = apiClass(reference, provider);
            switch (reference.provider()) {
                case "nexo" -> {
                    Object builder = staticCall(api, "itemFromId", new Class<?>[]{String.class}, reference.id());
                    value = builder == null ? null : instanceCall(builder, "build");
                }
                case "itemsadder" -> {
                    Object custom = staticCall(api, "getInstance", new Class<?>[]{String.class}, reference.id());
                    value = custom == null ? null : instanceCall(custom, "getItemStack");
                }
                case "oraxen" -> {
                    Object builder = staticCall(api, "getItemById", new Class<?>[]{String.class}, reference.id());
                    value = builder == null ? null : instanceCall(builder, "build");
                }
                case "craftengine" -> {
                    Object definition = unwrap(staticCall(api, "byId", new Class<?>[]{String.class}, reference.id()));
                    value = definition == null ? null : instanceCall(definition, "buildBukkitItem");
                }
                default -> throw new Unavailable("Unbekannter Provider: " + reference.provider());
            }
            value = unwrap(value);
            if (!(value instanceof ItemStack stack) || stack.getType().isAir()) {
                throw new Unavailable("Item '" + reference.id() + "' wurde von " + reference.provider() + " nicht gefunden");
            }
            if (amount < 1 || amount > stack.getMaxStackSize()) {
                throw new Unavailable("Ungültige Menge für externes Item: " + amount);
            }
            ItemStack result = stack.clone();
            result.setAmount(amount);
            return result;
        } catch (Unavailable error) {
            throw error;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(reference.provider(), "API-Aufruf fehlgeschlagen: " + error.getMessage());
            throw new Unavailable(reference.provider() + "-API konnte '" + reference.id() + "' nicht auflösen", error);
        }
    }

    boolean matches(ItemStack item, String token) {
        if (item == null || item.getType().isAir() || !isReference(token)) return false;
        Reference reference = parse(token);
        if (!providerEnabled(reference.provider())) return false;
        Plugin provider = provider(reference);
        if (provider == null || !provider.isEnabled()) return false;
        try {
            Class<?> api = apiClass(reference, provider);
            Object value = switch (reference.provider()) {
                case "nexo" -> staticCall(api, "idFromItem", new Class<?>[]{ItemStack.class}, item);
                case "itemsadder" -> {
                    Object custom = staticCall(api, "byItemStack", new Class<?>[]{ItemStack.class}, item);
                    yield custom == null ? null : instanceCall(custom, "getNamespacedID");
                }
                case "oraxen" -> staticCall(api, "getIdByItem", new Class<?>[]{ItemStack.class}, item);
                case "craftengine" -> staticCall(api, "getCustomItemId", new Class<?>[]{ItemStack.class}, item);
                default -> null;
            };
            value = unwrap(value);
            return value != null && idsEqual(reference, String.valueOf(value));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(reference.provider(), "Item-Prüfung fehlgeschlagen: " + error.getMessage());
            return false;
        }
    }

    boolean isKnownCustom(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        for (String provider : PROVIDERS) {
            if (providerEnabled(provider) && identity(item, provider) != null) return true;
        }
        return false;
    }

    String stateToken(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        for (String provider : PROVIDERS) {
            if (!providerEnabled(provider)) continue;
            String identity = identity(item, provider);
            if (identity != null) return "external:" + provider + ":" + identity;
        }
        return null;
    }

    private String identity(ItemStack item, String provider) {
        Adapter adapter = ADAPTERS.get(provider);
        Plugin loaded = Bukkit.getPluginManager().getPlugin(adapter.pluginName());
        if (loaded == null || !loaded.isEnabled()) return null;
        try {
            Class<?> api = apiClass(new Reference(provider, "identity"), loaded);
            Object value = switch (provider) {
                case "nexo" -> staticCall(api, "idFromItem", new Class<?>[]{ItemStack.class}, item);
                case "itemsadder" -> {
                    Object custom = staticCall(api, "byItemStack", new Class<?>[]{ItemStack.class}, item);
                    yield custom == null ? null : instanceCall(custom, "getNamespacedID");
                }
                case "oraxen" -> staticCall(api, "getIdByItem", new Class<?>[]{ItemStack.class}, item);
                case "craftengine" -> staticCall(api, "getCustomItemId", new Class<?>[]{ItemStack.class}, item);
                default -> null;
            };
            value = unwrap(value);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(provider, "API-Aufruf fehlgeschlagen: " + error.getMessage());
            return null;
        }
    }

    private boolean idsEqual(Reference reference, String actual) {
        String normalized = actual.trim().toLowerCase(Locale.ROOT);
        String expected = reference.id().trim().toLowerCase(Locale.ROOT);
        String prefix = reference.provider() + ":";
        if (normalized.startsWith(prefix)) normalized = normalized.substring(prefix.length());
        return normalized.equals(expected);
    }

    private boolean providerEnabled(String provider) {
        return plugin.getConfig().getBoolean("integrations." + provider, true);
    }

    private Plugin provider(Reference reference) {
        return Bukkit.getPluginManager().getPlugin(ADAPTERS.get(reference.provider()).pluginName());
    }

    private Class<?> apiClass(Reference reference, Plugin provider) throws ClassNotFoundException {
        try {
            return Class.forName(ADAPTERS.get(reference.provider()).apiClass(), true, provider.getClass().getClassLoader());
        } catch (ClassNotFoundException first) {
            return Class.forName(ADAPTERS.get(reference.provider()).apiClass());
        }
    }

    private static Object staticCall(Class<?> type, String name, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        return type.getMethod(name, parameterTypes).invoke(null, args);
    }

    private static Object instanceCall(Object receiver, String name, Object... args)
            throws ReflectiveOperationException {
        Method selected = Arrays.stream(receiver.getClass().getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == args.length)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(receiver.getClass().getName() + "." + name));
        return selected.invoke(receiver, args);
    }

    private static Object unwrap(Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        return value;
    }

    private void warn(String provider, String detail) {
        if (warned.add(provider + ":" + detail)) plugin.getLogger().warning("[Integrationen] " + provider + ": " + detail);
    }

    String status() {
        List<String> result = new ArrayList<>();
        for (String provider : List.of("nexo", "itemsadder", "oraxen", "craftengine")) {
            if (!providerEnabled(provider)) result.add(provider + "=deaktiviert");
            else {
                Plugin loaded = Bukkit.getPluginManager().getPlugin(ADAPTERS.get(provider).pluginName());
                result.add(provider + "=" + (loaded == null ? "nicht installiert" : loaded.isEnabled() ? "aktiv" : "nicht bereit"));
            }
        }
        return String.join(", ", result);
    }

    static boolean isProviderPlugin(String name) {
        if (name == null) return false;
        return ADAPTERS.values().stream().anyMatch(adapter -> adapter.pluginName().equalsIgnoreCase(name));
    }
}
