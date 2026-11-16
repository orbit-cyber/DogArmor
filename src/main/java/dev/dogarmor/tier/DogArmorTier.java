package dev.dogarmor.tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * One tier of dog armour. The first tier is the plain vanilla
 * wolf armour; every other tier is a WOLF_ARMOR item carrying a
 * MAX_DAMAGE component, a rarity, its own enchantments and a
 * persistent tier marker.
 *
 * @param index            position in the tier list (0-based)
 * @param key              config key, e.g. "diamond"
 * @param displayName      MiniMessage-parsed item name
 * @param lore             MiniMessage-parsed lore lines
 * @param durability       max durability (MAX_DAMAGE) of this tier
 * @param upgradeMaterial  material consumed to reach this tier, null for the base tier
 * @param upgradeCost      how many of the material are consumed
 * @param rarity           item rarity (name colour)
 * @param glint            whether the item gets an enchantment glint
 * @param enchantments     enchantments every item of this tier carries
 */
public record DogArmorTier(
        int index,
        String key,
        Component displayName,
        List<Component> lore,
        int durability,
        @Nullable Material upgradeMaterial,
        int upgradeCost,
        ItemRarity rarity,
        boolean glint,
        Map<Enchantment, Integer> enchantments
) {

    public boolean hasUpgradeMaterial() {
        return upgradeMaterial != null && upgradeCost > 0;
    }

    public ItemStack createItem(@Nullable ItemStack oldArmor, NamespacedKey tierKey) {
        ItemStack item = new ItemStack(Material.WOLF_ARMOR);

        // The meta is written FIRST and the data components LAST, deliberately.
        // setItemMeta() replaces the item's whole component set with the meta's
        // own, so a component written before it can be dropped - which would
        // silently reset every tier's durability to the vanilla 64. Written
        // after it, they cannot be lost.
        //
        // Enchantments of the old armour are deliberately not carried over: a
        // tier item carries exactly the enchantments its own config entry lists.
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        // The level comes from config, so it is not capped at the enchantment's
        // own maximum (unbreaking III is under it anyway).
        enchantments.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
        // The marker that tells the plugin which tier this armour is.
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, index);
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.MAX_DAMAGE, durability);
        item.setData(DataComponentTypes.RARITY, rarity);
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        // Keep the dye colour of the armour being upgraded.
        if (oldArmor != null && oldArmor.hasData(DataComponentTypes.DYED_COLOR)) {
            item.setData(DataComponentTypes.DYED_COLOR, oldArmor.getData(DataComponentTypes.DYED_COLOR));
        }
        return item;
    }

    /** This tier's enchantments as readable text, e.g. "Mending I, Unbreaking III". */
    public String enchantmentSummary() {
        if (enchantments.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        enchantments.forEach((enchantment, level) ->
                parts.add(prettyName(enchantment.getKey().getKey()) + " " + roman(level)));
        return String.join(", ", parts);
    }

    private static String prettyName(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
