package com.dbclasshw.document;

import java.time.LocalDateTime;

/** Safe sharing response, never contains credential fields. */
public class Permission {
    public Long id;
    public Long userId;
    public String username;
    public String email;
    public String role;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
