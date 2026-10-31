package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SyncStateCoordinator {
    private final GenderModPlugin plugin;
    private final ConcurrentMap<UUID, ViewerState> viewers = new ConcurrentHashMap<>();

    public SyncStateCoordinator(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    public void onChannelRegistered(Player viewer, String channel) {
        if (!isSyncChannel(channel)) return;

        ViewerState state = state(viewer);
        boolean catchUp;
        synchronized (state) {
            boolean wasReady = preferredChannel(state) != null;
            if (!state.channels.add(channel)) return;
            catchUp = !wasReady && preferredChannel(state) != null;
        }
        if (catchUp) plugin.getNetworkManager().syncTo(viewer);
    }

    public void onChannelUnregistered(Player viewer, String channel) {
        if (!isSyncChannel(channel)) return;

        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return;

        synchronized (state) {
            state.channels.remove(channel);
            if (channel.equals(ModConstants.SYNC)) state.v5HelloAccepted = false;
        }
    }

    public void onHelloAccepted(Player viewer) {
        if (!plugin.getNetworkManager().supportsHello()) return;

        ViewerState state = state(viewer);
        boolean catchUp;
        synchronized (state) {
            boolean wasReady = preferredChannel(state) != null;
            state.v5HelloAccepted = true;
            catchUp = !wasReady && preferredChannel(state) != null;
        }
        if (catchUp) plugin.getNetworkManager().syncTo(viewer);
    }

    public boolean isHelloAccepted(Player viewer) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return false;

        synchronized (state) {
            return state.v5HelloAccepted;
        }
    }

    public String preferredChannel(Player viewer) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return null;

        synchronized (state) {
            return preferredChannel(state);
        }
    }

    public void remove(Player player) {
        viewers.remove(player.getUniqueId());
    }

    public void clear() {
        viewers.clear();
    }

    private ViewerState state(Player viewer) {
        return viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerState());
    }

    private String preferredChannel(ViewerState state) {
        if (state.channels.contains(ModConstants.SYNC)
                && (!plugin.getNetworkManager().supportsHello() || state.v5HelloAccepted)) {
            return ModConstants.SYNC;
        }
        if (state.channels.contains(ModConstants.FORGE)) return ModConstants.FORGE;
        return null;
    }

    private static boolean isSyncChannel(String channel) {
        return channel.equals(ModConstants.SYNC) || channel.equals(ModConstants.FORGE);
    }

    private static final class ViewerState {
        private final Set<String> channels = new HashSet<>();
        private boolean v5HelloAccepted;
    }
}
