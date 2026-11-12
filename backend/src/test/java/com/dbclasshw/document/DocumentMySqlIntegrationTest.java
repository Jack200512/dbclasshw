package com.dbclasshw.document;

import com.dbclasshw.DbclasshwApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: uses only dbclasshw; preserves all rows, including uniquely named test rows. */
@EnabledIfEnvironmentVariable(named = "DBCLASSHW_MYSQL_TEST", matches = "true")
class DocumentMySqlIntegrationTest {
    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(DbclasshwApplication.class).run("--server.port=0");
    }

    private String baseUrl(ConfigurableApplicationContext context) {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        return "http://127.0.0.1:" + port + "/api/documents";
    }

    @Test
    void persistsActualMysqlDocumentAcrossApplicationRestart() throws Exception {
        Long id;
        String title = "M1-Task2-test-" + UUID.randomUUID();
        String content = "真实 MySQL 持久化 😀 ' OR 1=1 --";
        TestRestTemplate http = new TestRestTemplate();
        try (var context = start()) {
            var env = context.getEnvironment();
            String url = env.getRequiredProperty("spring.datasource.url");
            assertThat(url).containsPattern("/dbclasshw(?:\\?|$)");
            String serverUrl = url.replaceFirst("/dbclasshw(?=\\?|$)", "/");
            // Explicitly opted-in initialization: only CREATE IF NOT EXISTS, no data removal.
            try (var connection = DriverManager.getConnection(serverUrl,
                    env.getRequiredProperty("spring.datasource.username"),
                    env.getRequiredProperty("spring.datasource.password"))) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource("sql/init.sql"));
            }
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("dbclasshw");
            assertThat(jdbc.queryForObject("SELECT TABLE_COLLATION FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA='dbclasshw' AND TABLE_NAME='documents'", String.class))
                    .startsWith("utf8mb4_");
            String endpoint = baseUrl(context);
            var created = http.postForEntity(endpoint, new CreateDocumentRequest(title, content), Document.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Document document = created.getBody();
            assertThat(document).isNotNull();
            id = document.getId();
            assertThat(id).isPositive();
            assertThat(document.getVersion()).isEqualTo(1);
            assertThat(document.getCreatedAt()).isNotNull();
            assertThat(document.getUpdatedAt()).isNotNull();
            assertThat(created.getHeaders().getLocation().toString()).isEqualTo("/api/documents/" + id);
            assertThat(jdbc.queryForObject("SELECT content FROM documents WHERE id=?", String.class, id))
                    .isEqualTo(content);
            assertThat(http.getForObject(endpoint, Document[].class))
                    .anySatisfy(row -> assertThat(row.getId()).isEqualTo(id));
            assertThat(http.getForEntity(endpoint + "/-1", Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(http.postForEntity(endpoint, Map.of("title", " "), Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(http.postForEntity(endpoint, Map.of("title", "x".repeat(256)), Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            System.out.println("MYSQL_VERIFIED_DOCUMENT_ID=" + id);
        }
        // A new application context and connection pool must read the committed row.
        try (var restarted = start()) {
            var response = http.getForEntity(baseUrl(restarted) + "/" + id, Document.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTitle()).isEqualTo(title);
            assertThat(response.getBody().getContent()).isEqualTo(content);
            assertThat(response.getBody().getVersion()).isEqualTo(1);
            System.out.println("MYSQL_RESTART_PERSISTENCE_VERIFIED=" + id);
        }
    }
}
