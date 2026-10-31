package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkManagerTest {
    @Test
    void reportsHelloSupportForKnownProtocols() {
        assertFalse(fixture(4).networkManager().supportsHello());
        assertTrue(fixture(5).networkManager().supportsHello());
    }

    @Test
    void repliesToCompatibleV5Hello() throws IOException {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        when(player.getListeningPluginChannels()).thenReturn(Set.of(ModConstants.CLIENTBOUND_HELLO));
        NetworkManager manager = new NetworkManager(plugin);

        NetworkManager.HelloResult result = manager.handleHello(player, new byte[]{1});

        assertTrue(result.compatible());
        ArgumentCaptor<byte[]> response = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(ModConstants.CLIENTBOUND_HELLO), response.capture());
        assertArrayEquals(new byte[]{1}, response.getValue());
    }

    @Test
    void reportsMismatchedHelloAndRejectsTrailingData() throws IOException {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        when(player.getListeningPluginChannels()).thenReturn(Set.of());
        NetworkManager manager = new NetworkManager(plugin);

        assertFalse(manager.handleHello(player, new byte[]{2}).compatible());
        verify(player, never()).sendPluginMessage(any(), any(), any());
        assertThrows(IOException.class, () -> manager.handleHello(player, new byte[]{1, 0}));
    }

    @Test
    void broadcastsOneProfileThroughEachRecipientsPreferredTransport() {
        Fixture fixture = fixture(5);
        Player source = player(UUID.randomUUID());
        Player fabric = player(UUID.randomUUID());
        Player forge = player(UUID.randomUUID());
        Player unavailable = player(UUID.randomUUID());
        doReturn(List.of(source, fabric, forge, unavailable)).when(fixture.server()).getOnlinePlayers();
        when(fixture.coordinator().preferredChannel(fabric)).thenReturn(ModConstants.SYNC);
        when(fixture.coordinator().preferredChannel(forge)).thenReturn(ModConstants.FORGE);
        ModUser user = ProtocolTest.testUser(source.getUniqueId());

        fixture.networkManager().broadcast(source, user);

        verify(source, never()).sendPluginMessage(any(), any(), any());
        verify(fabric, times(1)).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
        verify(forge, times(1)).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.FORGE), any(byte[].class));
        verify(unavailable, never()).sendPluginMessage(any(), any(), any());
    }

    @Test
    void channelCatchUpSendsStoredProfilesExceptTheViewersOwn() {
        Fixture fixture = fixture(4);
        UUID viewerId = UUID.randomUUID();
        Player viewer = player(viewerId);
        fixture.userManager().put(ProtocolTest.testUser(viewerId));
        fixture.userManager().put(ProtocolTest.testUser(UUID.randomUUID()));
        when(fixture.coordinator().preferredChannel(viewer)).thenReturn(ModConstants.SYNC);

        fixture.networkManager().syncTo(viewer);

        verify(viewer, times(1)).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
    }

    @Test
    void rejectsUnsupportedProtocolAndInvalidForgeDiscriminator() {
        Fixture unsupported = fixtureWithoutInit(1);
        assertFalse(unsupported.networkManager().init());

        Fixture fixture = fixture(5);
        assertThrows(IOException.class, () -> fixture.networkManager().deserializeUser(new byte[]{0}, true));
    }

    private static Fixture fixture(int protocol) {
        Fixture fixture = fixtureWithoutInit(protocol);
        assertTrue(fixture.networkManager().init());
        return fixture;
    }

    private static Fixture fixtureWithoutInit(int protocol) {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Server server = mock(Server.class);
        FileConfiguration config = mock(FileConfiguration.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        UserManager userManager = new UserManager();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(config.getInt("mod.protocol", -1)).thenReturn(protocol);
        return new Fixture(plugin, new NetworkManager(plugin), server, coordinator, userManager);
    }

    private static Player player(UUID userId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(userId);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private record Fixture(
            GenderModPlugin plugin,
            NetworkManager networkManager,
            Server server,
            SyncStateCoordinator coordinator,
            UserManager userManager) {
    }
}
