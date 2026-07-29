package bm.b0b0b0.soulAuction.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process locks for sell/buy hot paths. No I/O — safe on Bukkit main / region thread.
 */
public final class LocalAuctionLocks {

    private final ConcurrentHashMap<UUID, Long> sellUntilEpochMillis = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> listingUntilEpochMillis = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public LocalAuctionLocks(long ttlMillis) {
        this.ttlMillis = Math.max(100L, ttlMillis);
    }

    public boolean tryAcquireSellLock(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        purgeExpired(sellUntilEpochMillis, now);
        Long until = sellUntilEpochMillis.get(playerId);
        if (until != null && until > now) {
            return false;
        }
        sellUntilEpochMillis.put(playerId, now + ttlMillis);
        return true;
    }

    public boolean tryAcquireListingLock(long listingId) {
        long now = System.currentTimeMillis();
        purgeExpired(listingUntilEpochMillis, now);
        Long until = listingUntilEpochMillis.get(listingId);
        if (until != null && until > now) {
            return false;
        }
        listingUntilEpochMillis.put(listingId, now + ttlMillis);
        return true;
    }

    public void releaseListingLock(long listingId) {
        listingUntilEpochMillis.remove(listingId);
    }

    public void releaseSellLock(UUID playerId) {
        sellUntilEpochMillis.remove(playerId);
    }

    private static <K> void purgeExpired(ConcurrentHashMap<K, Long> map, long now) {
        if (map.size() < 256) {
            return;
        }
        map.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
