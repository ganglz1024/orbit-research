package com.orbit.research.conversation;

import com.orbit.research.auth.Accounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final Accounts accounts;
    private final Conversations conversations;
    private final Compaction compaction;
    private final com.orbit.research.run.Runs runs;
    public ConversationController(Accounts accounts, Conversations conversations, Compaction compaction, com.orbit.research.run.Runs runs) { this.runs=runs; this.accounts = accounts; this.conversations = conversations; this.compaction=compaction; }
    public record Title(@NotBlank @Size(max=180) String title, @Size(max=100) String workspaceId, Integer revision, Boolean automatic) {}
    public record Action(@NotBlank String action, Integer revision) {}
    public record Workspace(@NotBlank @Size(max=100) String workspaceId) {}
    @PostMapping("/{id}/manage") public Conversations.Conversation manage(Authentication auth,@PathVariable String id,@Valid @RequestBody Action body){String owner=accounts.owner(auth);return conversations.manage(owner,id,body.action(),body.revision(),run->runs.cancel(owner,run));}
    @PatchMapping("/{id}/workspace") public Conversations.Conversation workspace(Authentication auth,@PathVariable String id,@Valid @RequestBody Workspace body){return conversations.associate(accounts.owner(auth),id,body.workspaceId());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Conversations.Conversation create(Authentication auth, @Valid @RequestBody Title body) { return conversations.create(accounts.owner(auth), body.title(),body.workspaceId()); }
    @GetMapping public Conversations.Page<Conversations.Conversation> list(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String q,@RequestParam(required=false) String workspaceId,@RequestParam(defaultValue="active") String state) { return conversations.list(accounts.owner(auth),page,size,q,workspaceId,state); }
    @GetMapping("/{id}") public Map<String, Object> get(Authentication auth, @PathVariable String id) { String owner = accounts.owner(auth); return Map.of("conversation", conversations.get(owner, id), "messages", conversations.messages(owner, id)); }
    @PatchMapping("/{id}") public Conversations.Conversation rename(Authentication auth, @PathVariable String id, @Valid @RequestBody Title body) { return conversations.rename(accounts.owner(auth), id, body.title(),body.revision(),Boolean.TRUE.equals(body.automatic())); }
    @PostMapping("/{id}/compact/preview") public Compaction.Checkpoint preview(Authentication auth,@PathVariable String id){return compaction.preview(accounts.owner(auth),id);}
    @PostMapping("/{id}/compact/{checkpoint}/apply") public Compaction.Checkpoint apply(Authentication auth,@PathVariable String id,@PathVariable String checkpoint){return compaction.apply(accounts.owner(auth),id,checkpoint);}
    @GetMapping("/{id}/compact") public Map<String,Object> checkpoint(Authentication auth,@PathVariable String id){return Map.of("checkpoint",compaction.active(accounts.owner(auth),id).map(c->(Object)c).orElse(""));}
}
