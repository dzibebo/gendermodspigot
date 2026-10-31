package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncStateCoordinatorTest {
    @Test
    void catchesUpWhenChannelPrecedesHello() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        verify(fixture.networkManager(), never()).syncTo(viewer);

        fixture.coordinator().onHelloAccepted(viewer);
        verify(fixture.networkManager()).syncTo(viewer);
        assertEquals(ModConstants.SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void catchesUpWhenHelloPrecedesChannel() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onHelloAccepted(viewer);
        verify(fixture.networkManager(), never()).syncTo(viewer);

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        verify(fixture.networkManager()).syncTo(viewer);
    }

    @Test
    void legacyFabricBecomesReadyOnChannelRegistration() {
        Fixture fixture = fixture(false);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);

        verify(fixture.networkManager()).syncTo(viewer);
        assertEquals(ModConstants.SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void forgeDoesNotRequireHello() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.FORGE);

        verify(fixture.networkManager()).syncTo(viewer);
        assertEquals(ModConstants.FORGE, fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void fabricTakesPriorityWithoutDuplicateCatchUp() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.FORGE);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onHelloAccepted(viewer);

        verify(fixture.networkManager(), times(1)).syncTo(viewer);
        assertEquals(ModConstants.SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void unregisteringFabricClearsNegotiationState() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onHelloAccepted(viewer);
        fixture.coordinator().onChannelUnregistered(viewer, ModConstants.SYNC);

        assertNull(fixture.coordinator().preferredChannel(viewer));
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        assertNull(fixture.coordinator().preferredChannel(viewer));
        verify(fixture.networkManager(), times(1)).syncTo(viewer);
    }

    private static Fixture fixture(boolean supportsHello) {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(networkManager.supportsHello()).thenReturn(supportsHello);
        return new Fixture(new SyncStateCoordinator(plugin), networkManager);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private record Fixture(SyncStateCoordinator coordinator, NetworkManager networkManager) {
    }
}
