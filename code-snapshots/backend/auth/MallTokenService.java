package com.caifan.admin.mall;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小程序与 Android 登录令牌：默认存 Redis（7 天），Redis 不可用时退化为进程内缓存。
 */
@Service
@RequiredArgsConstructor
public class MallTokenService {

    private static final String PREFIX = "mall:token:";
    private static final String ACTIVE_PREFIX = "mall:active:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, LocalToken> localTokenFallback = new ConcurrentHashMap<>();
    private final Map<String, LocalActive> localActiveFallback = new ConcurrentHashMap<>();

    public String issue(MallCustomer customer) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(TTL);
        try {
            redisTemplate.opsForValue().set(PREFIX + token, objectMapper.writeValueAsString(customer), TTL);
        } catch (Exception ignored) {
            // Redis 不可用时走进程内缓存
        }
        localTokenFallback.put(token, new LocalToken(customer, expiresAt));
        setActiveMarker(customer, "token:" + token, expiresAt);
        return token;
    }

    public MallCustomer resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(PREFIX + token);
            if (json != null) {
                MallCustomer customer = objectMapper.readValue(json, MallCustomer.class);
                return isActive(customer, "token:" + token) ? customer : null;
            }
            return null;
        } catch (Exception ignored) {
            LocalToken localToken = localTokenFallback.get(token);
            if (localToken == null || localToken.expiresAt().isBefore(Instant.now())) {
                localTokenFallback.remove(token);
                return null;
            }
            return isActive(localToken.customer(), "token:" + token) ? localToken.customer() : null;
        }
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        LocalToken localToken = localTokenFallback.get(token);
        MallCustomer customer = localToken == null ? null : localToken.customer();
        if (customer == null) {
            try {
                String json = redisTemplate.opsForValue().get(PREFIX + token);
                customer = json == null ? null : objectMapper.readValue(json, MallCustomer.class);
            } catch (Exception ignored) {
                // 无可用登录态
            }
        }
        try {
            redisTemplate.delete(PREFIX + token);
        } catch (Exception ignored) {
            // 忽略 Redis 异常
        }
        localTokenFallback.remove(token);
        if (customer != null) {
            clearActiveIfMatches(customer, "token:" + token);
        }
    }

    private void setActiveMarker(MallCustomer customer, String marker, Instant expiresAt) {
        String key = activeKey(customer);
        try {
            redisTemplate.opsForValue().set(key, marker, TTL);
        } catch (Exception ignored) {
            // Redis 不可用时走进程内缓存
        }
        localActiveFallback.put(key, new LocalActive(marker, expiresAt));
    }

    private boolean isActive(MallCustomer customer, String marker) {
        String active = getActiveMarker(customer);
        return marker.equals(active);
    }

    private String getActiveMarker(MallCustomer customer) {
        String key = activeKey(customer);
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ignored) {
            LocalActive localActive = localActiveFallback.get(key);
            if (localActive == null || localActive.expiresAt().isBefore(Instant.now())) {
                localActiveFallback.remove(key);
                return null;
            }
            return localActive.marker();
        }
    }

    private void clearActiveIfMatches(MallCustomer customer, String marker) {
        String key = activeKey(customer);
        if (!marker.equals(getActiveMarker(customer))) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
            // 忽略 Redis 异常
        }
        localActiveFallback.remove(key);
    }

    private String activeKey(MallCustomer customer) {
        return ACTIVE_PREFIX + customer.getMerchantId() + ":" + customer.getCustomerId();
    }

    private record LocalToken(MallCustomer customer, Instant expiresAt) {
    }

    private record LocalActive(String marker, Instant expiresAt) {
    }
}
