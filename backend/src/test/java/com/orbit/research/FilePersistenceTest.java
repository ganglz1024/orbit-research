package com.orbit.research;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FilePersistenceTest {
    @Test void migrationsAndUnicodeRowsSurviveClosingAndReopeningTheFileDatabase() throws Exception {
        String path = Path.of("target", "persistence-" + UUID.randomUUID()).toAbsolutePath().toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + path + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        var flyway = Flyway.configure().dataSource(url, "sa", "").load();
        assertEquals(2, Flyway.configure().dataSource(url, "sa", "").target("2").load().migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("INSERT INTO orbit_user VALUES('owner','researcher','fixture-hash','2026-10-28T00:00:00Z')");
            try (var insert = connection.prepareStatement("INSERT INTO conversation(id,owner_id,title,created_at,updated_at) VALUES('saved','owner',?,'2026-10-28T00:00:00Z','2026-10-28T00:00:00Z')")) {
                insert.setString(1, "关闭连接后仍保存的科研问题"); insert.executeUpdate();
            }
        }
        // Upgrade an existing V2 installation, preserving its owner and conversation.
        assertEquals(6, flyway.migrate().migrationsExecuted); // V3–V8, including additive reading bindings/snapshots.
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("INSERT INTO research_note VALUES('saved-note','owner','saved','save-key','hash',NULL,'研究笔记','重开后保留的人工判断','{\"kind\":\"user_note\",\"sources\":[]}',1,'now','now')");
            connection.createStatement().execute("INSERT INTO custom_skill VALUES('custom_saved','owner','证据核验','检查局限','按证据与推断分别输出',1,TRUE,FALSE,'now','now')");
            connection.createStatement().execute("INSERT INTO research_preference VALUES('owner','研究因果 TCN','custom_saved',TRUE,'model','now')");
            try(var insert=connection.prepareStatement("INSERT INTO model_configuration VALUES('owner','https://example.com/v1','saved-model',?,120,2048,FALSE,'max_tokens',1,'now')")) {
                insert.setString(1,new com.orbit.research.llm.SecretVault("",path+".key").encrypt("owner","fixture-secret"));insert.executeUpdate();
            }
        }
        // H2 shuts the database down when the last connection closes; this is a fresh open.
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT title FROM conversation WHERE id='saved'")) {
            assertTrue(rows.next()); assertEquals("关闭连接后仍保存的科研问题", rows.getString(1));
        }
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT body FROM research_note WHERE id='saved-note'")){assertTrue(rows.next());assertEquals("重开后保留的人工判断",rows.getString(1));}
            try(var rows=statement.executeQuery("SELECT research_context,default_skill_id,assistant_mode FROM research_preference WHERE owner_id='owner'")){assertTrue(rows.next());assertEquals("研究因果 TCN",rows.getString(1));assertEquals("custom_saved",rows.getString(2));assertEquals("model",rows.getString(3));}
            try(var rows=statement.executeQuery("SELECT instructions FROM custom_skill WHERE id='custom_saved'")){assertTrue(rows.next());assertEquals("按证据与推断分别输出",rows.getString(1));}
            try(var rows=statement.executeQuery("SELECT api_key_cipher,model FROM model_configuration WHERE owner_id='owner'")){assertTrue(rows.next());assertEquals("saved-model",rows.getString(2));assertEquals("fixture-secret",new com.orbit.research.llm.SecretVault("",path+".key").decrypt("owner",rows.getString(1)));}
        }
    }
}
