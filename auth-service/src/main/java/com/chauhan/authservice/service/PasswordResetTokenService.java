package com.chauhan.authservice.service;

import com.chauhan.authservice.entity.PasswordResetToken;
import com.chauhan.authservice.entity.User;

public interface PasswordResetTokenService {
    
    /**
     * Creates a new password reset token for the given user, saving it to the database.
     * @param user The user to associate the token with.
     * @return The created PasswordResetToken entity.
     */
    PasswordResetToken createTokenForUser(User user);
    
    /**
     * Validates a token string. Checks if it exists and is not expired.
     * @param tokenString The token value to validate.
     * @return The PasswordResetToken if valid.
     * @throws IllegalArgumentException if the token is invalid or expired.
     */
    PasswordResetToken validateToken(String tokenString);
    
    /**
     * Deletes the given token.
     * @param token The token entity to delete.
     */
    void deleteToken(PasswordResetToken token);

    /**
     * Deletes any existing token associated with the given user.
     * @param user The user whose token should be deleted.
     */
    void deleteTokenByUser(User user);
}
