package com.dbclasshw.document;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public record UpdateDocumentRequest(@NotBlank @Size(max=255) String title,
                                    @NotNull JsonNode content,
                                    @NotNull @Min(1) @Max(2147483646) Integer version) { }
