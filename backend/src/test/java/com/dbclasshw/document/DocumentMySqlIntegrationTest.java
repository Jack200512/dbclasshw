package com.dbclasshw.document;

import com.dbclasshw.DbclasshwApplication;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL only. Requires explicit migration first; never clears existing rows. */
@EnabledIfEnvironmentVariable(named="DBCLASSHW_MYSQL_TEST", matches="true")
class DocumentMySqlIntegrationTest {
    static final ObjectMapper JSON=new ObjectMapper();
    record Reply(int status, JsonNode body) { }

    static class Client {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();
        final String base;
        String token;
        Client(String base) { this.base=base; }
        Reply request(String method,String path,Object body,boolean csrf) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create(base+path));
            if(csrf) builder.header("X-CSRF-TOKEN",token);
            if(body!=null) builder.header("Content-Type","application/json");
            var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(),JSON.readTree(response.body()));
        }
        void csrf() throws Exception { token=request("GET","/api/auth/csrf",null,false).body.get("token").asText(); }
        String session() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
        void login(String login,String password) throws Exception {
            csrf();
            assertThat(request("POST","/api/auth/login",Map.of("login",login,"password",password),true).status).isEqualTo(200);
            csrf();
        }
    }
    ConfigurableApplicationContext start() { return new SpringApplicationBuilder(DbclasshwApplication.class).run("--server.port=0"); }
    String base(ConfigurableApplicationContext context) { return "http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort(); }

    @Test void authenticationIsolationAndPersistence() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,16);
        String name="m2a_"+suffix, nameB="m2b_"+suffix, email=name+"@example.test";
        String password=UUID.randomUUID()+"aA1!";
        long documentId;
        try(var context=start()) {
            var jdbc=context.getBean(JdbcTemplate.class);
            var a=new Client(base(context)); var b=new Client(base(context));
            assertThat(a.request("GET","/api/documents",null,false).status).isEqualTo(401);
            var registration=Map.of("username",name,"email",email,"password",password);
            assertThat(a.request("POST","/api/auth/register",registration,false).status).isEqualTo(403);
            a.csrf(); String initial=a.session();
            assertThat(a.request("POST","/api/documents",Map.of("title","anonymous"),true).status).isEqualTo(401);
            a.token="invalid";
            assertThat(a.request("POST","/api/auth/register",registration,true).status).isEqualTo(403);
            a.csrf();
            var registered=a.request("POST","/api/auth/register",registration,true);
            assertThat(registered.status).isEqualTo(201);
            assertThat(registered.body.has("passwordHash")).isFalse();
            long userId=registered.body.get("id").asLong();
            String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,userId);
            assertThat(hash).startsWith("$2").isNotEqualTo(password);
            assertThat(context.getBean(PasswordEncoder.class).matches(password,hash)).isTrue();
            assertThat(a.request("POST","/api/auth/register",registration,true).status).isEqualTo(409);
            assertThat(a.request("POST","/api/auth/register",Map.of("username",name+"x","email",email,"password",password),true).status).isEqualTo(409);
            assertThat(a.request("POST","/api/auth/register",Map.of("username",name,"email","other"+email,"password",password),true).status).isEqualTo(409);
            assertThat(a.request("POST","/api/auth/register",Map.of("username","bad@name","email","bad","password","short"),true).status).isEqualTo(400);
            assertThat(a.request("POST","/api/auth/register",Map.of("username",name+"x","email","x"+email,"password",password,"id",99),true).status).isEqualTo(400);
            assertThat(a.request("POST","/api/auth/login",Map.of("login",name,"password","incorrect"),true).status).isEqualTo(401);
            assertThat(a.request("POST","/api/auth/login",Map.of("login","__m1_migration__","password","anything"),true).status).isEqualTo(401);
            String preLoginToken=a.token;
            a.login(name,password);
            assertThat(a.session()).isNotEqualTo(initial);
            String newToken=a.token;
            a.token=preLoginToken;
            assertThat(a.request("POST","/api/documents",Map.of("title","stale csrf"),true).status).isEqualTo(403);
            a.token=newToken;
            assertThat(a.request("GET","/api/users/me",null,false).body.get("id").asLong()).isEqualTo(userId);
            for(String title:new String[]{"","   ","x".repeat(256)})
                assertThat(a.request("POST","/api/documents",Map.of("title",title),true).status).isEqualTo(400);
            for(String field:new String[]{"ownerId","owner_id","id","version","createdAt","updatedAt"})
                assertThat(a.request("POST","/api/documents",Map.of("title","forged",field,1),true).status).isEqualTo(400);
            var rich=Map.of("type","doc","content",List.of(Map.of("type","paragraph","content",List.of(Map.of("type","text","text","真实 MySQL 😀 private")))));
            var created=a.request("POST","/api/documents",Map.of("title","M2-test-"+suffix,"content",rich),true);
            assertThat(created.status).isEqualTo(201);
            documentId=created.body.get("id").asLong();
            assertThat(jdbc.queryForObject("SELECT owner_id FROM documents WHERE id=?",Long.class,documentId)).isEqualTo(userId);
            assertThat(a.request("GET","/api/documents/"+documentId,null,false).status).isEqualTo(200);
            assertThat(a.request("GET","/api/documents/-1",null,false).status).isEqualTo(404);
            b.csrf();
            assertThat(b.request("POST","/api/auth/register",Map.of("username",nameB,"email",nameB+"@example.test","password",password),true).status).isEqualTo(201);
            b.login(nameB+"@example.test",password);
            assertThat(b.request("GET","/api/documents",null,false).body.size()).isZero();
            assertThat(b.request("GET","/api/documents/"+documentId,null,false).status).isEqualTo(404);
            assertThat(a.request("PUT","/api/users/me",Map.of("email",nameB+"@example.test"),true).status).isEqualTo(409);
            assertThat(a.request("PUT","/api/users/me",Map.of("email","bad"),true).status).isEqualTo(400);
            email="updated_"+email;
            assertThat(a.request("PUT","/api/users/me",Map.of("email",email),true).status).isEqualTo(200);
            String oldSession=a.session();
            assertThat(a.request("POST","/api/auth/logout",null,false).status).isEqualTo(403);
            assertThat(a.request("POST","/api/auth/logout",null,true).status).isEqualTo(200);
            a.csrf();
            assertThat(a.request("GET","/api/users/me",null,false).status).isEqualTo(401);
            var old=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base(context)+"/api/users/me"))
                    .header("Cookie","JSESSIONID="+oldSession).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(old.statusCode()).isEqualTo(401);
            a.login(email,password);
            assertThat(a.request("GET","/api/documents/"+documentId,null,false).status).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE owner_id IS NULL",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE username='__m1_migration__'",Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='documents' AND COLUMN_NAME='owner_id'",String.class)).isEqualTo("NO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='documents' AND COLUMN_NAME='owner_id' AND REFERENCED_TABLE_NAME='users'",Long.class)).isEqualTo(1);
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    ()->jdbc.update("INSERT INTO documents(title,owner_id) VALUES('invalid test',NULL)"));
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    ()->jdbc.update("INSERT INTO documents(title,owner_id) VALUES('invalid test',-1)"));
        }
        try(var restarted=start()) {
            var client=new Client(base(restarted));client.login(email,password);
            assertThat(client.request("GET","/api/documents/"+documentId,null,false).status).isEqualTo(200);
        }
    }

    @Test void freshInitializationMatchesUpgradedSchema() throws Exception {
        try(var context=start()) {
            var jdbc=context.getBean(JdbcTemplate.class);
            String schema="dbclasshw_m2_verify_"+UUID.randomUUID().toString().replace("-","").substring(0,8);
            String sql=Files.readString(Path.of("sql/init.sql")).replace("dbclasshw",schema);
            try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
                ScriptUtils.executeSqlScript(connection,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            }
            String columns="SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLLATION_NAME,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME,ORDINAL_POSITION";
            assertThat(jdbc.queryForList(columns,schema)).isEqualTo(jdbc.queryForList(columns,"dbclasshw"));
            String constraints="SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME,CONSTRAINT_NAME";
            assertThat(jdbc.queryForList(constraints,schema)).isEqualTo(jdbc.queryForList(constraints,"dbclasshw"));
            System.out.println("FRESH_SCHEMA_VERIFIED="+schema+" (retained, no business data removed)");
        }
    }
}
