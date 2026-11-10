package com.orbit.research.analysis;

import com.orbit.research.auth.Accounts;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class ResearchAnalysisController {
    private final Accounts accounts;private final ResearchAnalyses analyses;private final ResearchNotes notes;
    public ResearchAnalysisController(Accounts accounts,ResearchAnalyses analyses,ResearchNotes notes){this.accounts=accounts;this.analyses=analyses;this.notes=notes;}
    @PostMapping("/conversations/{id}/analyses") @ResponseStatus(HttpStatus.ACCEPTED)
    public ResearchAnalyses.View create(Authentication a,@PathVariable String id,@RequestHeader(name="Idempotency-Key",required=false)String key,@Valid @RequestBody ResearchAnalyses.Input body){return analyses.create(accounts.owner(a),id,key,body);}
    @GetMapping("/conversations/{id}/analyses") public Map<String,Object> list(Authentication a,@PathVariable String id){return Map.of("items",analyses.list(accounts.owner(a),id));}
    @GetMapping("/analyses/{id}") public ResearchAnalyses.View get(Authentication a,@PathVariable String id){return analyses.get(accounts.owner(a),id);}
    @PostMapping("/analyses/{id}/cancel") public ResearchAnalyses.View cancel(Authentication a,@PathVariable String id){return analyses.cancel(accounts.owner(a),id);}
    @PostMapping("/conversations/{id}/notes") @ResponseStatus(HttpStatus.CREATED)
    public ResearchNotes.Note note(Authentication a,@PathVariable String id,@RequestHeader(name="Idempotency-Key",required=false)String key,@Valid @RequestBody ResearchNotes.Input body){return notes.create(accounts.owner(a),id,key,body);}
    @GetMapping("/notes") public Map<String,Object> notes(Authentication a,@RequestParam(required=false)String conversationId){return Map.of("items",notes.list(accounts.owner(a),conversationId));}
    @PatchMapping("/notes/{id}") public ResearchNotes.Note edit(Authentication a,@PathVariable String id,@Valid @RequestBody ResearchNotes.Edit body){return notes.edit(accounts.owner(a),id,body);}
}
