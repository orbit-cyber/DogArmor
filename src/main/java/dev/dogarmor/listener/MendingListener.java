package dev.dogarmor.listener;

import dev.dogarmor.DogArmorPlugin;
import dev.dogarmor.tier.TierManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Makes Mending on dog armour mean something.
 *
 * <p>In vanilla a dog never collects experience, so Mending on wolf armour could
 * never repair anything. Here, experience the <em>owner</em> picks up while
 * standing near the dog repairs that dog's armour, at the vanilla rate of two
 * durability per experience point, and that experience is taken out of the
 * pickup - the same trade a player makes wearing a Mending piece.
 */
public final class MendingListener implements Listener {

    /** The enchantment name as written in config.yml. */
    private static final String MENDING = "mending";

    private final DogArmorPlugin plugin;

    /**
     * Resolved once. This runs on every experience orb a player picks up, and a
     * registry lookup per orb is pure waste; the enchantment registry cannot
     * change while the server runs.
     */
    private @Nullable Enchantment mending;
    private boolean mendingResolved;

    public MendingListener(DogArmorPlugin plugin) {
        this.plugin = plugin;
    }

    // No ignoreCancelled here: PlayerExpChangeEvent is not Cancellable, and
    // asking for it would make registerEvents throw and the plugin fail to
    // enable. The event is adjusted through setAmount() instead.
    @EventHandler
    public void onExperienceGain(PlayerExpChangeEvent event) {
        if (!plugin.getConfig().getBoolean("mending.enabled", true)) {
            return;
        }
        int experience = event.getAmount();
        if (experience <= 0) {
            return;
        }
        Player owner = event.getPlayer();
        double range = Math.max(1.0, plugin.getConfig().getDouble("mending.range", 8.0));
        int durabilityPerExperience = Math.max(1,
                plugin.getConfig().getInt("mending.durability-per-experience", 2));

        int remaining = experience;
        for (Entity entity : owner.getNearbyEntities(range, range, range)) {
            if (remaining <= 0) {
                break;
            }
            if (!(entity instanceof Wolf wolf) || !wolf.isTamed()) {
                continue;
            }
            if (!(wolf.getOwner() instanceof Player wolfOwner)
                    || !wolfOwner.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            remaining = repair(wolf, remaining, durabilityPerExperience);
        }
        if (remaining != experience) {
            // The experience spent becomes the repair, so it does not also feed
            // the owner's experience bar.
            event.setAmount(remaining);
        }
    }

    /** Repairs one dog's armour from the experience available, returning what is left. */
    private int repair(Wolf wolf, int available, int durabilityPerExperience) {
        if (wolf.getEquipment() == null) {
            return available;
        }
        ItemStack armor = wolf.getEquipment().getItem(EquipmentSlot.BODY);
        if (armor == null || armor.getType() != Material.WOLF_ARMOR) {
            return available;
        }
        if (mendingLevel(armor) <= 0) {
            return available;
        }
        int damage = armor.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        if (damage <= 0) {
            return available;
        }

        int repairable = Math.min(damage, available * durabilityPerExperience);
        // Rounded up, so a point of experience is never spent for nothing.
        int spent = (repairable + durabilityPerExperience - 1) / durabilityPerExperience;

        ItemStack repaired = armor.clone();
        repaired.setData(DataComponentTypes.DAMAGE, damage - repairable);
        wolf.getEquipment().setItem(EquipmentSlot.BODY, repaired);
        wolf.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                wolf.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0);
        return available - spent;
    }

    /** The Mending level on an item, or 0 when it has none. */
    private int mendingLevel(ItemStack item) {
        if (!mendingResolved) {
            mending = TierManager.enchantmentByName(MENDING);
            mendingResolved = true;
        }
        if (mending == null || !item.containsEnchantment(mending)) {
            return 0;
        }
        return item.getEnchantmentLevel(mending);
    }
}
