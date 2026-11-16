package dev.dogarmor;

import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Reads and sends the configured MiniMessage messages.
 *
 * <p>{@code saveDefaultConfig()} never overwrites an existing config.yml, so a
 * server that updates the jar keeps the messages it had. Every key therefore has
 * a built-in default here: a message added in a later version still reads
 * properly instead of printing "Missing message: ...". config.yml always wins
 * when it sets the key.
 */
public final class Messages {

    private static final String DEFAULT_PREFIX = "<gray>[<aqua>DogArmor<gray>] ";

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("no-permission", "<red>You don't have permission to do that."),
            Map.entry("wolf-not-tamed", "<red>That wolf isn't tamed."),
            Map.entry("not-your-dog", "<red>You can only upgrade armour on your own dog."),
            Map.entry("no-wolf-looked-at", "<red>Look at a wolf to use this command."),
            Map.entry("no-armor", "<red>This dog isn't wearing armour. Equip a wolf armour item first."),
            Map.entry("must-upgrade-in-order",
                    "<red>Upgrades go one tier at a time - this dog is wearing <tier><red>."),
            Map.entry("max-tier", "<green>This armour is already at the highest tier (<tier><green>)."),
            Map.entry("need", "<red>You need <amount>x <material><red> to upgrade this armour to <tier><red>."),
            Map.entry("upgraded", "<green>Armour upgraded to <tier><green>!"),
            Map.entry("removed", "<green>Removed <tier><green> and gave it to you."),
            Map.entry("given", "<green>Given <tier><green>."),
            Map.entry("list-header", "<gray>Dog armour tiers (<count>):"),
            Map.entry("list-empty", "<red>This server has no armour tiers configured."),
            Map.entry("list-entry", "- <tier> <gray>(<durability> durability<gray>), upgrade: "
                    + "<amount>x <material><enchantments>"),
            Map.entry("list-entry-base", "- <tier> <gray>(<durability> durability<gray>), base tier"
                    + "<enchantments>"),
            Map.entry("tier-not-found", "<red>Unknown armour tier '<input><red>'."),
            Map.entry("give-usage", "<red>Usage: /dogarmor give <tier>"),
            Map.entry("reloaded", "<green>Configuration reloaded."),
            Map.entry("info", "This dog is wearing <tier> <gray>(durability <durability>)</gray><white>. "
                    + "Next upgrade: <amount>x <material><white>."),
            Map.entry("info-max", "This dog is wearing <tier> <gray>(durability <durability>)</gray><white> "
                    + "- max tier!"));

    private final DogArmorPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(DogArmorPlugin plugin) {
        this.plugin = plugin;
    }

    public Component get(String key, TagResolver... resolvers) {
        String fallback = DEFAULTS.getOrDefault(key, "<red>Missing message: " + key);
        String raw = plugin.getConfig().getString("messages." + key, fallback);
        String prefix = plugin.getConfig().getString("messages.prefix", DEFAULT_PREFIX);
        return miniMessage.deserialize(prefix + raw, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(get(key, resolvers));
    }

    /**
     * Sends a message without the config prefix, for the lines of a multi-line
     * listing, where repeating "[DogArmor]" on every line would only be noise.
     */
    public void sendRaw(CommandSender sender, String key, TagResolver... resolvers) {
        String fallback = DEFAULTS.getOrDefault(key, "<red>Missing message: " + key);
        String raw = plugin.getConfig().getString("messages." + key, fallback);
        sender.sendMessage(miniMessage.deserialize(raw, resolvers));
    }
}
