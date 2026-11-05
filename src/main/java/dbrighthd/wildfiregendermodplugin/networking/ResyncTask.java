package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class ResyncTask implements Runnable {
    static final long RUNS_PER_INTERVAL = Duration.ofNanos(SyncStateCoordinator.RESYNC_INTERVAL_NANOS).toSeconds();

    private final GenderModPlugin plugin;

    public ResyncTask(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    static int viewersPerRun(int onlineViewers) {
        return (int) Math.max(1, (onlineViewers + RUNS_PER_INTERVAL - 1) / RUNS_PER_INTERVAL);
    }

    @Override
    public void run() {
        var online = plugin.getServer().getOnlinePlayers();
        int limit = viewersPerRun(online.size());
        long now = System.nanoTime();
        int resynced = 0;

        for (Player viewer : online) {
            if (resynced >= limit) return;

            if (plugin.getSyncStateCoordinator().claimResync(viewer, now)) {
                plugin.getNetworkManager().syncTo(viewer);
                resynced++;
            }
        }
    }
}
