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
    RecipeListener(ExtraItemsPlugin plugin, ItemRegistry items) { this.plugin = plugin; this.items = items; }

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
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automate(CrafterCraftEvent event) {
        // A redstone crafter has no authenticated player/group; gated recipes are manual only.
        if (items.recipe(event.getRecipe()) != null || (event.getBlock().getState() instanceof Crafter crafter
                && Arrays.stream(crafter.getInventory().getContents()).anyMatch(i -> items.id(i) != null))) event.setCancelled(true);
    }
}
