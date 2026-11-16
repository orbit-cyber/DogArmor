package dev.dogarmor;

import dev.dogarmor.command.DogArmorCommand;
import dev.dogarmor.listener.MendingListener;
import dev.dogarmor.listener.WolfArmorListener;
import dev.dogarmor.tier.TierManager;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DogArmorPlugin extends JavaPlugin {

    private TierManager tierManager;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        tierManager = new TierManager(new NamespacedKey(this, "tier"));
        try {
            tierManager.load(getConfig());
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load tiers from config.yml: " + exception.getMessage());
        }

        WolfArmorListener listener = new WolfArmorListener(tierManager, messages);
        getServer().getPluginManager().registerEvents(listener, this);
        // Mending on diamond and netherite armour would otherwise never repair
        // anything, because a dog does not collect experience.
        getServer().getPluginManager().registerEvents(new MendingListener(this, tierManager), this);

        DogArmorCommand command = new DogArmorCommand(this, tierManager, messages, listener);
        PluginCommand pluginCommand = getCommand("dogarmor");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        DogArmorApi.install(new DogArmorApi(this, tierManager));

        getLogger().info("DogArmor enabled with " + tierManager.tiers().size() + " armour tiers.");
    }

    @Override
    public void onDisable() {
        // Before anything else: a panel that asks after this gets "not installed"
        // rather than a call into a plugin that is on its way out.
        DogArmorApi.uninstall();
    }

    /** Reloads config.yml and the tier list; false when the tier list is invalid. */
    public boolean reloadAll() {
        reloadConfig();
        try {
            tierManager.load(getConfig());
            return true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load tiers from config.yml: " + exception.getMessage());
            return false;
        }
    }
}
