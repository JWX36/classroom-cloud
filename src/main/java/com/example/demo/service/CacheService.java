package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量缓存：生产环境走 Redis（CACHE_MODE=redis），开发环境默认本地缓存。
 * 缓存的是作业列表 / 班级公告等读多写少的数据。
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redis;
    private final Map<String, Entry> local = new ConcurrentHashMap<>();

    @Value("${app.cache.mode:simple}")
    private String mode;

    public CacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Object get(String key) {
        if (isRedis()) {
            try {
                String value = redis.opsForValue().get(key);
                if (value != null) return deserialize(value);
            } catch (Exception e) {
                log.debug("Redis 读取失败，回退本地缓存：{}", e.getMessage());
            }
        }
        Entry entry = local.get(key);
        if (entry == null) return null;
        if (entry.expireAt > 0 && System.currentTimeMillis() > entry.expireAt) {
            local.remove(key);
            return null;
        }
        return entry.value;
    }

    public void put(String key, Object value, Duration ttl) {
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        local.put(key, new Entry(value, expireAt));
        if (isRedis()) {
            try {
                redis.opsForValue().set(key, serialize(value), ttl);
            } catch (Exception e) {
                log.debug("Redis 写入失败，仅使用本地缓存：{}", e.getMessage());
            }
        }
    }

    public void evict(String key) {
        local.remove(key);
        if (isRedis()) {
            try {
                redis.delete(key);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isRedis() {
        return "redis".equalsIgnoreCase(mode);
    }

    private String serialize(Object value) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private Object deserialize(String value) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(value);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    private record Entry(Object value, long expireAt) {
    }
}
