package dev.dogarmor.tier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/** Loads the tier list from config.yml and builds/identifies tier armour items. */
public final class TierManager {

    private static final int VANILLA_WOLF_ARMOR_MAX_DAMAGE = 64;

    private final NamespacedKey tierKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private List<DogArmorTier> tiers = List.of();

    public TierManager(NamespacedKey tierKey) {
        this.tierKey = tierKey;
    }

    public void load(FileConfiguration config) {
        List<Map<?, ?>> entries = config.getMapList("tiers");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("config.yml has no tiers defined");
        }

        List<DogArmorTier> parsed = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : entries) {
            Object rawKey = entry.get("key");
            if (rawKey == null || String.valueOf(rawKey).isBlank()) {
                throw new IllegalArgumentException("A tier entry is missing its 'key'");
            }
            String key = String.valueOf(rawKey).trim();
            // A Map<?, ?> cannot take a String default in getOrDefault, so each
            // value is read and defaulted through string(...) instead.
            String displayName = string(entry, "display-name", key);
            List<String> loreStrings = entry.get("lore") instanceof List<?> list
                    ? castStringList(list)
                    : List.of();
            int durability = toInt(entry.get("durability"), VANILLA_WOLF_ARMOR_MAX_DAMAGE);
            Material material = parseMaterial(string(entry, "upgrade-material", "NONE"));
            int cost = toInt(entry.get("upgrade-cost"), 0);
            ItemRarity rarity = parseRarity(string(entry, "rarity", "COMMON"));
            boolean glint = toBoolean(entry.get("glint"));
            Map<Enchantment, Integer> enchantments = parseEnchantments(entry.get("enchantments"), key);

            List<Component> lore = loreStrings.stream().map(miniMessage::deserialize).toList();
            parsed.add(new DogArmorTier(index++, key, miniMessage.deserialize(displayName), lore,
                    durability, material, cost, rarity, glint, enchantments));
        }
        this.tiers = List.copyOf(parsed);
    }

    public List<DogArmorTier> tiers() {
        return tiers;
    }

    public @Nullable DogArmorTier byKey(String key) {
        for (DogArmorTier tier : tiers) {
            if (tier.key().equalsIgnoreCase(key)) {
                return tier;
            }
        }
        return null;
    }

    public @Nullable DogArmorTier nextTier(int currentIndex) {
        int next = currentIndex + 1;
        return next < tiers.size() ? tiers.get(next) : null;
    }

    /** The tier whose upgrade material matches the given material, or null. */
    public @Nullable DogArmorTier candidateFor(Material material) {
        if (material == null) {
            return null;
        }
        for (DogArmorTier tier : tiers) {
            if (tier.hasUpgradeMaterial() && tier.upgradeMaterial() == material) {
                return tier;
            }
        }
        return null;
    }

    /**
     * The tier of the given equipped armour. Vanilla wolf armour without a
     * tier marker counts as the first tier; anything that is not wolf armour
     * returns null.
     */
    public @Nullable DogArmorTier tierOf(@Nullable ItemStack armor) {
        if (armor == null || armor.getType() != Material.WOLF_ARMOR) {
            return null;
        }
        if (tiers.isEmpty()) {
            return null;
        }
        Integer marked = armor.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        int index = marked == null ? 0 : marked;
        // A marker outside the list means config.yml dropped tiers since the
        // armour was made; keep the armour usable instead of treating it as none.
        if (index < 0) {
            index = 0;
        } else if (index >= tiers.size()) {
            index = tiers.size() - 1;
        }
        return tiers.get(index);
    }

    /** Reads the remaining durability of an equipped armour item. */
    public String durabilityDisplay(@Nullable ItemStack armor) {
        int current = armor == null ? 0
                : armor.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        int max = armor == null ? VANILLA_WOLF_ARMOR_MAX_DAMAGE
                : armor.getDataOrDefault(DataComponentTypes.MAX_DAMAGE, VANILLA_WOLF_ARMOR_MAX_DAMAGE);
        return (max - current) + "/" + max;
    }

    public ItemStack createItem(DogArmorTier tier, @Nullable ItemStack oldArmor) {
        return tier.createItem(oldArmor, tierKey);
    }

    public void equip(Wolf wolf, DogArmorTier tier, @Nullable ItemStack oldArmor) {
        wolf.getEquipment().setItem(EquipmentSlot.BODY, createItem(tier, oldArmor));
    }

    public void playUpgradeEffects(Wolf wolf) {
        World world = wolf.getWorld();
        Location loc = wolf.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.3, 0.4, 0.3, 0);
        world.spawnParticle(Particle.HEART, loc, 3, 0.2, 0.2, 0.2, 0);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
    }

    private static List<String> castStringList(List<?> list) {
        List<String> result = new ArrayList<>();
        for (Object value : list) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** A config value as trimmed text, with a fallback when it is absent or blank. */
    private static String string(Map<?, ?> entry, String name, String fallback) {
        Object value = entry.get(name);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static boolean toBoolean(@Nullable Object value) {
        return value != null && Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static @Nullable Material parseMaterial(String name) {
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
            return null;
        }
        Material material = Material.matchMaterial(name);
        if (material == null) {
            throw new IllegalArgumentException("Unknown upgrade-material: " + name);
        }
        return material;
    }

    private static ItemRarity parseRarity(String name) {
        try {
            return ItemRarity.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ItemRarity.COMMON;
        }
    }

    /**
     * Reads a tier's {@code enchantments:} section, given either as a map of
     * enchantment name to level ({@code mending: 1}) or as a list of
     * {@code "name: level"} strings. An unknown name fails the load, so a typo
     * shows up in the console instead of quietly giving the tier nothing.
     */
    private static Map<Enchantment, Integer> parseEnchantments(@Nullable Object raw, String tierKey) {
        if (raw == null) {
            return Map.of();
        }
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                addEnchantment(out, String.valueOf(entry.getKey()), toInt(entry.getValue(), 1), tierKey);
            }
        } else if (raw instanceof List<?> list) {
            for (Object value : list) {
                String text = String.valueOf(value).trim();
                int split = Math.max(text.lastIndexOf(':'), text.lastIndexOf(' '));
                if (split < 0) {
                    addEnchantment(out, text, 1, tierKey);
                } else {
                    addEnchantment(out, text.substring(0, split).trim(),
                            toInt(text.substring(split + 1).trim(), 1), tierKey);
                }
            }
        } else {
            throw new IllegalArgumentException(
                    "Tier '" + tierKey + "' has an 'enchantments' value that is not a map or a list");
        }
        // Config order is kept, so the item's enchantment summary reads the way
        // the file is written.
        return Collections.unmodifiableMap(out);
    }

    private static void addEnchantment(Map<Enchantment, Integer> out, String name, int level, String tierKey) {
        if (level <= 0) {
            return;
        }
        Enchantment enchantment = parseEnchantment(name);
        if (enchantment == null) {
            throw new IllegalArgumentException(
                    "Unknown enchantment '" + name + "' on tier '" + tierKey + "'");
        }
        out.put(enchantment, level);
    }

    private static @Nullable Enchantment parseEnchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        NamespacedKey key;
        try {
            key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (key == null) {
            return null;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }
}
