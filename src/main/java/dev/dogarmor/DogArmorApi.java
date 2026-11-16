package dev.dogarmor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import dev.dogarmor.tier.DogArmorTier;
import dev.dogarmor.tier.TierManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A small, stable surface for another plugin to read and change dog armour.
 *
 * <p>This exists because the only other way in is a command typed while looking
 * at a wolf, and a web panel cannot do that. StaffPanel uses it to list every
 * loaded dog with the armour it wears, and to set, repair or remove that armour.
 *
 * <p>Three rules shape it, and they are worth keeping if this is ever changed:
 *
 * <ul>
 *   <li><b>Plain types only.</b> Results are maps and lists of strings, numbers
 *       and booleans. The caller lives in another plugin's class loader, so
 *       handing it this plugin's own classes would fail the moment the two jars
 *       disagree about a field. {@code java.util} types are shared, so they always
 *       work.
 *   <li><b>The main thread decides.</b> Every call here can arrive from a web
 *       thread, and no wolf, item or config may be touched from there, so the work
 *       is marshalled onto the server thread and waited for.
 *   <li><b>Nothing throws.</b> A failure comes back as a sentence the panel can
 *       show, because a stack trace in a web request is useless to a staff member.
 * </ul>
 */
public final class DogArmorApi {

    /** Set on enable, cleared on disable. */
    private static volatile DogArmorApi instance;

    /** The live API, or null when DogArmor is not running. */
    public static DogArmorApi get() {
        return instance;
    }

    static void install(DogArmorApi api) {
        instance = api;
    }

    static void uninstall() {
        instance = null;
    }

    private final DogArmorPlugin plugin;
    private final TierManager tierManager;

    DogArmorApi(DogArmorPlugin plugin, TierManager tierManager) {
        this.plugin = plugin;
        this.tierManager = tierManager;
    }

    // -------------------------------------------------------------- reading

    /**
     * Every tier this server runs, in upgrade order.
     *
     * <p>Each entry has: {@code key}, {@code displayName} (plain text, so the
     * panel does not have to understand MiniMessage), {@code durability} (the
     * tier's max damage, which is its whole benefit), {@code upgradeMaterial}
     * (readable, empty for the base tier), {@code upgradeCost}, {@code rarity}.
     */
    public List<Map<String, Object>> tiers() {
        return onMain(this::tierSnapshot, new ArrayList<>());
    }

