package dbrighthd.wildfiregendermodplugin.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
