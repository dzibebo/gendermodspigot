package dbrighthd.wildfiregendermodplugin.networking;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

public final class InboundPacketGuard {
    public static final int MAX_SYNC_BYTES = 1024;
    public static final int MAX_HELLO_BYTES = 5;

    static final int CAPACITY = 8;
    static final double REFILL_PER_SECOND = 8.0;
    static final long WARNING_INTERVAL_NANOS = Duration.ofSeconds(10).toNanos();

    private final ConcurrentMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public InboundPacketGuard() {
        this(System::nanoTime);
    }

    InboundPacketGuard(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public boolean tryAcquire(UUID playerId) {
        long now = nanoTime.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(playerId, ignored -> new Bucket(now));

        synchronized (bucket) {
            long elapsed = Math.max(0L, now - bucket.lastRefillNanos);
            bucket.tokens = Math.min(CAPACITY,
                    bucket.tokens + elapsed * REFILL_PER_SECOND / Duration.ofSeconds(1).toNanos());
            bucket.lastRefillNanos = now;

            if (bucket.tokens < 1.0) return false;
            bucket.tokens -= 1.0;
            return true;
        }
    }

    public boolean shouldWarn(UUID playerId) {
        long now = nanoTime.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(playerId, ignored -> new Bucket(now));

        synchronized (bucket) {
            if (bucket.warned && now - bucket.lastWarningNanos < WARNING_INTERVAL_NANOS) return false;
            bucket.warned = true;
            bucket.lastWarningNanos = now;
            return true;
        }
    }

    public void remove(UUID playerId) {
        buckets.remove(playerId);
    }

    public void clear() {
        buckets.clear();
    }

    private static final class Bucket {
        private double tokens = CAPACITY;
        private long lastRefillNanos;
        private long lastWarningNanos;
        private boolean warned;

        private Bucket(long now) {
            lastRefillNanos = now;
        }
    }
}
