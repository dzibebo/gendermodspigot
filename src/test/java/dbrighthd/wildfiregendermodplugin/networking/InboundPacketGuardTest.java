package dbrighthd.wildfiregendermodplugin.networking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundPacketGuardTest {
    @Test
    void deterministicallyExhaustsAndRefillsTokenBucket() {
        AtomicLong clock = new AtomicLong();
        InboundPacketGuard guard = new InboundPacketGuard(clock::get);
        UUID playerId = UUID.randomUUID();

        for (int packet = 0; packet < InboundPacketGuard.CAPACITY; packet++) {
            assertTrue(guard.tryAcquire(playerId));
        }
        assertFalse(guard.tryAcquire(playerId));

        clock.addAndGet(Duration.ofMillis(125).toNanos());
        assertTrue(guard.tryAcquire(playerId));
        assertFalse(guard.tryAcquire(playerId));

        clock.addAndGet(Duration.ofSeconds(1).toNanos());
        for (int packet = 0; packet < InboundPacketGuard.CAPACITY; packet++) {
            assertTrue(guard.tryAcquire(playerId));
        }
        assertFalse(guard.tryAcquire(playerId));
    }

    @Test
    void throttlesWarningsAndResetsTransientPlayerState() {
        AtomicLong clock = new AtomicLong();
        InboundPacketGuard guard = new InboundPacketGuard(clock::get);
        UUID playerId = UUID.randomUUID();

        assertTrue(guard.shouldWarn(playerId));
        assertFalse(guard.shouldWarn(playerId));
        clock.addAndGet(InboundPacketGuard.WARNING_INTERVAL_NANOS - 1);
        assertFalse(guard.shouldWarn(playerId));
        clock.incrementAndGet();
        assertTrue(guard.shouldWarn(playerId));

        guard.remove(playerId);
        assertTrue(guard.shouldWarn(playerId));
        guard.clear();
        assertTrue(guard.shouldWarn(playerId));
    }
}
