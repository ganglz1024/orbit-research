package com.orbit.research.skill;

import com.orbit.research.common.ApiException;
import com.orbit.research.llm.ModelSettings;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class Skills {
    public record Skill(String id, String name, String version, boolean enabled, String status,
                        String kind, String description, String instructions, int revision, String updatedAt) {}
    public record Input(@NotBlank @Size(max=80) String name, @NotNull @Size(max=600) String description,
                        @NotBlank @Size(max=12000) String instructions, boolean enabled, @Min(0) int revision) {}
    public record Snapshot(String id, String name, String version, String instructions) {}
    private final JdbcTemplate jdbc; private final ModelSettings models;
    private static final List<String> IDS = List.of("paper_search", "paper_read", "paper_compare", "ppt_generate", "citation_graph", "paper_metadata", "paper_relations");
    private static final List<String> NAMES = List.of("文献检索", "论文解读", "论文对比", "生成汇报", "引用图谱", "整理论文卡片", "分析论文联系");
    public Skills(JdbcTemplate jdbc, ModelSettings models) { this.jdbc = jdbc; this.models = models; }
    private Skill custom(java.sql.ResultSet rs, int row, boolean configured) throws java.sql.SQLException {
        int version = rs.getInt("revision");
        return new Skill(rs.getString("id"), rs.getString("name"), String.valueOf(version), rs.getBoolean("enabled"), configured ? "ready" : "provider_required", "custom", rs.getString("description"), rs.getString("instructions"), version, rs.getString("updated_at"));
    }
    public boolean enabled(String owner, String id) {
        if (!IDS.contains(id) && !AcademicResearchSkillCatalog.contains(id)) return get(owner, id).enabled();
        return jdbc.query("SELECT enabled FROM skill_preference WHERE owner_id=? AND skill_id=?", (rs, row) -> rs.getBoolean(1), owner, id).stream().findFirst().orElse(true);
    }
    public List<Skill> list(String owner) {
        boolean configured = models.get(owner).configured();
        List<Skill> result = new ArrayList<>();
        for (int i=0; i<IDS.size(); i++) result.add(new Skill(IDS.get(i), NAMES.get(i), "1.0.0", enabled(owner, IDS.get(i)),
            isImplemented(IDS.get(i)) ? configured ? "ready" : "provider_required" : "not_implemented", "builtin", "", "", 1, ""));
        for (var item : AcademicResearchSkillCatalog.all()) result.add(new Skill(item.id(), item.name(), "1.0.0", enabled(owner, item.id()),
            configured ? "ready" : "provider_required", "builtin", item.description(), "", 1, ""));
        result.addAll(jdbc.query("SELECT * FROM custom_skill WHERE owner_id=? AND deleted=FALSE ORDER BY created_at,id", (rs, row) -> custom(rs, row, configured), owner));
        return result;
    }
    public Skill get(String owner, String id) { return list(owner).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(ApiException::notFound); }
    private boolean isImplemented(String id) { return Set.of("paper_read", "paper_compare", "paper_metadata", "paper_relations").contains(id); }
    public Snapshot snapshot(String owner, String id) {
        if (id.equals("chat")) return new Snapshot("chat", "研究对话", "1", "");
        Skill s = get(owner, id);
        if (!s.enabled()) throw new ApiException(409, "SKILL_DISABLED", "所选科研 Skill 已关闭。");
        if (s.status().equals("not_implemented")) throw new ApiException(422, "SKILL_NOT_IMPLEMENTED", "此能力尚未接通真实执行工具。");
        String instruction = s.kind().equals("custom") ? s.instructions() : AcademicResearchSkillCatalog.find(id).map(AcademicResearchSkillCatalog.Spec::instructions).orElseGet(() -> switch (id) {
            case "paper_read" -> "根据明确提供的 PDF 片段，按研究问题、方法、证据、局限组织解读，并标注文件 ID 和页序。";
            case "paper_metadata" -> "从本次明确提供的 PDF 页序片段整理题名、作者、年份与摘要，按请求的 JSON 格式输出。每个题录字段提供连续的逐字原文和真实页序；缺失时返回 null，不用模型记忆或文件名补齐。不把预印本年份当成已核验的正式发表年份。";
            case "paper_relations" -> "仅依据本次提供的论文题录和摘要推断主题、方法或背景联系，按请求的 JSON 格式输出并使用给定论文 ID。每条联系说明依据与不确定性；不得称为真实引用，资料不足时允许返回空连线。";
            default -> "按共同研究问题、方法差异、证据条件和局限比较所选论文，缺失信息写明待核验。";
        });
        return new Snapshot(s.id(), s.name(), s.version(), instruction);
    }
    @Transactional public Skill create(String owner, Input input) {
        jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE", String.class, owner);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM custom_skill WHERE owner_id=? AND deleted=FALSE", Integer.class, owner) >= 24) throw new ApiException(422, "SKILL_LIMIT", "最多保存 24 个自定义 Skill。");
        String id = "custom_" + UUID.randomUUID().toString().replace("-", ""), now = Instant.now().toString();
        jdbc.update("INSERT INTO custom_skill VALUES(?,?,?,?,?,?,?,FALSE,?,?)", id, owner, input.name().trim(), input.description().trim(), input.instructions().trim(), 1, input.enabled(), now, now);
        return get(owner, id);
    }
    @Transactional public Skill update(String owner, String id, Input input) {
        if (jdbc.update("UPDATE custom_skill SET name=?,description=?,instructions=?,enabled=?,revision=revision+1,updated_at=? WHERE id=? AND owner_id=? AND revision=? AND deleted=FALSE", input.name().trim(), input.description().trim(), input.instructions().trim(), input.enabled(), Instant.now().toString(), id, owner, input.revision()) == 0) {
            get(owner, id); throw new ApiException(409, "SKILL_CONFLICT", "Skill 已更新，请重新加载后编辑。");
        }
        return get(owner, id);
    }
    @Transactional public void delete(String owner, String id) {
        if (jdbc.update("UPDATE custom_skill SET deleted=TRUE,enabled=FALSE,revision=revision+1,updated_at=? WHERE id=? AND owner_id=? AND deleted=FALSE", Instant.now().toString(), id, owner) == 0) throw ApiException.notFound();
        jdbc.update("UPDATE research_preference SET default_skill_id='chat' WHERE owner_id=? AND default_skill_id=?", owner, id);
    }
    @Transactional public Skill toggle(String owner, String id, boolean enabled) {
        if (!IDS.contains(id) && !AcademicResearchSkillCatalog.contains(id)) {
            if (jdbc.update("UPDATE custom_skill SET enabled=?,revision=revision+1,updated_at=? WHERE id=? AND owner_id=? AND deleted=FALSE", enabled, Instant.now().toString(), id, owner) == 0) throw ApiException.notFound();
            return get(owner, id);
        }
        jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE", String.class, owner);
        if (jdbc.update("UPDATE skill_preference SET enabled=? WHERE owner_id=? AND skill_id=?", enabled, owner, id) == 0) jdbc.update("INSERT INTO skill_preference VALUES(?,?,?)", owner, id, enabled);
        return get(owner, id);
    }
}
