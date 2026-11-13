package com.dbclasshw.user;

import jakarta.validation.constraints.*;

public final class UserRequests {
    private UserRequests() { }

    public record Register(
            @NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{2,31}") String username,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=8,max=64) String password) { }

    public record Login(@NotBlank @Size(max=254) String login,
                        @NotBlank @Size(max=64) String password) { }

    public record Update(@NotBlank @Email @Size(max=254) String email) { }
}
