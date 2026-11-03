package dbrighthd.wildfiregendermodplugin.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagerTest {
    @Test
    void exposesStableSnapshotsAndLifecycleOperations() {
        UserManager manager = new UserManager();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        ModUser first = ProtocolTest.testUser(firstId);
        ModUser second = ProtocolTest.testUser(secondId);

        manager.put(first);
        var snapshot = manager.snapshot();
        manager.put(second);

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second));
        assertEquals(2, manager.snapshot().size());

        manager.remove(firstId);
        assertEquals(second, manager.snapshot().getFirst());
        manager.clear();
        assertTrue(manager.snapshot().isEmpty());
    }

    @Test
    void persistsAndReloadsAcrossInstances(@TempDir File tempDir) {
        UserManager manager1 = new UserManager(tempDir);
        UUID femaleId = UUID.randomUUID();
        ModUser femaleUser = ProtocolTest.testUser(femaleId);

        manager1.put(femaleUser, "Alice");
        assertEquals(femaleUser, manager1.get(femaleId));
        assertEquals(femaleUser, manager1.getByUsername("Alice"));

        UserManager manager2 = new UserManager(tempDir);
        ModUser loaded = manager2.get(femaleId);
        assertNotNull(loaded);
        assertEquals(femaleId, loaded.userId());
        assertEquals(femaleUser.configuration().generalOptions().genderIdentity(),
                loaded.configuration().generalOptions().genderIdentity());
        assertEquals(femaleUser.configuration().breastOptions().bustSize(),
                loaded.configuration().breastOptions().bustSize());
        assertEquals(loaded, manager2.getByUsername("alice"));
    }
}
