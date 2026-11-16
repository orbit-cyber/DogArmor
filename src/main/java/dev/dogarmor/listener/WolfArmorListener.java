package dev.dogarmor.listener;

import java.util.Map;

import dev.dogarmor.Messages;
import dev.dogarmor.tier.DogArmorTier;
import dev.dogarmor.tier.TierManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Right-clicking a tamed dog while holding the next tier's upgrade
 * material upgrades its armour, consuming the material.
 */
public final class WolfArmorListener implements Listener {

    private final TierManager tierManager;
    private final Messages messages;

    public WolfArmorListener(TierManager tierManager, Messages messages) {
        this.tierManager = tierManager;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Wolf wolf)) {
            return;
        }
        Player player = event.getPlayer();
        // PlayerInteractEntityEvent has no getItem(): the held item comes from
        // the player's own inventory for the hand that was used.
        DogArmorTier candidate = tierManager.candidateFor(materialOf(player.getInventory().getItem(event.getHand())));
        if (candidate == null) {
            return; // Not holding an upgrade material - leave vanilla behaviour alone.
        }

        // The checks come before setCancelled(): a stranger holding an ingot must
        // not have their click swallowed, and an untamed wolf must still behave
        // like an untamed wolf.
        if (!player.hasPermission("dogarmor.use")) {
            messages.send(player, "no-permission");
            return;
        }
        if (!wolf.isTamed()) {
            messages.send(player, "wolf-not-tamed");
            return;
        }
        if (!isOwnerOrAdmin(wolf, player)) {
            messages.send(player, "not-your-dog");
            return;
        }
        event.setCancelled(true);

        ItemStack armor = wolf.getEquipment() == null ? null
                : wolf.getEquipment().getItem(EquipmentSlot.BODY);
        DogArmorTier current = tierManager.tierOf(armor);
        if (current == null) {
            messages.send(player, "no-armor");
            return;
        }
        if (candidate.index() != current.index() + 1) {
            // Only claim "max tier" when there really is no further tier.
            if (tierManager.nextTier(current.index()) == null) {
                messages.send(player, "max-tier", Placeholder.component("tier", current.displayName()));
            } else {
                messages.send(player, "must-upgrade-in-order",
                        Placeholder.component("tier", current.displayName()));
            }
            return;
        }

        tryUpgrade(player, wolf, armor, current, candidate);
    }

    /** Shared by the listener and /dogarmor upgrade. */
    public void tryUpgrade(Player player, Wolf wolf, @Nullable ItemStack armor, DogArmorTier current,
            DogArmorTier next) {
        Material material = next.upgradeMaterial();
        int cost = next.upgradeCost();
        ItemStack costStack = new ItemStack(material, cost);
        if (!player.getInventory().containsAtLeast(costStack, cost)) {
            messages.send(player, "need",
                    Placeholder.unparsed("material", pretty(material)),
                    Placeholder.unparsed("amount", String.valueOf(cost)),
                    Placeholder.component("tier", next.displayName()));
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().removeItem(costStack);
        if (!leftover.isEmpty()) {
            // removeItem() takes what it can and returns the rest. Anything it
            // did take is given back here, so a partial removal never costs the
            // player materials for an upgrade that did not happen.
            int removed = cost;
            for (ItemStack stack : leftover.values()) {
                removed -= stack.getAmount();
            }
            if (removed > 0) {
                player.getInventory().addItem(new ItemStack(material, removed));
            }
            messages.send(player, "need",
                    Placeholder.unparsed("material", pretty(material)),
                    Placeholder.unparsed("amount", String.valueOf(cost)),
                    Placeholder.component("tier", next.displayName()));
            return;
        }

        tierManager.equip(wolf, next, armor);
        tierManager.playUpgradeEffects(wolf);
        messages.send(player, "upgraded", Placeholder.component("tier", next.displayName()));
    }

    private boolean isOwnerOrAdmin(Wolf wolf, Player player) {
        if (player.hasPermission("dogarmor.admin")) {
            return true;
        }
        return wolf.getOwner() instanceof Player owner && owner.getUniqueId().equals(player.getUniqueId());
    }

    private static @Nullable Material materialOf(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.getType();
    }

    public static String pretty(@Nullable Material material) {
        return material == null ? "nothing"
                : material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
