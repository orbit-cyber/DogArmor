package dev.dogarmor.command;

import java.util.List;
import java.util.Map;

import dev.dogarmor.DogArmorPlugin;
import dev.dogarmor.Messages;
import dev.dogarmor.listener.WolfArmorListener;
import dev.dogarmor.tier.DogArmorTier;
import dev.dogarmor.tier.TierManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class DogArmorCommand implements CommandExecutor, TabCompleter {

    private final DogArmorPlugin plugin;
    private final TierManager tierManager;
    private final Messages messages;
    private final WolfArmorListener listener;

    public DogArmorCommand(DogArmorPlugin plugin, TierManager tierManager, Messages messages,
            WolfArmorListener listener) {
        this.plugin = plugin;
        this.tierManager = tierManager;
        this.messages = messages;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String sub = args.length == 0 ? "info" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "info" -> handleInfo(player);
            case "upgrade" -> handleUpgrade(player);
            case "give" -> handleGive(player, args);
            case "reload" -> handleReload(player);
            default -> handleInfo(player);
        }
        return true;
    }

    private void handleInfo(Player player) {
        if (!player.hasPermission("dogarmor.use")) {
            messages.send(player, "no-permission");
            return;
        }
        Wolf wolf = targetWolf(player);
        if (wolf == null) {
            messages.send(player, "no-wolf-looked-at");
            return;
        }
        ItemStack armor = wolf.getEquipment() == null ? null
                : wolf.getEquipment().getItem(EquipmentSlot.BODY);
        DogArmorTier tier = tierManager.tierOf(armor);
        if (tier == null) {
            messages.send(player, "no-armor");
            return;
        }
        String durability = tierManager.durabilityDisplay(armor);
        DogArmorTier next = tierManager.nextTier(tier.index());
        if (next == null || !next.hasUpgradeMaterial()) {
            messages.send(player, "info-max",
                    Placeholder.component("tier", tier.displayName()),
                    Placeholder.unparsed("durability", durability));
        } else {
            messages.send(player, "info",
                    Placeholder.component("tier", tier.displayName()),
                    Placeholder.unparsed("durability", durability),
                    Placeholder.unparsed("amount", String.valueOf(next.upgradeCost())),
                    Placeholder.unparsed("material", WolfArmorListener.pretty(next.upgradeMaterial())));
        }
    }

    private void handleUpgrade(Player player) {
        if (!player.hasPermission("dogarmor.use")) {
            messages.send(player, "no-permission");
            return;
        }
        Wolf wolf = targetWolf(player);
        if (wolf == null) {
            messages.send(player, "no-wolf-looked-at");
            return;
        }
        if (!wolf.isTamed()) {
            messages.send(player, "wolf-not-tamed");
            return;
        }
        if (!(wolf.getOwner() instanceof Player owner) || !owner.getUniqueId().equals(player.getUniqueId())) {
            if (!player.hasPermission("dogarmor.admin")) {
                messages.send(player, "not-your-dog");
                return;
            }
        }
        ItemStack armor = wolf.getEquipment() == null ? null
                : wolf.getEquipment().getItem(EquipmentSlot.BODY);
        DogArmorTier current = tierManager.tierOf(armor);
        if (current == null) {
            messages.send(player, "no-armor");
            return;
        }
        DogArmorTier next = tierManager.nextTier(current.index());
        if (next == null || !next.hasUpgradeMaterial()) {
            messages.send(player, "max-tier", Placeholder.component("tier", current.displayName()));
            return;
        }
        listener.tryUpgrade(player, wolf, armor, current, next);
    }

    private void handleGive(Player player, String[] args) {
        if (!player.hasPermission("dogarmor.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(player, "give-usage");
            return;
        }
        DogArmorTier tier = tierManager.byKey(args[1]);
        if (tier == null) {
            messages.send(player, "tier-not-found", Placeholder.unparsed("input", args[1]));
            return;
        }
        ItemStack item = tierManager.createItem(tier, null);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> player.getWorld().dropItem(player.getLocation(), stack));
        }
        messages.send(player, "given", Placeholder.component("tier", tier.displayName()));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("dogarmor.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        if (!plugin.reloadAll()) {
            player.sendMessage(Component.text(
                    "Reload failed: the tier list in config.yml is invalid, see the console.", NamedTextColor.RED));
            return;
        }
        messages.send(player, "reloaded");
    }

    private @Nullable Wolf targetWolf(Player player) {
        return player.getTargetEntity(6) instanceof Wolf wolf ? wolf : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = new java.util.ArrayList<>(List.of("info", "upgrade"));
            if (sender.hasPermission("dogarmor.admin")) {
                subs.add("give");
                subs.add("reload");
            }
            return subs.stream().filter(sub -> sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("dogarmor.admin")) {
            return tierManager.tiers().stream()
                    .map(DogArmorTier::key)
                    .filter(key -> key.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
