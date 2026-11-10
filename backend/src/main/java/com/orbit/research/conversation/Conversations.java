package com.orbit.research.conversation;

import com.orbit.research.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class Conversations {
    public record Conversation(String id, String title, String createdAt, String updatedAt, String workspaceId, String deletedAt, String archivedAt, String pinnedAt, int revision, boolean titleManual) {}
    public record Message(String id, String role, String content, String runId, String createdAt, Object execution) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    private final JdbcTemplate jdbc;
    private final com.orbit.research.common.Json json;
    public Conversations(JdbcTemplate jdbc, com.orbit.research.common.Json json) { this.jdbc = jdbc; this.json=json; }
    private Conversation map(java.sql.ResultSet r,int row) throws java.sql.SQLException {
        return new Conversation(r.getString("id"),r.getString("title"),r.getString("created_at"),r.getString("updated_at"),r.getString("workspace_id"),r.getString("deleted_at"),r.getString("archived_at"),r.getString("pinned_at"),r.getInt("revision"),r.getBoolean("title_manual"));
    }
    public Conversation create(String owner,String title){return create(owner,title,null);}
    public Conversation create(String owner,String title,String workspace){
        String id=UUID.randomUUID().toString(),now=Instant.now().toString();
        jdbc.update("INSERT INTO conversation(id,owner_id,title,created_at,updated_at,workspace_id) VALUES(?,?,?,?,?,?)",id,owner,title.strip(),now,now,workspace);
        return get(owner,id);
    }
    private Conversation any(String owner,String id){return jdbc.query("SELECT * FROM conversation WHERE id=? AND owner_id=?",this::map,id,owner).stream().findFirst().orElseThrow(ApiException::notFound);}
    public Conversation get(String owner,String id){var c=any(owner,id);if(c.deletedAt()!=null)throw ApiException.notFound();return c;}
    public void writable(String owner,String id){if(get(owner,id).archivedAt()!=null)throw new ApiException(409,"CONVERSATION_ARCHIVED","请先取消归档，再继续对话。");}
    public Page<Conversation> list(String owner,int page,int size){return list(owner,page,size,"",null,"active");}
    public Page<Conversation> list(String owner,int page,int size,String q,String workspace,String state){
        if(page<0||size<1||size>100||page>100000||q.length()>200||!java.util.Set.of("active","archived","deleted").contains(state))throw new IllegalArgumentException();
        String where=" WHERE c.owner_id=?";var args=new java.util.ArrayList<Object>();args.add(owner);
        where+=state.equals("deleted")?" AND c.deleted_at IS NOT NULL":" AND c.deleted_at IS NULL AND c.archived_at IS "+(state.equals("archived")?"NOT NULL":"NULL");
        if(workspace!=null&&!workspace.isBlank()){where+=" AND c.workspace_id=?";args.add(workspace);}
        if(!q.isBlank()){
            String like="%"+q.toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            where+=" AND (LOWER(c.title) LIKE ? ESCAPE '!' OR EXISTS (SELECT 1 FROM chat_message m WHERE m.conversation_id=c.id AND LOWER(m.content) LIKE ? ESCAPE '!'))";args.add(like);args.add(like);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM conversation c"+where,Long.class,args.toArray());
        args.add(size);args.add(page*size);
        var items=jdbc.query("SELECT c.* FROM conversation c"+where+" ORDER BY CASE WHEN c.pinned_at IS NULL THEN 1 ELSE 0 END,c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?",this::map,args.toArray());
        return new Page<>(items,total,page,size);
    }
    public Conversation rename(String owner,String id,String title){return rename(owner,id,title,null,false);}
    @org.springframework.transaction.annotation.Transactional
    public Conversation rename(String owner,String id,String title,Integer revision,boolean automatic){
        lock(owner);var c=get(owner,id);check(c,revision);
        if(automatic&&(c.titleManual()||!c.title().equals("我的科研对话")))return c;
        if(title.isBlank()||title.length()>180)throw new IllegalArgumentException();
        jdbc.update("UPDATE conversation SET title=?,title_manual=?,updated_at=?,revision=revision+1 WHERE id=? AND owner_id=?",title.strip(),!automatic,Instant.now().toString(),id,owner);return get(owner,id);
    }
    private void lock(String owner){jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE",String.class,owner);}
    private void check(Conversation c,Integer revision){if(revision!=null&&revision!=c.revision())throw new ApiException(409,"CONVERSATION_CONFLICT","对话已被修改，请刷新后重试。");}
    @org.springframework.transaction.annotation.Transactional
    public Conversation manage(String owner,String id,String action,Integer revision,java.util.function.Consumer<String> cancel){
        lock(owner);var c=any(owner,id);check(c,revision);
        if(!java.util.Set.of("delete","restore","archive","unarchive","pin","unpin").contains(action))throw new IllegalArgumentException();
        if(c.deletedAt()!=null&&!java.util.Set.of("restore","delete").contains(action))throw ApiException.notFound();
        if(action.equals("delete")&&c.deletedAt()!=null||action.equals("restore")&&c.deletedAt()==null||action.equals("archive")&&c.archivedAt()!=null||action.equals("unarchive")&&c.archivedAt()==null||action.equals("pin")&&c.pinnedAt()!=null||action.equals("unpin")&&c.pinnedAt()==null)return c;
        if(action.equals("delete")||action.equals("archive"))for(String run:jdbc.queryForList("SELECT id FROM run_record WHERE conversation_id=? AND status IN ('queued','running')",String.class,id))cancel.accept(run);
        String column=switch(action){case "delete","restore"->"deleted_at";case "archive","unarchive"->"archived_at";default->"pinned_at";};
        boolean clear=java.util.Set.of("restore","unarchive","unpin").contains(action);
        jdbc.update("UPDATE conversation SET "+column+"=?,revision=revision+1 WHERE id=? AND owner_id=?",clear?null:Instant.now().toString(),id,owner);
        return any(owner,id);
    }
    // Legacy browser associations may only fill an unknown workspace, never reassign one.
    @org.springframework.transaction.annotation.Transactional
    public Conversation associate(String owner,String id,String workspace){lock(owner);var c=get(owner,id);if(c.workspaceId()!=null&&!c.workspaceId().equals(workspace))throw new ApiException(409,"WORKSPACE_CONFLICT","此对话已属于其他研究空间。");jdbc.update("UPDATE conversation SET workspace_id=? WHERE id=? AND owner_id=? AND workspace_id IS NULL",workspace,id,owner);return get(owner,id);}
    public List<Message> messages(String owner, String id) {
        get(owner, id);
        return jdbc.query("SELECT m.*,r.skill_snapshot,r.model_snapshot, rr.snapshot AS reading_snapshot FROM chat_message m LEFT JOIN run_record r ON r.id=m.run_id LEFT JOIN reading_request_snapshot rr ON rr.run_id=m.run_id WHERE m.conversation_id=? ORDER BY m.created_at,m.id", (rs, row) ->
            new Message(rs.getString("id"), rs.getString("role"), rs.getString("content"), rs.getString("run_id"), rs.getString("created_at"), java.util.Map.of("reading",json.read(rs.getString("reading_snapshot")==null?"{}":rs.getString("reading_snapshot")),"skill",json.read(rs.getString("skill_snapshot")==null?"{}":rs.getString("skill_snapshot")),"model",json.read(rs.getString("model_snapshot")==null?"{}":rs.getString("model_snapshot")))), id);
    }
}
