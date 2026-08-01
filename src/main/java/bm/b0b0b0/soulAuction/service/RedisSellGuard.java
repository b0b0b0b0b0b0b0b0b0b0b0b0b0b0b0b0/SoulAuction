package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings.RedisSettings;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

public final class RedisSellGuard {

    private final Plugin plugin;
    private final RedisClient jedis;
    private final HostAndPort redisAddress;
    private final JedisClientConfig redisClientConfig;
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
            this.jedis = null;
            this.redisAddress = null;
            this.redisClientConfig = null;
            return;
        }
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(settings.timeoutMs)
                .socketTimeoutMillis(settings.timeoutMs)
                .database(settings.database);
        if (settings.password != null && !settings.password.isBlank()) {
            clientConfigBuilder.password(settings.password);
        }
        this.redisClientConfig = clientConfigBuilder.build();
        this.redisAddress = new HostAndPort(settings.host, settings.port);
        this.jedis = RedisClient.builder()
                .hostAndPort(redisAddress)
                .clientConfig(redisClientConfig)
                .build();
    }

    public boolean enabled() {
        return jedis != null;
    }

    public boolean distributedLocksRequired() {
        return jedis != null;
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

    public void releaseSellLock(UUID playerId) {
        if (playerId == null) {
            return;
        }
        localLocks.releaseSellLock(playerId);
        releaseDistributed("soulauction:sell-lock:" + playerId);
    }

    public void publishCacheInvalidate(String auctionId) {
        if (plugin == null || jedis == null || !pubSubEnabled) {
            return;
        }
        String payload = auctionId == null || auctionId.isBlank() ? "*" : auctionId.toLowerCase();
        PluginSchedulers.runAsync(plugin, () -> publishRaw(payload));
    }

    public void publishListingChange(String action, bm.b0b0b0.soulAuction.model.AuctionListing listing, com.google.gson.Gson gson) {
        if (plugin == null || jedis == null || !pubSubEnabled || listing == null || gson == null) {
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
        if (jedis == null || !pubSubEnabled || onInvalidate == null) {
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
                try (Jedis subscriber = new Jedis(redisAddress, redisClientConfig)) {
                    subscriber.subscribe(pubSub, pubSubChannel);
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
        if (jedis != null) {
            jedis.close();
        }
    }

    boolean tryAcquireDistributed(String key) {
        if (jedis == null) {
            return true;
        }
        try {
            SetParams params = SetParams.setParams().nx().px(sellLockMillis);
            String result = jedis.set(key, "1", params);
            return "OK".equalsIgnoreCase(result);
        } catch (Exception exception) {
            return false;
        }
    }

    void releaseDistributed(String key) {
        if (jedis == null) {
            return;
        }
        try {
            jedis.del(key);
        } catch (Exception ignored) {
        }
    }

    private void publishRaw(String payload) {
        try {
            jedis.publish(pubSubChannel, payload);
        } catch (Exception ignored) {
        }
    }
}
