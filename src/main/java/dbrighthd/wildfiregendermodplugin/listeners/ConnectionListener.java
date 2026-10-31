package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;

import java.util.UUID;

/**
 * Handles player channel and lifecycle events.
 *
 * @author winnpixie
 */
public class ConnectionListener implements Listener {
    private final GenderModPlugin plugin;

    public ConnectionListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChannelRegistered(PlayerRegisterChannelEvent event) {
        plugin.getSyncStateCoordinator().onChannelRegistered(event.getPlayer(), event.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChannelUnregistered(PlayerUnregisterChannelEvent event) {
        plugin.getSyncStateCoordinator().onChannelUnregistered(event.getPlayer(), event.getChannel());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getCustomLogger().debug("Removing %s", player.getName());
        plugin.getSyncStateCoordinator().remove(player);
        plugin.getInboundPacketGuard().remove(uuid);
        plugin.getUserManager().remove(uuid);
    }
}
