package de.extraitems;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Exact-use knife durability plus the plugin-owned Old but Gold anvil upgrade. */
final class ToolService implements Listener {
    private static final String OLD_GOLD_LORE = "§6Old but Gold I";
    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final NamespacedKey oldGoldKey;

    ToolService(ExtraItemsPlugin plugin, ItemRegistry items) {
        this.plugin = plugin;
        this.items = items;
        this.oldGoldKey = new NamespacedKey(plugin, "old_but_gold");
    }

    boolean isOldGold(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(oldGoldKey, PersistentDataType.BYTE);
    }

    int maxUses(ItemStack item) {
        ItemRegistry.Tool tool = items.tool(item);
        if (tool == null) return 0;
        int level = item.getEnchantmentLevel(Enchantment.UNBREAKING);
        return ToolPolicy.maxUses(tool.baseUses(), tool.usesPerUnbreakingLevel(), level);
    }

    int craftsAvailable(ItemStack item, int damagePerCraft) {
        if (!(item != null && item.getItemMeta() instanceof Damageable damageable)) return 0;
        return ToolPolicy.craftsAvailable(maxUses(item), damageable.getDamage(), damagePerCraft,
                damageable.isUnbreakable() || isOldGold(item));
    }

    /** Returns true while the item survives. Old but Gold never takes damage. */
    boolean damage(ItemStack item, int uses, Player owner) {
        ItemRegistry.Tool tool = items.tool(item);
        if (tool == null || !(item.getItemMeta() instanceof Damageable meta)) return false;
        refresh(item);
        meta = (Damageable) item.getItemMeta();
        if (meta.isUnbreakable() || isOldGold(item)) return true;
        int next = meta.getDamage() + uses;
        if (next >= meta.getMaxDamage()) {
            if (owner != null) owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return false;
        }
        meta.setDamage(next);
        item.setItemMeta(meta);
        return true;
    }

    void refresh(ItemStack item) {
        ItemRegistry.Tool tool = items.tool(item);
        if (tool == null || !(item.getItemMeta() instanceof Damageable meta)) return;
        meta.setMaxDamage(maxUses(item));
        if (isOldGold(item)) meta.setUnbreakable(true);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void melee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (items.tool(weapon) == null) return;
        if (damage(weapon, 1, player)) player.getInventory().setItemInMainHand(weapon);
        else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack left = inventory.getItem(0);
        ItemStack right = inventory.getItem(1);
        String rightId = items.id(right);

        if (rightId != null && items.tool(left) != null) {
            ItemRegistry.Tool tool = items.tool(left);
            if (rightId.equals(tool.oldGoldBook())) {
                ItemStack result = left.clone();
                result.setAmount(1);
                applyOldGold(result);
                event.setResult(result);
                event.getView().setRepairCost(5);
                return;
            }
        }
        if (rightId != null && "old_but_gold_book".equals(rightId)) {
            event.setResult(null); // This enchantment is deliberately knife-only.
            return;
        }
        ItemStack result = event.getResult();
        if (items.tool(result) != null) {
            refresh(result);
            event.setResult(result);
        }
    }

    private void applyOldGold(ItemStack item) {
        ItemMeta raw = item.getItemMeta();
        if (!(raw instanceof Damageable meta)) return;
        meta.getPersistentDataContainer().set(oldGoldKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(OLD_GOLD_LORE::equals);
        lore.add(OLD_GOLD_LORE);
        meta.setLore(lore);
        item.setItemMeta(meta);
        refresh(item);
    }
}
