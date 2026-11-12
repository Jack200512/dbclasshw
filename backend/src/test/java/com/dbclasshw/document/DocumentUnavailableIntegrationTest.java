package com.dbclasshw.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/dbclasshw?connectTimeout=500&socketTimeout=500",
        "spring.datasource.username=unavailable_test",
        "spring.datasource.password=",
        "spring.datasource.hikari.connection-timeout=1000"
})
class DocumentUnavailableIntegrationTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void rejectsInvalidTitlesBeforeAccessingDatabase() {
        for (String title : new String[]{"", "   ", "x".repeat(256)}) {
            var response = http.postForEntity("/api/documents", Map.of("title", title), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "INVALID_REQUEST");
        }
        assertThat(http.postForEntity("/api/documents", Map.of(), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsServerManagedFieldsAndMalformedIds() {
        for (String field : new String[]{"id", "version", "createdAt", "updatedAt"}) {
            assertThat(http.postForEntity("/api/documents", Map.of("title", "Test", field, 1),
                    Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(http.getForEntity("/api/documents/not-a-number", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unavailableDatabaseNeverReportsSuccessOrLeaksDetails() {
        var response = http.postForEntity("/api/documents", Map.of("title", "Test"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(Map.of("code", "DATABASE_UNAVAILABLE",
                "message", "Document storage is temporarily unavailable"));
        assertThat(http.getForEntity("/api/documents", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(http.getForEntity("/api/health", Map.class).getBody()).containsEntry("status", "UP");
    }
}
