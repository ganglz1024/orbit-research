package com.orbit.research.analysis;

import com.orbit.research.common.*;
import com.orbit.research.conversation.Conversations;
import com.orbit.research.document.Documents;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class ResearchNotes {
    public record Input(@Size(max=36) String analysisId,@NotBlank @Size(max=180) String title,@NotNull @Size(max=12000) String body,
                        @Valid @Size(max=5) List<ResearchAnalyses.Paper> papers) {public Input {papers=papers==null?List.of():papers;}}
    public record Edit(@NotBlank @Size(max=180) String title,@NotNull @Size(max=12000) String body,@Min(1) int revision) {}
    public record Note(String id,String conversationId,String analysisId,String title,String body,Object snapshot,int revision,String createdAt,String updatedAt) {}
    private final JdbcTemplate jdbc;private final Json json;private final Conversations conversations;private final ResearchAnalyses analyses;
    public ResearchNotes(JdbcTemplate jdbc,Json json,Conversations conversations,ResearchAnalyses analyses){this.jdbc=jdbc;this.json=json;this.conversations=conversations;this.analyses=analyses;}
    private Note map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Note(rs.getString("id"),rs.getString("conversation_id"),rs.getString("analysis_id"),rs.getString("title"),rs.getString("body"),json.read(rs.getString("snapshot")),rs.getInt("revision"),rs.getString("created_at"),rs.getString("updated_at"));}
    public Note get(String owner,String id){return jdbc.query("SELECT * FROM research_note WHERE id=? AND owner_id=?",this::map,id,owner).stream().findFirst().orElseThrow(ApiException::notFound);}
    public List<Note> list(String owner,String workspace){if(workspace!=null){conversations.get(owner,workspace);return jdbc.query("SELECT * FROM research_note WHERE owner_id=? AND conversation_id=? ORDER BY updated_at DESC,id DESC LIMIT 100",this::map,owner,workspace);}return jdbc.query("SELECT * FROM research_note WHERE owner_id=? ORDER BY updated_at DESC,id DESC LIMIT 100",this::map,owner);}
    @Transactional public Note create(String owner,String workspace,String key,Input input){
        conversations.get(owner,workspace);if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,100}"))throw new ApiException(400,"IDEMPOTENCY_REQUIRED","请提供笔记保存标识。");
        String hash=Documents.hash(json.write(List.of(workspace,input)).getBytes(StandardCharsets.UTF_8));
        jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE",String.class,owner);
        var existing=jdbc.queryForList("SELECT id,request_hash FROM research_note WHERE owner_id=? AND save_key=?",owner,key);
        if(!existing.isEmpty()){if(!hash.equals(existing.get(0).get("request_hash")))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","该保存标识已用于其他笔记。");return get(owner,(String)existing.get(0).get("id"));}
        Object snapshot;
        if(input.analysisId()!=null&&!input.analysisId().isBlank()){var analysis=analyses.get(owner,input.analysisId());if(!analysis.conversationId().equals(workspace))throw ApiException.notFound();if(analysis.result()==null)throw new ApiException(409,"ANALYSIS_NOT_READY","分析尚未完成，不能保存为成果。");snapshot=analysis;}
        else {if(input.papers().isEmpty()&&input.body().isBlank())throw new IllegalArgumentException();snapshot=Map.of("kind","user_note","sources",analyses.sources(owner,workspace,input.papers(),input.title()));}
        String id=UUID.randomUUID().toString(),now=Instant.now().toString();jdbc.update("INSERT INTO research_note VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,owner,workspace,key,hash,input.analysisId(),input.title(),input.body(),json.write(snapshot),1,now,now);return get(owner,id);
    }
    @Transactional public Note edit(String owner,String id,Edit input){get(owner,id);if(jdbc.update("UPDATE research_note SET title=?,body=?,revision=revision+1,updated_at=? WHERE id=? AND owner_id=? AND revision=?",input.title(),input.body(),Instant.now().toString(),id,owner,input.revision())==0)throw new ApiException(409,"NOTE_CONFLICT","笔记已更新，请重新加载。原始分析快照保持不变。");return get(owner,id);}
}
