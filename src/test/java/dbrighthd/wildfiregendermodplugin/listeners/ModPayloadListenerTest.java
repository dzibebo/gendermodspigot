package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import dbrighthd.wildfiregendermodplugin.networking.SyncStateCoordinator;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModPayloadListenerTest {
    @Test
    void rejectsOversizedPayloadBeforeRateLimitingOrParsing() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID());

        fixture.listener().onPluginMessageReceived(
                ModConstants.SEND_GENDER_INFO, player, new byte[InboundPacketGuard.MAX_SYNC_BYTES + 1]);

        verify(fixture.guard(), never()).tryAcquire(any(UUID.class));
        verify(fixture.networkManager(), never()).deserializeUser(any(byte[].class), eq(false));
    }

    @Test
    void rejectsOversizedHelloBeforeRateLimitingOrParsing() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID());

        fixture.listener().onPluginMessageReceived(
                ModConstants.SERVERBOUND_HELLO, player, new byte[InboundPacketGuard.MAX_HELLO_BYTES + 1]);

        verify(fixture.guard(), never()).tryAcquire(any(UUID.class));
        verify(fixture.networkManager(), never()).handleHello(any(Player.class), any(byte[].class));
    }

    @Test
    void acceptsPayloadAtExactSizeLimit() throws IOException {
        Fixture fixture = fixture();
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        ModUser user = ProtocolTest.testUser(playerId);
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false))).thenReturn(user);

        fixture.listener().onPluginMessageReceived(
                ModConstants.SEND_GENDER_INFO, player, new byte[InboundPacketGuard.MAX_SYNC_BYTES]);

        verify(fixture.guard()).tryAcquire(playerId);
        verify(fixture.networkManager()).deserializeUser(any(byte[].class), eq(false));
        verify(fixture.networkManager()).broadcast(player, user);
    }

    @Test
    void dropsRateLimitedPayloadBeforeParsing() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID());
        when(fixture.guard().tryAcquire(player.getUniqueId())).thenReturn(false);

        fixture.listener().onPluginMessageReceived(ModConstants.SEND_GENDER_INFO, player, new byte[]{1});

        verify(fixture.networkManager(), never()).deserializeUser(any(byte[].class), eq(false));
        verify(fixture.logger(), times(1)).warning(anyString(), eq("Tester"));
    }

    @Test
    void ignoresMalformedPayloadWithoutChangingState() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID());
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false)))
                .thenThrow(new IOException("malformed"));

        fixture.listener().onPluginMessageReceived(ModConstants.SEND_GENDER_INFO, player, new byte[]{1});

        assertTrue(fixture.userManager().snapshot().isEmpty());
        verify(fixture.networkManager(), never()).broadcast(any(Player.class), any(ModUser.class));
    }

    @Test
    void rejectsPayloadForAnotherUuidWithoutChangingState() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID());
        ModUser forgedUser = ProtocolTest.testUser(UUID.randomUUID());
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false))).thenReturn(forgedUser);

        fixture.listener().onPluginMessageReceived(ModConstants.SEND_GENDER_INFO, player, new byte[]{1});

        assertTrue(fixture.userManager().snapshot().isEmpty());
        verify(fixture.networkManager(), never()).broadcast(any(Player.class), any(ModUser.class));
    }

    @Test
    void acceptsOnlyCompatibleHelloVersions() throws IOException {
        Fixture fixture = fixture();
        Player accepted = player(UUID.randomUUID());
        Player mismatched = player(UUID.randomUUID());
        when(fixture.networkManager().handleHello(accepted, new byte[]{1}))
                .thenReturn(new NetworkManager.HelloResult(1, true));
        when(fixture.networkManager().handleHello(mismatched, new byte[]{2}))
                .thenReturn(new NetworkManager.HelloResult(2, false));

        fixture.listener().onPluginMessageReceived(ModConstants.SERVERBOUND_HELLO, accepted, new byte[]{1});
        fixture.listener().onPluginMessageReceived(ModConstants.SERVERBOUND_HELLO, mismatched, new byte[]{2});

        verify(fixture.coordinator()).onHelloAccepted(accepted);
        verify(fixture.coordinator(), never()).onHelloAccepted(mismatched);
    }

    private static Fixture fixture() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        InboundPacketGuard guard = mock(InboundPacketGuard.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        UserManager userManager = new UserManager();
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(plugin.getInboundPacketGuard()).thenReturn(guard);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(guard.tryAcquire(any(UUID.class))).thenReturn(true);
        when(guard.shouldWarn(any(UUID.class))).thenReturn(true);
        return new Fixture(new ModPayloadListener(plugin), networkManager, guard, coordinator, logger, userManager);
    }

    private static Player player(UUID userId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(userId);
        when(player.getName()).thenReturn("Tester");
        return player;
    }

    private record Fixture(
            ModPayloadListener listener,
            NetworkManager networkManager,
            InboundPacketGuard guard,
            SyncStateCoordinator coordinator,
            CustomPluginLogger logger,
            UserManager userManager) {
    }
}
