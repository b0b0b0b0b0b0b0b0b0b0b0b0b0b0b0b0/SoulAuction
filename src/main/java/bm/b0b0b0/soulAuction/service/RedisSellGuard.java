package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings.RedisSettings;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

public final class RedisSellGuard {

    private final Plugin plugin;
    private final JedisPool jedisPool;
    private final long sellLockMillis;
    private final boolean pubSubEnabled;
    private final String pubSubChannel;
    private final LocalAuctionLocks localLocks;
    private Thread subscriberThread;
    private JedisPubSub pubSub;

    public RedisSellGuard(Plugin plugin, boolean enabled, RedisSettings settings) {
        this.plugin = plugin;
        this.sellLockMillis = settings.sellLockMillis;
        this.localLocks = new LocalAuctionLocks(sellLockMillis);
        this.pubSubEnabled = settings.pubSubEnabled;
        this.pubSubChannel = settings.pubSubChannel == null || settings.pubSubChannel.isBlank()
                ? "soulauction:cache"
                : settings.pubSubChannel;
        if (!enabled || !settings.enabled) {
            this.jedisPool = null;
            return;
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        if (settings.password == null || settings.password.isBlank()) {
            this.jedisPool = new JedisPool(poolConfig, settings.host, settings.port, settings.timeoutMs, null, settings.database);
        } else {
            this.jedisPool = new JedisPool(poolConfig, settings.host, settings.port, settings.timeoutMs, settings.password, settings.database);
        }
    }

    public boolean enabled() {
        return jedisPool != null;
    }

    public boolean distributedLocksRequired() {
        return jedisPool != null;
    }

    public boolean tryAcquireSellLock(UUID playerId) {
        if (!localLocks.tryAcquireSellLock(playerId)) {
            return false;
        }
        if (distributedLocksRequired() && !tryAcquireDistributed("soulauction:sell-lock:" + playerId)) {
            localLocks.releaseSellLock(playerId);
            return false;
        }
        return true;
    }

    @Deprecated
    public boolean tryAcquireListingLock(long listingId) {
        return tryAcquireListingLockLocal(listingId);
    }

    public boolean tryAcquireListingLockLocal(long listingId) {
        return localLocks.tryAcquireListingLock(listingId);
    }

    public boolean tryAcquireListingLockDistributed(long listingId) {
        return tryAcquireDistributed("soulauction:buy-lock:" + listingId);
    }

    public void releaseListingLock(long listingId) {
        localLocks.releaseListingLock(listingId);
        releaseDistributed("soulauction:buy-lock:" + listingId);
    }

    public void releaseListingLockLocal(long listingId) {
        localLocks.releaseListingLock(listingId);
    }

    public void publishCacheInvalidate(String auctionId) {
        if (plugin == null || jedisPool == null || !pubSubEnabled) {
            return;
        }
        String payload = auctionId == null || auctionId.isBlank() ? "*" : auctionId.toLowerCase();
        PluginSchedulers.runAsync(plugin, () -> publishRaw(payload));
    }

    public void publishListingChange(String action, bm.b0b0b0.soulAuction.model.AuctionListing listing, com.google.gson.Gson gson) {
        if (plugin == null || jedisPool == null || !pubSubEnabled || listing == null || gson == null) {
            return;
        }
        String payload;
        if ("REMOVE".equalsIgnoreCase(action)) {
            payload = "L-" + listing.listingId() + "|" + listing.auctionId().toLowerCase(java.util.Locale.ROOT);
        } else {
            payload = "L+" + gson.toJson(listing);
        }
        String finalPayload = payload;
        PluginSchedulers.runAsync(plugin, () -> publishRaw(finalPayload));
    }

    public void startCacheSubscriber(Consumer<String> onInvalidate) {
        if (jedisPool == null || !pubSubEnabled || onInvalidate == null) {
            return;
        }
        stopCacheSubscriber();
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (!pubSubChannel.equals(channel)) {
                    return;
                }
                onInvalidate.accept(message);
            }
        };
        subscriberThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(pubSub, pubSubChannel);
                } catch (Exception exception) {
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "SoulAuction-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void stopCacheSubscriber() {
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
            pubSub = null;
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
            subscriberThread = null;
        }
    }

    public void close() {
        stopCacheSubscriber();
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    boolean tryAcquireDistributed(String key) {
        if (jedisPool == null) {
            return true;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            SetParams params = SetParams.setParams().nx().px(sellLockMillis);
            String result = jedis.set(key, "1", params);
            return "OK".equalsIgnoreCase(result);
        } catch (Exception exception) {
            return false;
        }
    }

    void releaseDistributed(String key) {
        if (jedisPool == null) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        } catch (Exception ignored) {
        }
    }

    private void publishRaw(String payload) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(pubSubChannel, payload);
        } catch (Exception ignored) {
        }
    }
}
