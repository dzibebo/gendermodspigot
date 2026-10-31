package dbrighthd.wildfiregendermodplugin.wildfire;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UserManager {
    private final ConcurrentMap<UUID, ModUser> users = new ConcurrentHashMap<>();

    public void put(ModUser user) {
        users.put(user.userId(), user);
    }

    public void remove(UUID userId) {
        users.remove(userId);
    }

    public List<ModUser> snapshot() {
        return List.copyOf(users.values());
    }

    public void clear() {
        users.clear();
    }
}
