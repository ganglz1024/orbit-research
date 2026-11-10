package com.orbit.research.conversation;

import com.orbit.research.auth.Accounts;
import com.orbit.research.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Associates existing conversations with immutable browser document identities; does not upload or authorize a PDF. */
@RestController
@RequestMapping("/api/v1/reading-conversations")
public class ReadingConversations {
 private final JdbcTemplate jdbc; private final Conversations conversations; private final Accounts accounts;
 public ReadingConversations(JdbcTemplate jdbc,Conversations conversations,Accounts accounts){this.jdbc=jdbc;this.conversations=conversations;this.accounts=accounts;}
 public record Binding(@Pattern(regexp="pdf-[a-f0-9]{24}") @NotNull String documentId,@Pattern(regexp="[a-f0-9]{64}") @NotNull String documentVersion,@Min(1) @Max(100000) int initialPage){}
 @PutMapping("/{id}") @Transactional
 public Binding bind(Authentication auth,@PathVariable String id,@Valid @RequestBody Binding binding){String owner=accounts.owner(auth);conversations.writable(owner,id);jdbc.queryForObject("SELECT id FROM conversation WHERE id=? AND owner_id=? FOR UPDATE",String.class,id,owner);
  if(!binding.documentId().equals("pdf-"+binding.documentVersion().substring(0,24)))throw new ApiException(409,"DOCUMENT_VERSION_CONFLICT","文件标识与版本不匹配。");
  var old=jdbc.query("SELECT * FROM reading_conversation WHERE conversation_id=?",(r,n)->new Binding(r.getString("document_id"),r.getString("document_version"),r.getInt("initial_page")),id);
  if(!old.isEmpty()){if(!old.get(0).documentId().equals(binding.documentId())||!old.get(0).documentVersion().equals(binding.documentVersion()))throw new ApiException(409,"READING_SCOPE_CONFLICT","阅读会话不能改绑到其他文档版本。");return old.get(0);}
  if(!conversations.messages(owner,id).isEmpty())throw new ApiException(409,"READING_CONTEXT_NOT_EMPTY","只能为新建的空白会话绑定论文，避免沿用其他对话资料。");
  jdbc.update("INSERT INTO reading_conversation VALUES(?,?,?,?)",id,binding.documentId(),binding.documentVersion(),binding.initialPage());return binding;
 }
 @GetMapping public Map<String,Object> list(Authentication auth,@RequestParam String workspaceId,@RequestParam String documentId,@RequestParam String documentVersion,@RequestParam(defaultValue="active") String state){
  if(workspaceId.length()>100||documentId.length()>100||documentVersion.length()!=64||!Set.of("active","archived").contains(state))throw new IllegalArgumentException();
  String archived=state.equals("archived")?"IS NOT NULL":"IS NULL";
  var ids=jdbc.queryForList("SELECT c.id FROM conversation c JOIN reading_conversation r ON r.conversation_id=c.id WHERE c.owner_id=? AND c.workspace_id=? AND r.document_id=? AND r.document_version=? AND c.deleted_at IS NULL AND c.archived_at "+archived+" ORDER BY c.updated_at DESC,c.id",String.class,accounts.owner(auth),workspaceId,documentId,documentVersion);
  return Map.of("items",ids.stream().map(id->conversations.get(accounts.owner(auth),id)).toList());
 }

 // A short scholarly selection may contain hundreds of font spans, each with its own quad.
 private static final int MAX_READING_RECTS=8192;
 public record Evidence(String chunkId,int pageIndex,String quote,String scope,List<List<Double>> rects){}
 public record ReadingInput(String documentId,String documentVersion,List<Evidence> evidence){}
 public void validate(String owner,String conversation,ReadingInput input){
  conversations.get(owner,conversation);
  var bindings=jdbc.query("SELECT * FROM reading_conversation WHERE conversation_id=?",(r,n)->new Binding(r.getString("document_id"),r.getString("document_version"),r.getInt("initial_page")),conversation);
  if(bindings.isEmpty()||!Objects.equals(bindings.get(0).documentId(),input.documentId())||!Objects.equals(bindings.get(0).documentVersion(),input.documentVersion()))throw new ApiException(409,"READING_SCOPE_CONFLICT","请求不属于当前阅读会话的文档版本。");
  if(input.evidence()==null||input.evidence().size()>6)throw new IllegalArgumentException();
  int total=0,rectCount=0;for(var e:input.evidence()){
   if(e==null||e.quote()==null||e.quote().isBlank()||e.quote().length()>4000||e.pageIndex()<0||e.pageIndex()>100000||e.chunkId()==null||e.chunkId().length()>120||e.scope()==null||!Set.of("selection","nearby","authorized-block").contains(e.scope())||e.rects()==null)throw new IllegalArgumentException();
   rectCount+=e.rects().size();if(rectCount>MAX_READING_RECTS)throw new ApiException(422,"READING_ANCHOR_LIMIT","选区的位置标记过多，请分段引用；原文和草稿已保留。");
   for(var r:e.rects())if(r==null||r.size()!=8||r.stream().anyMatch(v->v==null||!Double.isFinite(v)))throw new IllegalArgumentException();total+=e.quote().length();
  }if(total>6000)throw new ApiException(422,"READING_BUDGET","阅读证据超过本轮预算，请减少引用。");
 }
}
