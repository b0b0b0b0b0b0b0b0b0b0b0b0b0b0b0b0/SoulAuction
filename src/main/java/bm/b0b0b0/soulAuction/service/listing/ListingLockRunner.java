package bm.b0b0b0.soulAuction.service.listing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ListingLockRunner {

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public <T> T withLock(long listingId, Supplier<T> action) {
        Object lock = locks.computeIfAbsent(listingId, ignored -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }

    public void withLock(long listingId, Runnable action) {
        withLock(listingId, () -> {
            action.run();
            return null;
        });
    }
}
