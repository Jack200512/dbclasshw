package com.dbclasshw.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 255) String title,
        String content) {
}
