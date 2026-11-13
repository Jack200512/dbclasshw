package com.dbclasshw.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:mysql://127.0.0.1:1/dbclasshw?connectTimeout=500&socketTimeout=500",
    "spring.datasource.username=unavailable_test", "spring.datasource.password=",
    "spring.datasource.hikari.connection-timeout=1000"})
class DocumentUnavailableIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test void unauthenticatedReadsReturn401() {
        assertThat(http.getForEntity("/api/documents",Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/users/me",Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void stateChangesRequireCsrfEvenBeforeAuthentication() {
        var r=http.postForEntity("/api/auth/register",Map.of("username","testuser","email","a@example.com","password","password123"),Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void unavailableDatabaseNeverReportsRegistrationSuccessOrLeaksDetails() {
        var csrf=http.getForEntity("/api/auth/csrf",Map.class);
        HttpHeaders headers=new HttpHeaders();
        headers.set("Cookie",csrf.getHeaders().getFirst("Set-Cookie").split(";",2)[0]);
        headers.set((String)csrf.getBody().get("headerName"),(String)csrf.getBody().get("token"));
        var body=Map.of("username","testuser","email","a@example.com","password","password123");
        var r=http.postForEntity("/api/auth/register",new HttpEntity<>(body,headers),Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).containsEntry("code","DATABASE_UNAVAILABLE");
        assertThat(http.getForEntity("/api/health",Map.class).getBody()).containsEntry("status","UP");
    }
}
