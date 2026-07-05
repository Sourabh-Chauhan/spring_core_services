package com.chauhan.authservice.service;

import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;

public interface VerificationTokenService {
    
    /**
     * Creates a new verification token for the given user, saving it to the database.
     * @param user The user to associate the token with.
     * @return The created VerificationToken entity.
     */
    VerificationToken createTokenForUser(User user);
    
    /**
     * Validates a token string. Checks if it exists and is not expired.
     * @param tokenString The token value to validate.
     * @return The VerificationToken if valid.
     * @throws IllegalArgumentException if the token is invalid or expired.
     */
    VerificationToken validateToken(String tokenString);
    
    /**
     * Deletes the given token.
     * @param token The token entity to delete.
     */
    void deleteToken(VerificationToken token);

    /**
     * Deletes any existing token associated with the given user.
     * @param user The user whose token should be deleted.
     */
    void deleteTokenByUser(User user);
}
