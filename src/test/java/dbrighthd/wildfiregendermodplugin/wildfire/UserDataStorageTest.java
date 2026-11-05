package dbrighthd.wildfiregendermodplugin.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserDataStorageTest {
    @Test
    void keepsTheLatestProfileWhenSavesAreQueuedTogether(@TempDir File tempDir) {
        UserDataStorage storage = new UserDataStorage(tempDir);
        UUID userId = UUID.randomUUID();
        ModUser male = new ModUser(userId, ModSyncPacketV5.defaultConfiguration(GenderIdentities.MALE));
        ModUser other = ProtocolTest.testUser(userId);

        storage.save(male, "first");
        storage.save(other, "second");
        storage.save(other, "third");
        storage.close();

        Map<UUID, ModUser> users = new HashMap<>();
        Map<String, UUID> names = new HashMap<>();
        UserDataStorage reader = new UserDataStorage(tempDir);
        reader.loadAll(users, names);
        reader.close();

        assertEquals(GenderIdentities.OTHER, users.get(userId).configuration().generalOptions().genderIdentity());
        assertEquals(Map.of("third", userId), names);
    }

    @Test
    void writesDirectlyOnceTheExecutorIsClosed(@TempDir File tempDir) {
        UserDataStorage storage = new UserDataStorage(tempDir);
        UUID userId = UUID.randomUUID();
        storage.close();

        storage.save(ProtocolTest.testUser(userId), "late");

        Map<UUID, ModUser> users = new HashMap<>();
        Map<String, UUID> names = new HashMap<>();
        UserDataStorage reader = new UserDataStorage(tempDir);
        reader.loadAll(users, names);
        reader.close();

        assertNotNull(users.get(userId));
        assertEquals(userId, names.get("late"));
    }
}
