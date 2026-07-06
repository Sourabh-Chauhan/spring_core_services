package com.chauhan.authservice.service;

public interface TokenBlacklistService {
    /**
     * Adds the token to the blacklist with a specific time-to-live.
     *
     * @param token      The token to blacklist.
     * @param ttlSeconds The remaining lifetime of the token in seconds.
     */
    void blacklistToken(String token, long ttlSeconds);

    /**
     * Checks if a token is present in the blacklist.
     *
     * @param token The token to verify.
     * @return true if the token is blacklisted, false otherwise.
     */
    boolean isTokenBlacklisted(String token);
}
