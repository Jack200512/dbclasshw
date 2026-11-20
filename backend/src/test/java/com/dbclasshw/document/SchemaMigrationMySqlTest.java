package com.dbclasshw.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises production SQL only in new, uniquely named databases; retains evidence. */
@EnabledIfEnvironmentVariable(named="DBCLASSHW_MYSQL_TEST", matches="true")
class SchemaMigrationMySqlTest {
    @Test void m1UpgradePreservesLegacyRowsAndMatchesFreshSchema() throws Exception {
        try (var context=new DocumentMySqlIntegrationTest().start();
             var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
            String suffix=UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            String upgraded="dbclasshw_m2_verify_upgrade_"+suffix;
            String fresh="dbclasshw_m2_verify_fresh_"+suffix;
            try (var statement=connection.createStatement()) {
                statement.execute("CREATE DATABASE "+upgraded+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                statement.execute("USE "+upgraded);
                statement.execute("""
                        CREATE TABLE documents (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(255) NOT NULL,
                          content TEXT, version INT NOT NULL DEFAULT 1,
                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                statement.execute("""
                        INSERT INTO documents(id,title,content,version,created_at,updated_at)
                        VALUES(42,'M1 migration fixture','旧内容 😀 <script>literal</script>',7,
                        '2025-01-02 03:04:05','2025-06-07 08:09:10')
                        """);
            }
            var original=rows(connection,"SELECT id,title,content,version,created_at,updated_at FROM documents");
            // Ownership migration is repeatable at its supported intermediate schema only.
            for (int i=0;i<2;i++) run(connection,"migrate-m1-m2.sql",upgraded);
            for (int i=0;i<2;i++) {
                run(connection,"migrate-m2-richtext.sql",upgraded);
                run(connection,"migrate-m2-sharing.sql",upgraded);
            }
            assertThat(rows(connection,"SELECT id,title,content,version,created_at,updated_at FROM documents")).isEqualTo(original);
            assertThat(rows(connection,"""
                    SELECT d.id,u.username,u.enabled,d.content_format FROM documents d
                    JOIN users u ON u.id=d.owner_id
                    """)).containsExactly(List.of("42","__m1_migration__","0","PLAIN_TEXT"));
            run(connection,"migrate-m3-history.sql",upgraded);
            var baseline=rows(connection,"SELECT * FROM document_versions ORDER BY id");
            run(connection,"migrate-m3-history.sql",upgraded);
            assertThat(rows(connection,"SELECT * FROM document_versions ORDER BY id")).isEqualTo(baseline);
            assertThat(rows(connection,"SELECT id,title,content,version,created_at,updated_at FROM documents")).isEqualTo(original);
            assertThat(rows(connection,"SELECT document_id,version_number,change_type,content_format FROM document_versions"))
                    .containsExactly(List.of("42","7","BASELINE","PLAIN_TEXT"));
            try(var statement=connection.createStatement()) {
                statement.execute("UPDATE documents SET title='mismatch',updated_at=updated_at WHERE id=42");
                assertThat(org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                        ()->run(connection,"migrate-m3-history.sql",upgraded)).getMessage()).contains("snapshot mismatch");
                assertThat(rows(connection,"SELECT * FROM document_versions ORDER BY id")).isEqualTo(baseline);
                statement.execute("UPDATE documents SET title='M1 migration fixture',updated_at=updated_at WHERE id=42");
            }
            run(connection,"init.sql",fresh);
            // Compare definitions, index columns, FK targets/actions and CHECK expression,
            // rather than assuming matching constraint names imply matching behavior.
            for (String table:List.of("users","documents","document_permissions","document_versions")) {
                String a=rows(connection,"SHOW CREATE TABLE "+upgraded+"."+table).get(0).get(1);
                String b=rows(connection,"SHOW CREATE TABLE "+fresh+"."+table).get(0).get(1);
                assertThat(a.replaceAll(" AUTO_INCREMENT=\\d+", "")).isEqualTo(b.replaceAll(" AUTO_INCREMENT=\\d+", ""));
            }
            System.out.println("MIGRATION_SCHEMAS_VERIFIED="+upgraded+", "+fresh+" (retained)");
        }
    }

    private List<List<String>> rows(Connection connection,String sql) throws Exception {
        var result=new ArrayList<List<String>>();
        try (var statement=connection.createStatement(); var rs=statement.executeQuery(sql)) {
            while (rs.next()) {
                var row=new ArrayList<String>();
                for (int i=1;i<=rs.getMetaData().getColumnCount();i++) row.add(rs.getString(i));
                result.add(row);
            }
        }
        return result;
    }

    private void run(Connection connection,String file,String schema) throws Exception {
        String delimiter=";";
        var pending=new StringBuilder();
        // The project's scripts use standalone DELIMITER lines for the stored procedure.
        for (String line:Files.readAllLines(Path.of("sql",file))) {
            String trimmed=line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter=trimmed.substring(10).trim(); continue; }
            pending.append(line.replace("dbclasshw",schema)).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql=pending.toString().trim();
                try (var statement=connection.createStatement()) {
                    statement.execute(sql.substring(0,sql.length()-delimiter.length()));
                }
                pending.setLength(0);
            }
        }
        assertThat(pending.toString().trim()).isEmpty();
    }
}
