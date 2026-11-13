package com.dbclasshw.document;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Document> create(@Valid @RequestBody CreateDocumentRequest request, Authentication auth) {
        Document document = service.create(request, Long.parseLong(auth.getName()));
        return ResponseEntity.created(URI.create("/api/documents/" + document.getId())).body(document);
    }

    @GetMapping
    public List<Document> findAll(Authentication auth) {
        return service.findAll(Long.parseLong(auth.getName()));
    }

    @GetMapping("/{id}")
    public Document findById(@PathVariable long id, Authentication auth) {
        return service.findById(id, Long.parseLong(auth.getName()));
    }
}
