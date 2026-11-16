package dev.dogarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Reads and sends the configured MiniMessage messages. */
public final class Messages {

    private final DogArmorPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(DogArmorPlugin plugin) {
        this.plugin = plugin;
    }

    public Component get(String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key, "<red>Missing message: " + key);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return miniMessage.deserialize(prefix + raw, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(get(key, resolvers));
    }
}
