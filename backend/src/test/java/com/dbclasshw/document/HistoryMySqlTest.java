package com.dbclasshw.document;

import com.dbclasshw.DbclasshwApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DBCLASSHW_MYSQL_TEST",matches="true")
class HistoryMySqlTest {
    record Actor(DocumentMySqlIntegrationTest.Client http,long id,String name) { }
    private Actor actor(String base) throws Exception {
        String name="hist_"+UUID.randomUUID().toString().replace("-", "").substring(0,14);
        String password=UUID.randomUUID()+"Aa1!";
        var client=new DocumentMySqlIntegrationTest.Client(base);client.csrf();
        var result=client.request("POST","/api/auth/register",Map.of("username",name,"email",name+"@example.test","password",password),true);
        assertThat(result.status()).isEqualTo(201);client.login(name,password);
        return new Actor(client,result.body().get("id").asLong(),name);
    }
    private Map<String,Object> rich(String text) { return Map.of("type","doc","content",List.of(Map.of("type","paragraph","content",List.of(Map.of("type","text","text",text))))); }
    private Map<String,Object> update(String text,int version) { return Map.of("title",text,"content",rich(text),"version",version); }

    @Test void historyPermissionsConcurrencyAtomicRollbackAndNotifications() throws Exception {
        var support=new DocumentMySqlIntegrationTest();
        String schema="dbclasshw_m3_verify_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
        String url;
        // Failure injection is strictly isolated from the existing business database.
        try(var bootstrap=support.start()) {
            url=bootstrap.getEnvironment().getProperty("spring.datasource.url").replace("/dbclasshw", "/"+schema);
            String sql=Files.readString(Path.of("sql/init.sql")).replace("dbclasshw",schema);
            try(var connection=bootstrap.getBean(javax.sql.DataSource.class).getConnection()) {
                ScriptUtils.executeSqlScript(connection,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            }
        }
        try(var context=new SpringApplicationBuilder(DbclasshwApplication.class).run("--server.port=0","--spring.datasource.url="+url)) {
            var jdbc=context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(schema);
            var a=actor(support.base(context));var b=actor(support.base(context));var c=actor(support.base(context));var outsider=actor(support.base(context));
            var created=a.http.request("POST","/api/documents",Map.of("title","v1 title","content",rich("v1 body 😀")),true);
            assertThat(created.status()).isEqualTo(201);
            long id=created.body().get("id").asLong();String path="/api/documents/"+id,history=path+"/versions";
            assertThat(a.http.request("GET",history+"/1",null,false).body().get("changeType").asText()).isEqualTo("CREATED");
            assertThat(a.http.request("PUT",path,update("v2",1),true).status()).isEqualTo(200);
            assertThat(a.http.request("PUT",path,update("v3",2),true).status()).isEqualTo(200);
            var original=jdbc.queryForList("SELECT * FROM document_versions WHERE document_id=? ORDER BY version_number",id);
            var list=a.http.request("GET",history,null,false).body();
            assertThat(list.findValuesAsText("versionNumber")).containsExactly("3","2","1");
            assertThat(list.get(0).has("content")).isFalse();
            assertThat(list.toString()).doesNotContain("password");
            var v1=a.http.request("GET",history+"/1",null,false).body();
            assertThat(v1.get("editorId").asLong()).isEqualTo(a.id);
            assertThat(v1.get("content").asText()).contains("v1 body 😀");
            assertThat(v1.get("createdAt").asText()).isEqualTo(created.body().get("updatedAt").asText());
            for(var entry:Map.of(b,"EDITOR",c,"VIEWER").entrySet()) {
                assertThat(a.http.request("POST",path+"/permissions",Map.of("login",entry.getKey().name,"role",entry.getValue()),true).status()).isEqualTo(201);
                assertThat(entry.getKey().http.request("GET",history,null,false).status()).isEqualTo(200);
                assertThat(entry.getKey().http.request("GET",history+"/1",null,false).status()).isEqualTo(200);
            }
            assertThat(outsider.http.request("GET",history,null,false).status()).isEqualTo(404);
            assertThat(outsider.http.request("GET",history+"/1",null,false).status()).isEqualTo(404);
            assertThat(outsider.http.request("POST",history+"/1/restore",Map.of("version",3),true).status()).isEqualTo(404);
            assertThat(c.http.request("POST",history+"/1/restore",Map.of("version",3),true).status()).isEqualTo(403);
            assertThat(a.http.request("POST",history+"/1/restore",Map.of("version",3),false).status()).isEqualTo(403);
            assertThat(a.http.request("POST",history+"/1/restore",Map.of("version",3,"editorId",b.id),true).status()).isEqualTo(400);
            assertThat(a.http.request("POST",history+"/999/restore",Map.of("version",3),true).status()).isEqualTo(404);
            for(Object invalid:List.of(Map.of(),Map.of("version",0),Map.of("version",Integer.MAX_VALUE)))
                assertThat(a.http.request("POST",history+"/1/restore",invalid,true).status()).isEqualTo(400);
            try(var socket=new DocumentSocketMySqlTest.Socket(b.http,"http://127.0.0.1:5173")) {
                socket.subscribe(id);socket.next();
                var restored=a.http.request("POST",history+"/1/restore",Map.of("version",3),true);
                assertThat(restored.status()).isEqualTo(200);
                assertThat(restored.body().get("version").asInt()).isEqualTo(4);
                assertThat(restored.body().get("content")).isEqualTo(created.body().get("content"));
                assertThat(restored.body().get("title")).isEqualTo(created.body().get("title"));
                var event=socket.next();assertThat(event.get("type").asText()).isEqualTo("DOCUMENT_UPDATED");
                assertThat(event.get("version").asInt()).isEqualTo(4);
                assertThat(jdbc.queryForList("SELECT * FROM document_versions WHERE document_id=? AND version_number<=3 ORDER BY version_number",id)).isEqualTo(original);
                var snapshot=a.http.request("GET",history+"/4",null,false).body();
                assertThat(snapshot.get("changeType").asText()).isEqualTo("RESTORED");
                assertThat(snapshot.get("sourceVersion").asInt()).isEqualTo(1);
                assertThat(a.http.request("POST",history+"/1/restore",Map.of("version",3),true).status()).isEqualTo(409);
                assertThat(a.http.request("PUT",path,update("stale",3),true).status()).isEqualTo(409);socket.quiet();
                var stable=jdbc.queryForList("SELECT * FROM documents WHERE id=?",id);
                var stableHistory=jdbc.queryForList("SELECT * FROM document_versions WHERE document_id=? ORDER BY id",id);
                // Reject every snapshot in this isolated DB to exercise real transaction rollback.
                jdbc.execute("CREATE TRIGGER reject_test_snapshot BEFORE INSERT ON document_versions FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test snapshot rejection'");
                try {
                    long count=jdbc.queryForObject("SELECT COUNT(*) FROM documents",Long.class);
                    assertThat(a.http.request("POST","/api/documents",Map.of("title","must rollback","content",rich("failure")),true).status()).isEqualTo(503);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents",Long.class)).isEqualTo(count);
                    assertThat(a.http.request("PUT",path,update("must rollback",4),true).status()).isEqualTo(503);
                    assertThat(a.http.request("POST",history+"/2/restore",Map.of("version",4),true).status()).isEqualTo(503);
                    assertThat(jdbc.queryForList("SELECT * FROM documents WHERE id=?",id)).isEqualTo(stable);
                    assertThat(jdbc.queryForList("SELECT * FROM document_versions WHERE document_id=? ORDER BY id",id)).isEqualTo(stableHistory);
                    socket.quiet();
                } finally { jdbc.execute("DROP TRIGGER reject_test_snapshot"); }
                // Restore and save compete for the same current version, exactly one commits.
                var saving=CompletableFuture.supplyAsync(()->requestStatus(a,"PUT",path,update("concurrent",4)));
                var restoring=CompletableFuture.supplyAsync(()->requestStatus(b,"POST",history+"/2/restore",Map.of("version",4)));
                assertThat(List.of(saving.join(),restoring.join())).containsExactlyInAnyOrder(200,409);
                assertThat(socket.next().get("version").asInt()).isEqualTo(5);socket.quiet();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_versions WHERE document_id=?",Long.class,id)).isEqualTo(5);
            }
            assertThat(b.http.request("POST",history+"/1/restore",Map.of("version",5),true).status()).isEqualTo(200);
            assertThat(a.http.request("GET",history+"/6",null,false).body().get("editorId").asLong()).isEqualTo(b.id);
            // Permission transaction holds the existing document lock; queued restore must recheck.
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var sharing=context.getBean(PermissionService.class);
            for(boolean revoke:new boolean[]{false,true}) {
                if(revoke) a.http.request("PUT",path+"/permissions/"+b.id,Map.of("role","EDITOR"),true);
                var queued=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Integer>>();
                tx.executeWithoutResult(status->{
                    if(revoke) sharing.revoke(id,a.id,b.id);
                    else sharing.change(id,a.id,b.id,new SharingRequests.Change("VIEWER"));
                    queued.set(CompletableFuture.supplyAsync(()->requestStatus(b,"POST",history+"/1/restore",Map.of("version",6))));
                    try { Thread.sleep(250); } catch(InterruptedException e) { throw new RuntimeException(e); }
                    assertThat(queued.get().isDone()).isFalse();
                });
                assertThat(queued.get().get(10,TimeUnit.SECONDS)).isEqualTo(revoke?404:403);
                assertThat(jdbc.queryForObject("SELECT version FROM documents WHERE id=?",Integer.class,id)).isEqualTo(6);
            }
            assertThat(b.http.request("GET",history,null,false).status()).isEqualTo(404);
            assertThat(b.http.request("GET",history+"/1",null,false).status()).isEqualTo(404);
            assertThatThrownBy(()->jdbc.update("INSERT INTO document_versions(document_id,version_number,title,content_format,editor_id,change_type) VALUES(?,1,'duplicate','PLAIN_TEXT',?,'CREATED')",id,a.id))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            assertThat(a.http.request("DELETE",path,null,true).status()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_versions WHERE document_id=?",Long.class,id)).isZero();
            // Legacy BASELINE restores preserve the explicit plain-text format, not JSON guesses.
            jdbc.update("INSERT INTO documents(owner_id,title,content,version) VALUES(?,?,?,9)",a.id,"legacy baseline","{literal}\n<img src=x onerror=alert(1)>\n旧文本 😀");
            long legacyId=jdbc.queryForObject("SELECT id FROM documents WHERE owner_id=? AND title='legacy baseline'",Long.class,a.id);
            var legacy=context.getBean(DocumentMapper.class).findById(legacyId,a.id);
            context.getBean(DocumentVersionMapper.class).insert(legacy,a.id,"BASELINE",null);
            var plain=a.http.request("POST","/api/documents/"+legacyId+"/versions/9/restore",Map.of("version",9),true);
            assertThat(plain.status()).isEqualTo(200);
            assertThat(plain.body().get("version").asInt()).isEqualTo(10);
            assertThat(plain.body().get("contentFormat").asText()).isEqualTo("PLAIN_TEXT");
            assertThat(plain.body().get("content").asText()).isEqualTo(legacy.getContent());
            assertThat(a.http.request("GET","/api/documents/"+legacyId+"/versions/10",null,false).body().get("sourceVersion").asInt()).isEqualTo(9);
            System.out.println("HISTORY_ATOMICITY_PERMISSIONS_SOCKET_VERIFIED="+schema+" (retained)");
        }
    }

    private int requestStatus(Actor actor,String method,String path,Object body) {
        try { return actor.http.request(method,path,body,true).status(); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
}
