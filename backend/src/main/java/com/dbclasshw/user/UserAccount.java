package com.dbclasshw.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/** Internal MyBatis row. HTTP endpoints return UserProfile, never this row. */
public class UserAccount {
    public Long id;
    public String username;
    public String email;
    @JsonIgnore public String passwordHash;
    public boolean enabled;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
