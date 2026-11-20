package com.dbclasshw.document;

import jakarta.validation.constraints.*;

public final class SharingRequests {
    private SharingRequests() { }
    public record Add(@NotBlank @Size(max=254) String login,
                      @NotNull @Pattern(regexp="EDITOR|VIEWER") String role) { }
    public record Change(@NotNull @Pattern(regexp="EDITOR|VIEWER") String role) { }
}
