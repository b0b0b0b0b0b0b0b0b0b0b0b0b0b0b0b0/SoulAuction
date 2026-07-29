package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings.RedisSettings;
import java.util.UUID;
import java.util.function.Consumer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

public final class RedisSellGuard {

    private final JedisPool jedisPool;
    private final long sellLockMillis;
    private final boolean pubSubEnabled;
    private final String pubSubChannel;
    private Thread subscriberThread;
    private JedisPubSub pubSub;

    public RedisSellGuard(boolean enabled, RedisSettings settings) {
        this.sellLockMillis = settings.sellLockMillis;
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

    public boolean tryAcquireSellLock(UUID playerId) {
        if (jedisPool == null) {
            return true;
        }
        String key = "soulauction:sell-lock:" + playerId;
        try (Jedis jedis = jedisPool.getResource()) {
            SetParams params = SetParams.setParams().nx().px(sellLockMillis);
            String result = jedis.set(key, "1", params);
            return "OK".equalsIgnoreCase(result);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean tryAcquireListingLock(long listingId) {
        if (jedisPool == null) {
            return true;
        }
        String key = "soulauction:buy-lock:" + listingId;
        try (Jedis jedis = jedisPool.getResource()) {
            SetParams params = SetParams.setParams().nx().px(sellLockMillis);
            String result = jedis.set(key, "1", params);
            return "OK".equalsIgnoreCase(result);
        } catch (Exception exception) {
            return false;
        }
    }

    public void publishCacheInvalidate(String auctionId) {
        if (jedisPool == null || !pubSubEnabled) {
            return;
        }
        String payload = auctionId == null || auctionId.isBlank() ? "*" : auctionId.toLowerCase();
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(pubSubChannel, payload);
        } catch (Exception ignored) {
            // best effort
        }
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
                // ignore
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
}
