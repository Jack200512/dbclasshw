package com.dbclasshw.document;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.List;

@RestController
@RequestMapping("/api/documents/{id}/permissions")
public class PermissionController {
    private final PermissionService service;
    public PermissionController(PermissionService service) { this.service=service; }

    @GetMapping
    public List<Permission> list(@PathVariable long id,Authentication auth) {
        return service.list(id,Long.parseLong(auth.getName()));
    }
    @PostMapping
    public ResponseEntity<List<Permission>> add(@PathVariable long id,Authentication auth,
                                               @Valid @RequestBody SharingRequests.Add body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(id,Long.parseLong(auth.getName()),body));
    }
    @PutMapping("/{userId}")
    public List<Permission> change(@PathVariable long id,@PathVariable long userId,Authentication auth,
                                   @Valid @RequestBody SharingRequests.Change body) {
        return service.change(id,Long.parseLong(auth.getName()),userId,body);
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable long id,@PathVariable long userId,Authentication auth) {
        service.revoke(id,Long.parseLong(auth.getName()),userId);
        return ResponseEntity.noContent().build();
    }
}
