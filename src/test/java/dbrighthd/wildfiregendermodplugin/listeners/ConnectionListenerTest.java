package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.PaperConfigurationBridge;
import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import dbrighthd.wildfiregendermodplugin.networking.SyncStateCoordinator;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionListenerTest {
    @Test
    void quitClearsProfileNegotiationAndRateLimitState() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        InboundPacketGuard guard = mock(InboundPacketGuard.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        UserManager userManager = new UserManager();
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        userManager.put(ProtocolTest.testUser(playerId));
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getInboundPacketGuard()).thenReturn(guard);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(event.getPlayer()).thenReturn(player);

        new ConnectionListener(plugin).onPlayerQuit(event);

        assertTrue(userManager.snapshot().isEmpty());
        verify(coordinator).remove(player);
        verify(guard).remove(playerId);
    }

    @Test
    void joinWithPendingV5TriggersModernSync() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationBridge bridge = mock(PaperConfigurationBridge.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        UserManager userManager = new UserManager();

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        when(plugin.getPaperConfigurationBridge()).thenReturn(bridge);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(bridge.consumePendingV5(playerId)).thenReturn(true);

        new ConnectionListener(plugin).onPlayerJoin(event);

        verify(coordinator).onChannelRegistered(player, ModConstants.CLIENTBOUND_SYNC_MODERN);
        verify(coordinator).onHelloAccepted(player);
        verify(networkManager).syncTo(player);
    }

    @Test
    void joinWithPendingV5ByNameTriggersModernSync() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationBridge bridge = mock(PaperConfigurationBridge.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        UserManager userManager = new UserManager();

        UUID offlinePlayerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(offlinePlayerId);
        when(player.getName()).thenReturn("Ever0141");

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        when(plugin.getPaperConfigurationBridge()).thenReturn(bridge);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(bridge.consumePendingV5(offlinePlayerId, "Ever0141")).thenReturn(true);

        new ConnectionListener(plugin).onPlayerJoin(event);

        verify(coordinator).onChannelRegistered(player, ModConstants.CLIENTBOUND_SYNC_MODERN);
        verify(coordinator).onHelloAccepted(player);
        verify(networkManager).syncTo(player);
        org.junit.jupiter.api.Assertions.assertNotNull(userManager.get(offlinePlayerId));
    }
}
