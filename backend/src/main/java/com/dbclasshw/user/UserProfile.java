package com.dbclasshw.user;

import java.time.LocalDateTime;

public record UserProfile(Long id, String username, String email, LocalDateTime createdAt) {
    static UserProfile from(UserAccount user) {
        return new UserProfile(user.id, user.username, user.email, user.createdAt);
    }
}
