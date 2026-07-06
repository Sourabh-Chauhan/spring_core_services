package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void blacklistToken(String token, long ttlSeconds) {
        if (ttlSeconds > 0) {
            String key = BLACKLIST_KEY_PREFIX + token;
//            redisTemplate.opsForValue().set(key, "true", ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(key, "true", Expiration.from(ttlSeconds, TimeUnit.SECONDS));

        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
