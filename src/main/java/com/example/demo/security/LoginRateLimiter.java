package com.example.demo.security;

import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流（内存版）。
 * 规则：同一账号 5 分钟内登录失败满 5 次，锁定 5 分钟。
 * 多实例部署时可替换为基于 Redis 的实现，接口保持不变。
 */
@Service
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MILLIS = 5 * 60 * 1000L;
    static final long LOCK_MILLIS = 5 * 60 * 1000L;
    /** 防止被随机用户名撑爆内存的上限，超过时清理过期项。 */
    private static final int MAX_ENTRIES = 5000;

    private final Map<String, Entry> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String code) {
        Entry entry = attempts.get(key(code));
        if (entry == null) return false;
        long now = System.currentTimeMillis();
        if (entry.lockedUntil > now) {
            return true;
        }
        if (entry.lockedUntil > 0) {
            // 曾经锁定、现已到期：清掉，重新计次
            attempts.remove(key(code), entry);
        }
        // lockedUntil == 0：尚未锁定，只做失败计数，不动条目
        return false;
    }

    public void onFailure(String code) {
        String k = key(code);
        if (k.isEmpty()) return;
        long now = System.currentTimeMillis();
        Entry entry = attempts.compute(k, (ignored, old) -> {
            if (old == null || now - old.windowStart > WINDOW_MILLIS) {
                return new Entry(now, 1, 0);
            }
            return new Entry(old.windowStart, old.failures + 1, old.lockedUntil);
        });
        if (entry.failures >= MAX_FAILURES) {
            attempts.put(k, new Entry(entry.windowStart, entry.failures, now + LOCK_MILLIS));
        }
        if (attempts.size() > MAX_ENTRIES) {
            purgeExpired(now);
        }
    }

    public void onSuccess(String code) {
        attempts.remove(key(code));
    }

    private void purgeExpired(long now) {
        Iterator<Map.Entry<String, Entry>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            if (now - entry.windowStart > WINDOW_MILLIS && entry.lockedUntil <= now) {
                it.remove();
            }
        }
    }

    private String key(String code) {
        return code == null ? "" : code.trim();
    }

    private record Entry(long windowStart, int failures, long lockedUntil) {
    }
}