    private List<Map<String, Object>> tierSnapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DogArmorTier tier : tierManager.tiers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", tier.key());
            entry.put("displayName", plain(tier.displayName(), tier.key()));
            entry.put("durability", tier.durability());
            entry.put("upgradeMaterial", tier.upgradeMaterial() == null
                    ? "" : pretty(tier.upgradeMaterial()));
            entry.put("upgradeCost", tier.upgradeCost());
            entry.put("rarity", tier.rarity().name());
            out.add(entry);
        }
        return out;
    }

    /**
     * Every loaded tamed wolf, wherever it is.
     *
     * <p>Each entry has: {@code id} (uuid), {@code name}, {@code owner} (uuid),
     * {@code ownerName}, {@code world}, {@code health}, {@code armor} (boolean,
     * whether it is wearing wolf armour at all), {@code tier} (tier key, empty
     * when it wears none), {@code tierName}, {@code durability} (left, 0 when it
     * wears none) and {@code maxDurability}.
     *
     * <p>Only loaded dogs are here: a dog in an unloaded chunk cannot be read, and
     * saying nothing is better than guessing what it is wearing. The panel says so
     * above the list.
     */
    public List<Map<String, Object>> dogs() {
        return onMain(this::dogSnapshot, new ArrayList<>());
    }

    private List<Map<String, Object>> dogSnapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (!wolf.isTamed()) {
                    continue;
                }
                out.add(describe(wolf));
            }
        }
        return out;
    }

    private Map<String, Object> describe(Wolf wolf) {
        Map<String, Object> entry = new LinkedHashMap<>();
        ItemStack armor = bodyOf(wolf);
        DogArmorTier tier = tierManager.tierOf(armor);
        boolean wearing = armor != null && armor.getType() == Material.WOLF_ARMOR;

        entry.put("id", wolf.getUniqueId().toString());
        entry.put("name", plain(wolf.customName(), "Wolf"));
        entry.put("owner", ownerId(wolf));
        entry.put("ownerName", ownerName(wolf));
        entry.put("world", wolf.getWorld().getName());
        entry.put("health", wolf.getHealth());
        entry.put("armor", wearing);
        entry.put("tier", tier == null ? "" : tier.key());
        entry.put("tierName", tier == null ? "" : plain(tier.displayName(), tier.key()));
        entry.put("durability", wearing ? durabilityLeft(armor) : 0);
        entry.put("maxDurability", wearing ? durabilityMax(armor) : 0);
        return entry;
    }

    private String ownerId(Wolf wolf) {
        AnimalTamer owner = wolf.getOwner();
        return owner == null ? "" : owner.getUniqueId().toString();
    }

    private String ownerName(Wolf wolf) {
        AnimalTamer owner = wolf.getOwner();
        if (owner == null) {
            return "nobody";
        }
        String name = owner.getName();
        return name == null || name.isBlank() ? owner.getUniqueId().toString().substring(0, 8) : name;
    }

    // ------------------------------------------------------------- changing

    /**
     * Puts a tier's armour on a dog, keeping only the dye colour it had.
     * Enchantments are not carried over.
     */
    public String setTier(String dogId, String tierKey) {
        DogArmorTier tier = tierManager.byKey(tierKey == null ? "" : tierKey.trim());
        if (tier == null) {
            return "There is no armour tier called '" + tierKey + "' on this server.";
        }
        UUID id = parse(dogId);
        if (id == null) {
            return "That is not a dog the panel can find.";
        }
        return onMain(() -> {
            Wolf wolf = findWolf(id);
            if (wolf == null) {
                return "That dog is not loaded right now, so its armour cannot be changed.";
            }
            tierManager.equip(wolf, tier, bodyOf(wolf));
            return null;
        }, "The server did not answer.");
    }

    /** Puts a dog's armour back to full durability. */
    public String repair(String dogId) {
        UUID id = parse(dogId);
        if (id == null) {
            return "That is not a dog the panel can find.";
        }
        return onMain(() -> {
            Wolf wolf = findWolf(id);
            if (wolf == null) {
                return "That dog is not loaded right now, so its armour cannot be repaired.";
            }
            ItemStack armor = bodyOf(wolf);
            if (armor == null || armor.getType() != Material.WOLF_ARMOR) {
                return "That dog is not wearing armour.";
            }
            ItemStack repaired = armor.clone();
            repaired.setData(DataComponentTypes.DAMAGE, 0);
            equip(wolf, repaired);
            return null;
        }, "The server did not answer.");
    }

    /** Takes a dog's armour off. The item is not given to anyone. */
    public String remove(String dogId) {
        UUID id = parse(dogId);
        if (id == null) {
            return "That is not a dog the panel can find.";
        }
        return onMain(() -> {
            Wolf wolf = findWolf(id);
            if (wolf == null) {
                return "That dog is not loaded right now, so its armour cannot be taken off.";
            }
            ItemStack armor = bodyOf(wolf);
            if (armor == null || armor.getType() != Material.WOLF_ARMOR) {
                return "That dog is not wearing armour.";
            }
            equip(wolf, new ItemStack(Material.AIR));
            return null;
        }, "The server did not answer.");
    }

    /** Re-reads config.yml, so a tier added on disk appears without a restart. */
    public String reload() {
        return onMain(() -> {
            try {
                return plugin.reloadAll() ? null
                        : "The tiers could not be reloaded - check config.yml. The list already "
                                + "in use is unchanged.";
            } catch (RuntimeException exception) {
                return "The tiers could not be reloaded: " + exception.getMessage();
            }
        }, "The server did not answer.");
    }

    // ------------------------------------------------------------- plumbing

    private @Nullable ItemStack bodyOf(Wolf wolf) {
        if (wolf.getEquipment() == null) {
            return null;
        }
        ItemStack item = wolf.getEquipment().getItem(EquipmentSlot.BODY);
        return item == null || item.getType().isAir() ? null : item;
    }

    private void equip(Wolf wolf, ItemStack item) {
        if (wolf.getEquipment() != null) {
            wolf.getEquipment().setItem(EquipmentSlot.BODY, item);
        }
    }

    private @Nullable Wolf findWolf(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof Wolf wolf ? wolf : null;
    }

    private int durabilityLeft(ItemStack armor) {
        int max = durabilityMax(armor);
        int damage = armor.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        return Math.max(0, max - damage);
    }

    private int durabilityMax(ItemStack armor) {
        return armor.getDataOrDefault(DataComponentTypes.MAX_DAMAGE, 64);
    }

    private static String plain(@Nullable Component component, String fallback) {
        if (component == null) {
            return fallback;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(component);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static @Nullable UUID parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Runs work on the server thread and waits for it.
     *
     * <p>Every caller may be a web thread, and neither a wolf nor the config may be
     * touched from there. The five second cap is the same one the panel uses for
     * its own server calls: long enough for any of this, short enough that a
     * stalled server does not hold a web request open for ever.
     */
    private <T> T onMain(Callable<T> work, T fallback) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return work.call();
            } catch (Exception exception) {
                plugin.getLogger().warning("DogArmor API call failed: " + exception);
                return fallback;
            }
        }
        FutureTask<T> task = new FutureTask<>(work);
        try {
            Bukkit.getScheduler().runTask(plugin, task);
            return task.get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("DogArmor API call did not finish: " + exception);
            return fallback;
        }
    }
}
