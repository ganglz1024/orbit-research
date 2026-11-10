package com.orbit.research.document;

import com.orbit.research.common.ApiException;
import com.orbit.research.common.Json;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class Annotations {
    public record Input(@NotBlank String versionId, @NotBlank String hash, @Min(1) int page,
                        @NotBlank @Pattern(regexp="highlight|underline|note") String type,
                        @NotNull @Size(max=8000) String quote, @NotNull @Size(max=4000) String comment,
                        @NotNull @Size(min=1, max=200) List<List<Double>> rects,
                        @NotBlank @Pattern(regexp="#[A-Fa-f0-9]{6}") String color,
                        @NotBlank @Pattern(regexp="user|ai") String origin) {}
    public record Edit(@Min(1) int revision, @NotNull @Size(max=4000) String comment, @NotBlank @Pattern(regexp="#[A-Fa-f0-9]{6}") String color) {}
    public record Position(@NotBlank String versionId, @Min(1) int page, @DecimalMin("0.25") @DecimalMax("5") double zoom, @Min(0) @Max(270) int rotation) {}
    public record Annotation(String id, String documentId, String versionId, String hash, int page, String type, String quote, String comment, Object rects, String color, String origin, int revision, String createdAt, String updatedAt) {}
    private final JdbcTemplate jdbc; private final Documents documents; private final Json json;
    public Annotations(JdbcTemplate jdbc, Documents documents, Json json) { this.jdbc = jdbc; this.documents = documents; this.json = json; }
    public List<Annotation> list(String owner, String documentId) {
        var doc = documents.get(owner, documentId);
        return jdbc.query("SELECT * FROM annotation WHERE document_id=? ORDER BY page_number,created_at,id", (rs, row) -> new Annotation(rs.getString("id"), documentId, doc.versionId(), doc.hash(), rs.getInt("page_number"), rs.getString("kind"), rs.getString("quote_text"), rs.getString("comment_text"), json.read(rs.getString("rectangles")), rs.getString("color"), rs.getString("origin"), rs.getInt("revision"), rs.getString("created_at"), rs.getString("updated_at")), documentId);
    }
    public Annotation add(String owner, String documentId, Input body) {
        var doc = documents.get(owner, documentId);
        if (!doc.versionId().equals(body.versionId()) || !doc.hash().equals(body.hash())) throw new ApiException(409, "DOCUMENT_VERSION_CONFLICT", "文件版本或校验值不一致。");
        if (body.page() > doc.pageCount()) throw new IllegalArgumentException();
        for (var quad : body.rects()) if (quad == null || quad.size() != 8 || quad.stream().anyMatch(n -> n == null || !Double.isFinite(n) || Math.abs(n) > 100000)) throw new IllegalArgumentException();
        documents.validateRectangles(owner, documentId, body.page(), body.rects());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM annotation WHERE document_id=?", Integer.class, documentId) >= 5000) throw new ApiException(422, "ANNOTATION_LIMIT", "单份 PDF 的批注上限为 5000 条。");
        String id = UUID.randomUUID().toString(), now = Instant.now().toString();
        jdbc.update("INSERT INTO annotation VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", id, documentId, body.page(), body.type(), body.quote(), body.comment(), json.write(body.rects()), body.color(), body.origin(), 1, now, now);
        return list(owner, documentId).stream().filter(annotation -> annotation.id().equals(id)).findFirst().orElseThrow();
    }
    private String documentId(String owner, String id) {
        return jdbc.query("SELECT a.document_id FROM annotation a JOIN document d ON d.id=a.document_id WHERE a.id=? AND d.owner_id=?", (rs, row) -> rs.getString(1), id, owner).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    @Transactional public Annotation edit(String owner, String id, Edit body) {
        String documentId = documentId(owner, id);
        if (jdbc.update("UPDATE annotation SET comment_text=?,color=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?", body.comment(), body.color(), Instant.now().toString(), id, body.revision()) != 1) throw conflict();
        return list(owner, documentId).stream().filter(annotation -> annotation.id().equals(id)).findFirst().orElseThrow();
    }
    public void delete(String owner, String id, int revision) {
        documentId(owner, id);
        if (jdbc.update("DELETE FROM annotation WHERE id=? AND revision=?", id, revision) != 1) throw conflict();
    }
    public Object position(String owner, String id) {
        documents.get(owner, id);
        return jdbc.query("SELECT * FROM reading_position WHERE document_id=?", (rs, row) -> new Position(id, rs.getInt("page_number"), rs.getDouble("zoom"), rs.getInt("rotation")), id).stream().findFirst().orElse(new Position(id, 1, 1, 0));
    }
    @Transactional public Position position(String owner, String id, Position body) {
        var doc = documents.get(owner, id);
        if (!body.versionId().equals(id) || body.page() > doc.pageCount() || body.rotation() % 90 != 0 || !Double.isFinite(body.zoom())) throw new IllegalArgumentException();
        jdbc.queryForObject("SELECT id FROM document WHERE id=? FOR UPDATE", String.class, id);
        if (jdbc.update("UPDATE reading_position SET page_number=?,zoom=?,rotation=?,updated_at=? WHERE document_id=?", body.page(), body.zoom(), body.rotation(), Instant.now().toString(), id) == 0)
            jdbc.update("INSERT INTO reading_position VALUES(?,?,?,?,?)", id, body.page(), body.zoom(), body.rotation(), Instant.now().toString());
        return body;
    }
    private static ApiException conflict() { return new ApiException(409, "ANNOTATION_CONFLICT", "批注已被其他操作修改，请刷新后重试。"); }
}
