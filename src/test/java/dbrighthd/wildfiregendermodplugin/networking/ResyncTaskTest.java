package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResyncTaskTest {
    @Test
    void spreadsAFullCycleAcrossTheInterval() {
        assertEquals(1, ResyncTask.viewersPerRun(0));
        assertEquals(1, ResyncTask.viewersPerRun(1));
        assertEquals(1, ResyncTask.viewersPerRun(200));
        assertEquals(1, ResyncTask.viewersPerRun(300));
        assertEquals(2, ResyncTask.viewersPerRun(301));
        assertEquals(3, ResyncTask.viewersPerRun(601));
    }

    @Test
    void resyncsOneDueViewerPerRunOnSmallerServers() {
        Fixture fixture = fixture();
        List<Player> viewers = IntStream.range(0, 5).mapToObj(index -> mock(Player.class)).toList();
        doReturn(viewers).when(fixture.server()).getOnlinePlayers();
        when(fixture.coordinator().claimResync(any(), anyLong())).thenReturn(true);

        new ResyncTask(fixture.plugin()).run();

        verify(fixture.networkManager(), times(1)).syncTo(viewers.getFirst());
        verify(fixture.networkManager(), times(1)).syncTo(any());
    }

    @Test
    void skipsViewersThatAreNotDue() {
        Fixture fixture = fixture();
        Player viewer = mock(Player.class);
        doReturn(List.of(viewer)).when(fixture.server()).getOnlinePlayers();
        when(fixture.coordinator().claimResync(any(), anyLong())).thenReturn(false);

        new ResyncTask(fixture.plugin()).run();

        verify(fixture.networkManager(), never()).syncTo(any());
    }

    private static Fixture fixture() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Server server = mock(Server.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        return new Fixture(plugin, server, coordinator, networkManager);
    }

    private record Fixture(GenderModPlugin plugin, Server server, SyncStateCoordinator coordinator, NetworkManager networkManager) {
    }
}
