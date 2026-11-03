package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Handles payload packets from mod users.
 *
 * @author winnpixie
 */
public class ModPayloadListener implements PluginMessageListener {
    private final GenderModPlugin plugin;

    public ModPayloadListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        boolean hello = channel.equals(ModConstants.SERVERBOUND_HELLO)
                || channel.equals(ModConstants.SERVERBOUND_HELLO_MODERN);

        boolean isForge = channel.equals(ModConstants.FORGE) || channel.equals(ModConstants.FORGE_MODERN);

        boolean syncChannel = channel.equals(ModConstants.SEND_GENDER_INFO)
                || channel.equals(ModConstants.SERVERBOUND_SYNC_MODERN)
                || channel.equals(ModConstants.SERVERBOUND_SYNC_LEGACY)
                || channel.equals(ModConstants.SYNC_MODERN)
                || isForge;

        if (!hello && !syncChannel) return;

        int maximumSize = hello ? InboundPacketGuard.MAX_HELLO_BYTES : InboundPacketGuard.MAX_SYNC_BYTES;
        if (message.length > maximumSize) {
            warn(player, "Rejected oversized " + (hello ? "hello" : "sync")
                    + " payload from %s (" + message.length + " bytes)");
            return;
        }

        if (!plugin.getInboundPacketGuard().tryAcquire(player.getUniqueId())) {
            warn(player, "Rate limit exceeded by %s; dropping client payloads");
            return;
        }

        if (hello) {
            handleHello(player, channel, message);
            return;
        }

        ModUser user;
        try {
            user = plugin.getNetworkManager().deserializeUser(player, message, isForge);
            if (user == null) {
                user = plugin.getNetworkManager().deserializeUser(message, isForge);
            }
        } catch (IOException ex) {
            warn(player, ex, "Rejected malformed sync payload from %s");
            return;
        }

        if (user == null) {
            return;
        }

        if (user.userId() == null && player != null) {
            user = new ModUser(player.getUniqueId(), user.configuration());
        }

        if (player != null && !player.getUniqueId().equals(user.userId())) {
            warn(player, "Unauthorized configuration update by %s for " + user.userId());
            return;
        }

        plugin.getUserManager().put(user, player != null ? player.getName() : null);
        plugin.getCustomLogger().debug("Stored %s as %s",
                player != null ? player.getName() : user.userId(),
                user.configuration().generalOptions().genderIdentity().name());

        if (player != null) {
            if (channel.equals(ModConstants.SERVERBOUND_SYNC_MODERN) || channel.equals(ModConstants.SYNC_MODERN)) {
                plugin.getSyncStateCoordinator().onHelloAccepted(player);
                plugin.getSyncStateCoordinator().onChannelRegistered(player, ModConstants.CLIENTBOUND_SYNC_MODERN);
            } else if (channel.equals(ModConstants.SERVERBOUND_SYNC_LEGACY)) {
                plugin.getSyncStateCoordinator().onHelloAccepted(player);
                plugin.getSyncStateCoordinator().onChannelRegistered(player, ModConstants.CLIENTBOUND_SYNC_LEGACY);
            } else if (channel.equals(ModConstants.SEND_GENDER_INFO)) {
                plugin.getSyncStateCoordinator().onChannelRegistered(player, ModConstants.SYNC);
            } else if (isForge) {
                plugin.getSyncStateCoordinator().onChannelRegistered(player, channel);
            }

            plugin.getNetworkManager().broadcast(player, user);
        }
    }

    private void handleHello(Player player, String channel, byte[] message) {
        try {
            NetworkManager.HelloResult result = ModConstants.SERVERBOUND_HELLO_MODERN.equals(channel)
                    ? plugin.getNetworkManager().handleHello(player, channel, message)
                    : plugin.getNetworkManager().handleHello(player, message);
            if (result.compatible()) {
                plugin.getSyncStateCoordinator().onHelloAccepted(player);
            } else {
                warn(player, "Unsupported sync hello version from %s: " + result.clientVersion());
            }
        } catch (IOException ex) {
            warn(player, ex, "Rejected malformed hello payload from %s");
        }
    }

    private void warn(Player player, String message) {
        if (plugin.getInboundPacketGuard().shouldWarn(player.getUniqueId())) {
            plugin.getCustomLogger().warning(message, player.getName());
        }
    }

    private void warn(Player player, Throwable throwable, String message) {
        if (plugin.getInboundPacketGuard().shouldWarn(player.getUniqueId())) {
            plugin.getCustomLogger().warning(throwable, message, player.getName());
        }
    }
}
