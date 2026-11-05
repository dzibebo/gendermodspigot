package dbrighthd.wildfiregendermodplugin.wildfire;

import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UserManager {
    private final ConcurrentMap<UUID, ModUser> activeUsers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ModUser> savedUsers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final UserDataStorage storage;

    public UserManager() {
        this((UserDataStorage) null);
    }

    public UserManager(File directory) {
        this(directory != null ? new UserDataStorage(directory) : null);
    }

    public UserManager(UserDataStorage storage) {
        this.storage = storage;
        if (storage != null) {
            storage.loadAll(savedUsers, nameIndex);
        }
    }

    public void put(ModUser user) {
        put(user, null);
    }

    public void put(ModUser user, String username) {
        if (user == null || user.userId() == null) return;

        UUID userId = user.userId();
        activeUsers.put(userId, user);
        ModUser previous = savedUsers.put(userId, user);
        boolean nameUnchanged = username == null || username.isBlank()
                || userId.equals(nameIndex.put(username.toLowerCase(Locale.ROOT), userId));

        if (storage != null && !(nameUnchanged && user.equals(previous))) {
            storage.save(user, username);
        }
    }

    public void saveSynchronously(ModUser user, String username) {
        if (user == null || user.userId() == null) return;

        activeUsers.put(user.userId(), user);
        savedUsers.put(user.userId(), user);

        if (username != null && !username.isBlank()) {
            nameIndex.put(username.toLowerCase(Locale.ROOT), user.userId());
        }

        if (storage != null) {
            storage.saveSynchronously(user, username);
        }
    }

    public ModUser get(UUID userId) {
        if (userId == null) return null;
        ModUser active = activeUsers.get(userId);
        if (active != null) return active;
        return savedUsers.get(userId);
    }

    public ModUser getByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        UUID id = nameIndex.get(username.toLowerCase(Locale.ROOT));
        if (id == null) return null;
        return get(id);
    }

    public ModUser getByPlayer(Player player) {
        if (player == null) return null;
        ModUser user = get(player.getUniqueId());
        if (user != null) return user;
        return getByUsername(player.getName());
    }

    public void remove(UUID userId) {
        if (userId != null) {
            activeUsers.remove(userId);
        }
    }

    public List<ModUser> snapshot() {
        return List.copyOf(activeUsers.values());
    }

    public void clear() {
        activeUsers.clear();
    }

    public void close() {
        if (storage != null) {
            storage.close();
        }
        activeUsers.clear();
    }
}
