package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;

import java.util.Set;
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
    void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        boolean v5HandshakeDone = plugin.getPaperConfigurationBridge() != null
                && (plugin.getPaperConfigurationBridge().consumePendingV5(uuid)
                || plugin.getPaperConfigurationBridge().consumePendingV5(uuid, name));

        if (!v5HandshakeDone) {
            Set<String> channels = player.getListeningPluginChannels();
            if (channels.contains(ModConstants.CLIENTBOUND_SYNC_MODERN) || channels.contains(ModConstants.SYNC_MODERN)) {
                v5HandshakeDone = true;
            }
        }

        if (v5HandshakeDone) {
            plugin.getSyncStateCoordinator().onChannelRegistered(player, ModConstants.CLIENTBOUND_SYNC_MODERN);
            plugin.getSyncStateCoordinator().onHelloAccepted(player);
            plugin.getNetworkManager().syncTo(player);

            ModUser existing = plugin.getUserManager().get(uuid);
            if (existing != null) {
                plugin.getNetworkManager().broadcast(player, existing);
            } else {
                ModUser defaultUser = new ModUser(uuid, ModSyncPacketV5.defaultConfiguration(GenderIdentities.MALE));
                plugin.getUserManager().put(defaultUser);
                plugin.getNetworkManager().broadcast(player, defaultUser);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onChannelRegistered(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        String channel = event.getChannel();
        plugin.getSyncStateCoordinator().onChannelRegistered(player, channel);

        if (ModConstants.CLIENTBOUND_SYNC_MODERN.equals(channel)
                || ModConstants.SYNC_MODERN.equals(channel)
                || ModConstants.CLIENTBOUND_SYNC_LEGACY.equals(channel)
                || ModConstants.SYNC.equals(channel)) {
            plugin.getSyncStateCoordinator().onHelloAccepted(player);
            plugin.getNetworkManager().syncTo(player);

            ModUser existing = plugin.getUserManager().get(player.getUniqueId());
            if (existing != null) {
                plugin.getNetworkManager().broadcast(player, existing);
            } else if (ModConstants.CLIENTBOUND_SYNC_MODERN.equals(channel) || ModConstants.SYNC_MODERN.equals(channel)) {
                ModUser defaultUser = new ModUser(player.getUniqueId(), ModSyncPacketV5.defaultConfiguration(GenderIdentities.MALE));
                plugin.getUserManager().put(defaultUser);
                plugin.getNetworkManager().broadcast(player, defaultUser);
            }
        }

        if (plugin.getPaperConfigurationBridge() == null || !plugin.getPaperConfigurationBridge().isEnabled()) {
            if (ModConstants.CLIENTBOUND_HELLO_MODERN.equals(channel)
                    || ModConstants.CLIENTBOUND_SYNC_MODERN.equals(channel)) {
                if (!plugin.getSyncStateCoordinator().isHelloAccepted(player)) {
                    player.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_HELLO_MODERN,
                            NetworkManager.createHelloPayload());
                }
            } else if (ModConstants.CLIENTBOUND_HELLO.equals(channel)) {
                if (!plugin.getSyncStateCoordinator().isHelloAccepted(player)) {
                    player.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_HELLO,
                            NetworkManager.createHelloPayload());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSyncStateCoordinator().preferredChannel(player) != null) {
            plugin.getNetworkManager().syncTo(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.getSyncStateCoordinator().preferredChannel(player) != null) {
                plugin.getNetworkManager().syncTo(player);
            }
        });
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
        if (plugin.getPaperConfigurationBridge() != null) {
            plugin.getPaperConfigurationBridge().remove(uuid, player.getName());
        }
        plugin.getSyncStateCoordinator().remove(player);
        plugin.getInboundPacketGuard().remove(uuid);
        plugin.getUserManager().remove(uuid);
    }
}
