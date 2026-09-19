package de.extraitems;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/**
 * Adds main-hand combat attributes while remaining compatible with both Bukkit's
 * legacy UUID constructor and its newer NamespacedKey/EquipmentSlotGroup API.
 */
final class CombatAttributes {
    private CombatAttributes() {}

    static void apply(ItemStack item, Plugin plugin, String id, double totalDamage, double attacksPerSecond) {
        ItemMeta meta = item.getItemMeta();
        try {
            add(meta, plugin, id + "_attack_damage", new String[]{"ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE"},
                    totalDamage - 1.0);
            add(meta, plugin, id + "_attack_speed", new String[]{"ATTACK_SPEED", "GENERIC_ATTACK_SPEED"},
                    attacksPerSecond - 4.0);
            item.setItemMeta(meta);
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("Kampfattribute werden von dieser Bukkit-API nicht unterstützt", error);
        }
    }

    private static void add(ItemMeta meta, Plugin plugin, String keyName, String[] attributeNames,
                            double amount) throws ReflectiveOperationException {
        Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
        Class<?> modifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
        Object attribute = staticField(attributeClass, attributeNames);
        Object modifier = createModifier(modifierClass, plugin, keyName, amount);
        Method add = ItemMeta.class.getMethod("addAttributeModifier", attributeClass, modifierClass);
        Object accepted = add.invoke(meta, attribute, modifier);
        if (accepted instanceof Boolean result && !result) {
            throw new IllegalArgumentException("Doppeltes Kampf-attribut: " + keyName);
        }
    }

    private static Object staticField(Class<?> type, String[] names) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                Field field = type.getField(name);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
                // Try the name used by the other supported API generation.
            }
        }
        throw new NoSuchFieldException(type.getName() + " " + String.join("/", names));
    }

    private static Object createModifier(Class<?> modifierClass, Plugin plugin, String keyName,
                                         double amount) throws ReflectiveOperationException {
        Class<?> operationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
        Object operation = enumValue(operationClass, "ADD_NUMBER");
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        UUID uuid = UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));

        Constructor<?>[] constructors = modifierClass.getConstructors();
        Arrays.sort(constructors, Comparator.comparingInt((Constructor<?> value) -> constructorScore(value)).reversed());
        ReflectiveOperationException last = null;
        for (Constructor<?> constructor : constructors) {
            try {
                Object[] arguments = arguments(constructor.getParameterTypes(), key, uuid, amount, operation);
                if (arguments != null) return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException error) {
                last = error;
            }
        }
        if (last != null) throw last;
        throw new NoSuchMethodException("Kein kompatibler AttributeModifier-Konstruktor");
    }

    private static int constructorScore(Constructor<?> constructor) {
        int score = 0;
        for (Class<?> type : constructor.getParameterTypes()) {
            if (type == NamespacedKey.class) score += 8;
            else if (type.getName().endsWith("EquipmentSlotGroup")) score += 4;
            else if (type.getName().endsWith("EquipmentSlot")) score += 2;
            else if (type == UUID.class) score += 1;
        }
        return score;
    }

    private static Object[] arguments(Class<?>[] types, NamespacedKey key, UUID uuid, double amount,
                                      Object operation) throws ReflectiveOperationException {
        Object[] values = new Object[types.length];
        boolean hasAmount = false, hasOperation = false;
        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            if (type == NamespacedKey.class) values[index] = key;
            else if (type == UUID.class) values[index] = uuid;
            else if (type == String.class) values[index] = key.toString();
            else if (type == double.class || type == Double.class) {
                values[index] = amount;
                hasAmount = true;
            } else if (type.isInstance(operation)) {
                values[index] = operation;
                hasOperation = true;
            } else if (type.getName().endsWith("EquipmentSlotGroup")) {
                values[index] = type.getField("MAINHAND").get(null);
            } else if (type.getName().endsWith("EquipmentSlot")) {
                values[index] = enumValue(type, "HAND");
            } else return null;
        }
        return hasAmount && hasOperation ? values : null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }
}
