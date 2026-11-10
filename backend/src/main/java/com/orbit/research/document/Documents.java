package com.orbit.research.document;

import com.orbit.research.common.ApiException;
import com.orbit.research.conversation.Conversations;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class Documents {
    public record Document(String id, String versionId, String name, String hash, long bytes, int pageCount, String parseStatus, String createdAt) {}
    public record UploadResult(String filename, Document document, boolean reused, String code, String message) {}
    private final JdbcTemplate jdbc; private final Conversations conversations; private final TransactionTemplate tx; private final Path root;
    public Documents(JdbcTemplate jdbc, Conversations conversations, TransactionTemplate tx, @Value("${orbit.storage-path}") String storage) throws java.io.IOException {
        this.jdbc = jdbc; this.conversations = conversations; this.tx = tx;
        root = Path.of(storage).toAbsolutePath().normalize(); Files.createDirectories(root);
        if (System.getProperty("pdfbox.fontcache") == null) {
            Path cache = root.getParent().resolve("font-cache"); Files.createDirectories(cache);
            System.setProperty("pdfbox.fontcache", cache.toString());
        }
    }
    public static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception error) { throw new IllegalStateException(error); } }
    public Document get(String owner, String id) {
        return jdbc.query("SELECT * FROM document WHERE id=? AND owner_id=?", (rs, row) -> new Document(rs.getString("id"), rs.getString("id"), rs.getString("name"), rs.getString("content_hash"), rs.getLong("byte_size"), rs.getInt("page_count"), rs.getString("parse_status"), rs.getString("created_at")), id, owner)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public List<Document> list(String owner, String conversationId) {
        conversations.get(owner, conversationId);
        return jdbc.query("SELECT document_id FROM conversation_document WHERE conversation_id=?", (rs, row) -> get(owner, rs.getString(1)), conversationId);
    }
    private void link(String owner, String conversationId, String documentId) {
        tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM conversation WHERE id=? AND owner_id=? FOR UPDATE", String.class, conversationId, owner);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM conversation_document WHERE conversation_id=? AND document_id=?", Integer.class, conversationId, documentId) == 0)
                jdbc.update("INSERT INTO conversation_document VALUES(?,?)", conversationId, documentId);
        });
    }
    public void associate(String owner, String conversationId, String documentId) { conversations.get(owner, conversationId); get(owner, documentId); link(owner, conversationId, documentId); }
    public List<UploadResult> upload(String owner, String conversationId, List<MultipartFile> files) {
        conversations.get(owner, conversationId);
        if (files.isEmpty() || files.size() > 5) throw new ApiException(400, "BATCH_LIMIT", "每批请选择 1–5 份 PDF。");
        List<UploadResult> results = new ArrayList<>();
        for (var file : files) {
            String filename = safeName(file.getOriginalFilename());
            try {
                var result = uploadOne(owner, conversationId, filename, file); results.add(result);
            } catch (ApiException error) { results.add(new UploadResult(filename, null, false, error.code(), error.getMessage())); }
            catch (Exception error) { results.add(new UploadResult(filename, null, false, "UPLOAD_FAILED", "文件未保存，请重试。")); }
        }
        return results;
    }
    private synchronized UploadResult uploadOne(String owner, String conversationId, String filename, MultipartFile file) throws java.io.IOException {
        if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024) throw new ApiException(413, "FILE_TOO_LARGE", "文件为空或超过 50 MB。");
        byte[] bytes = file.getBytes();
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw new ApiException(422, "INVALID_PDF", "文件内容不是有效 PDF。");
        String hash = hash(bytes);
        var duplicate = jdbc.query("SELECT id FROM document WHERE owner_id=? AND content_hash=?", (rs, row) -> rs.getString(1), owner, hash).stream().findFirst();
        if (duplicate.isPresent()) { link(owner, conversationId, duplicate.get()); return new UploadResult(filename, get(owner, duplicate.get()), true, null, null); }
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM document WHERE owner_id=?", Long.class, owner);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE owner_id=?", Integer.class, owner) >= 1000) throw new ApiException(413, "DOCUMENT_LIMIT", "当前个人空间最多保存 1000 份 PDF。");
        if (used + bytes.length > 1024L * 1024 * 1024) throw new ApiException(413, "STORAGE_LIMIT", "当前个人空间的 1 GB 文件上限已达到。");
        int pages; String text = "", parseStatus = "readable_only";
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted()) throw new ApiException(422, "PDF_ENCRYPTED", "请先解除 PDF 密码保护后上传。");
            pages = pdf.getNumberOfPages();
            if (pages < 1 || pages > 2000) throw new ApiException(422, "PDF_PAGE_LIMIT", "PDF 页数应在 1–2000 页之间。");
            try {
                var stripper = new PDFTextStripper(); StringBuilder extracted = new StringBuilder();
                int extractedPages = 0;
                for (int page = 1; page <= Math.min(pages, 50) && extracted.length() < 16000; page++) {
                    stripper.setStartPage(page); stripper.setEndPage(page);
                    String chunk = stripper.getText(pdf).strip(); extractedPages = page;
                    if (!chunk.isBlank()) extracted.append("\n[PDF 第 ").append(page).append(" 页]\n").append(chunk);
                }
                boolean truncated = extracted.length() > 16000 || extractedPages < pages;
                text = extracted.substring(0, Math.min(16000, extracted.length()));
                if (!text.isBlank()) parseStatus = truncated ? "text_truncated" : "text_ready";
            } catch (Exception error) { parseStatus = "parse_failed"; text = ""; }
        } catch (InvalidPasswordException error) { throw new ApiException(422, "PDF_ENCRYPTED", "请先解除 PDF 密码保护后上传。"); }
        catch (java.io.IOException error) { throw new ApiException(422, "INVALID_PDF", "PDF 结构损坏或无法读取。"); }
        String id = UUID.randomUUID().toString(), now = Instant.now().toString(); Path asset = path(id);
        Files.write(asset, bytes, StandardOpenOption.CREATE_NEW);
        try {
            final int count = pages; final String extracted = text, state = parseStatus;
            tx.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO document VALUES(?,?,?,?,?,?,?,?,?)", id, owner, filename, hash, bytes.length, count, state, extracted, now);
                jdbc.update("INSERT INTO conversation_document VALUES(?,?)", conversationId, id);
            });
        } catch (RuntimeException error) { Files.deleteIfExists(asset); throw error; }
        return new UploadResult(filename, get(owner, id), false, null, null);
    }
    public List<Map<String, String>> context(String owner, String conversationId, List<String> ids) {
        List<Map<String, String>> sources = new ArrayList<>(); int length = 0;
        for (String id : ids) {
            var doc = get(owner, id);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM conversation_document WHERE conversation_id=? AND document_id=?", Integer.class, conversationId, id) != 1) throw new ApiException(409, "DOCUMENT_SCOPE", "选定文件尚未关联到当前会话。");
            String text = jdbc.queryForObject("SELECT extracted_text FROM document WHERE id=?", String.class, id);
            if (text == null || text.isBlank()) throw new ApiException(422, "DOCUMENT_NOT_READABLE_BY_AI", "所选 PDF 没有可用文本层，当前仍可在线阅读和批注。");
            length += text.length();
            if (length > 20000) throw new ApiException(422, "DOCUMENT_CONTEXT_LIMIT", "选定材料文本超过当前上下文限制，请减少材料数量。");
            sources.add(Map.of("id", id, "versionId", doc.versionId(), "hash", doc.hash(), "name", doc.name(), "parseStatus", doc.parseStatus(), "text", text));
        }
        return sources;
    }
    public FileSystemResource content(String owner, String id, String version) {
        get(owner, id); if (!id.equals(version)) throw ApiException.notFound();
        Path asset = path(id); if (!Files.isRegularFile(asset)) throw new ApiException(410, "FILE_MISSING", "文件实体不存在，请检查存储目录或备份。");
        return new FileSystemResource(asset);
    }
    public void validateRectangles(String owner, String id, int page, List<List<Double>> rectangles) {
        get(owner, id);
        try (var pdf = Loader.loadPDF(path(id).toFile())) {
            var crop = pdf.getPage(page - 1).getCropBox();
            for (var quad : rectangles) {
                double left = Double.POSITIVE_INFINITY, bottom = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
                for (int n = 0; n < 8; n += 2) {
                    double x = quad.get(n), y = quad.get(n + 1);
                    if (x < crop.getLowerLeftX() - 1 || x > crop.getUpperRightX() + 1 || y < crop.getLowerLeftY() - 1 || y > crop.getUpperRightY() + 1) throw new IllegalArgumentException();
                    left = Math.min(left, x); right = Math.max(right, x); bottom = Math.min(bottom, y); top = Math.max(top, y);
                }
                if (right - left < 0.01 || top - bottom < 0.01) throw new IllegalArgumentException();
            }
        } catch (java.io.IOException error) { throw new ApiException(410, "FILE_MISSING", "无法重新读取批注对应的原文件。"); }
    }
    private Path path(String id) { if (!id.matches("[a-f0-9-]{36}")) throw ApiException.notFound(); return root.resolve(id + ".pdf"); }
    private static String safeName(String value) { if (value == null) return "document.pdf"; String name = value.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip(); return name.isEmpty() ? "document.pdf" : name.substring(0, Math.min(name.length(), 250)); }
}
