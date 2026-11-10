package com.orbit.research.document;

import com.orbit.research.auth.Accounts;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AnnotationController {
    private final Accounts accounts; private final Annotations annotations;
    public AnnotationController(Accounts accounts, Annotations annotations) { this.accounts = accounts; this.annotations = annotations; }
    @GetMapping("/documents/{id}/annotations") public Object list(Authentication auth, @PathVariable String id) { return Map.of("items", annotations.list(accounts.owner(auth), id)); }
    @PostMapping("/documents/{id}/annotations") @ResponseStatus(HttpStatus.CREATED)
    public Object add(Authentication auth, @PathVariable String id, @Valid @RequestBody Annotations.Input body) { return annotations.add(accounts.owner(auth), id, body); }
    @PatchMapping("/annotations/{id}") public Object edit(Authentication auth, @PathVariable String id, @Valid @RequestBody Annotations.Edit body) { return annotations.edit(accounts.owner(auth), id, body); }
    @DeleteMapping("/annotations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable String id, @RequestParam int revision) { annotations.delete(accounts.owner(auth), id, revision); }
    @GetMapping("/documents/{id}/reading-position") public Object position(Authentication auth, @PathVariable String id) { return annotations.position(accounts.owner(auth), id); }
    @PutMapping("/documents/{id}/reading-position") public Object position(Authentication auth, @PathVariable String id, @Valid @RequestBody Annotations.Position body) { return annotations.position(accounts.owner(auth), id, body); }
}
