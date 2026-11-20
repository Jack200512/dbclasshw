package com.dbclasshw.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/documents/{id}/versions")
public class DocumentVersionController {
    public record RestoreRequest(@NotNull @Min(1) @Max(2147483646) Integer version) { }
    private final DocumentService documents;
    public DocumentVersionController(DocumentService documents) { this.documents=documents; }

    @GetMapping
    public List<DocumentVersion> list(@PathVariable long id,Authentication auth) {
        return documents.versions(id,Long.parseLong(auth.getName()));
    }

    @GetMapping("/{version}")
    public DocumentVersion find(@PathVariable long id,@PathVariable int version,Authentication auth) {
        return documents.version(id,Long.parseLong(auth.getName()),version);
    }

    @PostMapping("/{version}/restore")
    public Document restore(@PathVariable long id,@PathVariable int version,Authentication auth,
                            @Valid @RequestBody RestoreRequest request) {
        return documents.restore(id,Long.parseLong(auth.getName()),version,request.version());
    }
}
