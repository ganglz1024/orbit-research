package com.orbit.research.document;

import com.orbit.research.auth.Accounts;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    private final Accounts accounts; private final Documents documents;
    public DocumentController(Accounts accounts, Documents documents) { this.accounts = accounts; this.documents = documents; }
    @PostMapping(value="/documents", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(Authentication auth, @RequestParam String conversationId, @RequestPart("files") List<MultipartFile> files) {
        var results = documents.upload(accounts.owner(auth), conversationId, files);
        long failed = results.stream().filter(result -> result.code() != null).count();
        return ResponseEntity.status(failed == 0 ? 201 : failed == results.size() ? 422 : 207).body(Map.of("items", results));
    }
    @GetMapping("/documents/{id}") public Documents.Document get(Authentication auth, @PathVariable String id) { return documents.get(accounts.owner(auth), id); }
    @GetMapping("/conversations/{id}/documents") public Map<String, Object> list(Authentication auth, @PathVariable String id) { return Map.of("items", documents.list(accounts.owner(auth), id)); }
    @PostMapping("/conversations/{id}/documents/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void associate(Authentication auth, @PathVariable String id, @PathVariable String documentId) { documents.associate(accounts.owner(auth), id, documentId); }
    @GetMapping("/documents/{id}/versions/{versionId}/content")
    public ResponseEntity<Resource> content(Authentication auth, @PathVariable String id, @PathVariable String versionId) {
        String owner = accounts.owner(auth); var doc = documents.get(owner, id); var file = documents.content(owner, id, versionId);
        // ResourceHttpMessageConverter supplies Range/206 support without reading the whole PDF.
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(doc.name(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff").header("ETag", "\"" + doc.hash() + "\"").body(file);
    }
}
