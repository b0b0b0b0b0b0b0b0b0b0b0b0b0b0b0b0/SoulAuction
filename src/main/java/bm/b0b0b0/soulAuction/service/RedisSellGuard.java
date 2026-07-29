package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings.RedisSettings;
import java.util.UUID;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

public final class RedisSellGuard {

    private final JedisPool jedisPool;
    private final long sellLockMillis;

    public RedisSellGuard(boolean enabled, RedisSettings settings) {
        this.sellLockMillis = settings.sellLockMillis;
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

    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
