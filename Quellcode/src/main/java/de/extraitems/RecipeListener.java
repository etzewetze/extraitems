package de.extraitems;

import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

final class RecipeListener implements Listener {
    private final ExtraItemsPlugin plugin;
    private final ItemRegistry items;
    private final ToolService tools;
    RecipeListener(ExtraItemsPlugin plugin, ItemRegistry items, ToolService tools) {
        this.plugin = plugin; this.items = items; this.tools = tools;
    }

    boolean allowed(Player player, CraftingInventory inventory, Recipe recipe) {
        var spec = items.recipe(recipe);
        var actual = Arrays.stream(inventory.getMatrix()).map(items::ingredientId).toList();
        if (spec == null) return actual.stream().noneMatch(s -> s != null && s.startsWith("extraitems:"));
        return CraftPolicy.mayCraft(plugin.gate().ready(player), player.hasPermission(spec.permission()), spec.ingredients(), actual);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || !allowed(player, event.getInventory(), event.getRecipe())) {
            event.getInventory().setResult(null);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !allowed(player, event.getInventory(), event.getRecipe())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) plugin.message(player, "no-permission");
            return;
        }
        var spec = items.recipe(event.getRecipe());
        if (spec != null && spec.damagesTool()) craftWithTool(event, player, spec);
    }

    private void craftWithTool(CraftItemEvent event, Player player, ItemRegistry.RecipeSpec spec) {
        event.setCancelled(true);
        ItemStack[] matrix = event.getInventory().getMatrix();
        int toolSlot = -1;
        ItemStack knife = null;
        int ingredientCrafts = Integer.MAX_VALUE;
        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack stack = matrix[slot];
            if (stack == null || stack.getType().isAir()) continue;
            if (spec.tool().equals(items.id(stack))) {
                if (toolSlot >= 0) return;
                toolSlot = slot; knife = stack;
            } else ingredientCrafts = Math.min(ingredientCrafts, stack.getAmount());
        }
        if (toolSlot < 0 || knife == null || ingredientCrafts == Integer.MAX_VALUE) return;

        int crafts = Math.min(ingredientCrafts, tools.craftsAvailable(knife, spec.toolDamage()));
        if (!event.isShiftClick()) crafts = Math.min(crafts, 1);
        ItemStack sample = items.create(spec.result(), spec.amount());
        if (event.isShiftClick()) {
            crafts = Math.min(crafts, inventoryCapacity(player.getInventory(), sample) / spec.amount());
        } else if (!cursorFits(event.getCursor(), sample)) crafts = 0;
        if (crafts <= 0) return;

        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack stack = matrix[slot];
            if (stack == null || stack.getType().isAir() || slot == toolSlot) continue;
            int remaining = stack.getAmount() - crafts;
            matrix[slot] = remaining <= 0 ? null : stack.asQuantity(remaining);
        }
        if (!tools.damage(knife, Math.multiplyExact(crafts, spec.toolDamage()), player)) matrix[toolSlot] = null;
        else matrix[toolSlot] = knife;
        event.getInventory().setMatrix(matrix);

        int products = Math.multiplyExact(crafts, spec.amount());
        if (event.isShiftClick()) addResults(player.getInventory(), spec.result(), products);
        else {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir()) player.setItemOnCursor(items.create(spec.result(), products));
            else { cursor.setAmount(cursor.getAmount() + products); player.setItemOnCursor(cursor); }
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_STONECUTTER_TAKE_RESULT, .7f, 1.1f);
        player.updateInventory();
    }

    private boolean cursorFits(ItemStack cursor, ItemStack output) {
        return cursor == null || cursor.getType().isAir()
                || (cursor.isSimilar(output) && cursor.getAmount() + output.getAmount() <= cursor.getMaxStackSize());
    }

    private int inventoryCapacity(PlayerInventory inventory, ItemStack output) {
        int capacity = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) capacity += output.getMaxStackSize();
            else if (stack.isSimilar(output)) capacity += output.getMaxStackSize() - stack.getAmount();
        }
        return capacity;
    }

    private void addResults(PlayerInventory inventory, String id, int amount) {
        int max = items.create(id, 1).getMaxStackSize();
        while (amount > 0) {
            int part = Math.min(max, amount);
            inventory.addItem(items.create(id, part));
            amount -= part;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automate(CrafterCraftEvent event) {
        // A redstone crafter has no authenticated player/group; gated recipes are manual only.
        if (items.recipe(event.getRecipe()) != null || (event.getBlock().getState() instanceof Crafter crafter
                && Arrays.stream(crafter.getInventory().getContents()).anyMatch(i -> items.id(i) != null))) event.setCancelled(true);
    }
}
