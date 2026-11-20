package com.dbclasshw.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named="DBCLASSHW_MYSQL_TEST",matches="true")
class SharingMySqlTest {
    record Actor(DocumentMySqlIntegrationTest.Client http,long id,String name) { }

    private Actor actor(String base,String prefix) throws Exception {
        String name=prefix+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String password=UUID.randomUUID()+"Aa1!";
        var client=new DocumentMySqlIntegrationTest.Client(base);client.csrf();
        var registered=client.request("POST","/api/auth/register",Map.of("username",name,"email",name+"@example.test","password",password),true);
        assertThat(registered.status()).isEqualTo(201);client.login(name,password);
        return new Actor(client,registered.body().get("id").asLong(),name);
    }

    private Map<String,Object> content(String text) {
        return Map.of("type","doc","content",List.of(Map.of("type","paragraph","content",List.of(Map.of("type","text","text",text)))));
    }
    private Map<String,Object> update(String title,int version) { return Map.of("title",title,"content",content(title),"version",version); }

    @Test void fullSharingMatrixConstraintsConflictAndCascade() throws Exception {
        var support=new DocumentMySqlIntegrationTest();
        try(var context=support.start()) {
            var jdbc=context.getBean(JdbcTemplate.class);
            var a=actor(support.base(context),"shareA_");var b=actor(support.base(context),"shareB_");var c=actor(support.base(context),"shareC_");
            var outsider=actor(support.base(context),"shareD_");
            String title="M2-sharing-"+UUID.randomUUID();
            var created=a.http.request("POST","/api/documents",Map.of("title",title,"content",content("private")),true);
            assertThat(created.status()).isEqualTo(201);long id=created.body().get("id").asLong();
            String path="/api/documents/"+id,permissions=path+"/permissions";
            assertThat(created.body().get("currentRole").asText()).isEqualTo("OWNER");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_permissions WHERE document_id=?",Long.class,id)).isZero();
            assertThat(a.http.request("POST",permissions,Map.of("login",a.name,"role","EDITOR"),true).status()).isEqualTo(400);
            assertThat(a.http.request("POST",permissions,Map.of("login",b.name,"role","OWNER"),true).status()).isEqualTo(400);
            assertThat(a.http.request("POST",permissions,Map.of("login","no_such_"+UUID.randomUUID(),"role","EDITOR"),true).status()).isEqualTo(400);
            assertThat(a.http.request("POST",permissions,Map.of("login",b.name,"role","EDITOR"),false).status()).isEqualTo(403);
            assertThat(a.http.request("POST",permissions,Map.of("login",b.name,"role","EDITOR"),true).status()).isEqualTo(201);
            assertThat(a.http.request("POST",permissions,Map.of("login",c.name+"@example.test","role","VIEWER"),true).status()).isEqualTo(201);
            var originalPermissions=jdbc.queryForList("SELECT * FROM document_permissions WHERE document_id=? ORDER BY id",id);
            try(var connection=context.getBean(javax.sql.DataSource.class).getConnection()) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.FileSystemResource("sql/migrate-m2-sharing.sql"));
            }
            assertThat(jdbc.queryForList("SELECT * FROM document_permissions WHERE document_id=? ORDER BY id",id)).isEqualTo(originalPermissions);
            assertThat(a.http.request("POST",permissions,Map.of("login",b.name,"role","VIEWER"),true).status()).isEqualTo(409);
            var members=a.http.request("GET",permissions,null,false);
            assertThat(members.body().size()).isEqualTo(2);assertThat(members.body().toString()).doesNotContain("password");
            for(var expected:Map.of(b,"EDITOR",c,"VIEWER").entrySet()) {
                var client=expected.getKey().http;
                var list=client.request("GET","/api/documents?keyword="+title,null,false);
                assertThat(list.body().size()).isEqualTo(1);
                assertThat(list.body().get(0).get("currentRole").asText()).isEqualTo(expected.getValue());
                assertThat(client.request("GET",path,null,false).body().get("currentRole").asText()).isEqualTo(expected.getValue());
                assertThat(client.request("DELETE",path,null,true).status()).isEqualTo(403);
                assertThat(client.request("GET",permissions,null,false).status()).isEqualTo(403);
                assertThat(client.request("POST",permissions,Map.of("login",outsider.name,"role","EDITOR"),true).status()).isEqualTo(403);
                assertThat(client.request("PUT",permissions+"/"+b.id,Map.of("role","EDITOR"),true).status()).isEqualTo(403);
                assertThat(client.request("DELETE",permissions+"/"+b.id,null,true).status()).isEqualTo(403);
            }
            assertThat(c.http.request("PUT",path,update(title,1),true).status()).isEqualTo(403);
            assertThat(outsider.http.request("GET",path,null,false).status()).isEqualTo(404);
            assertThat(outsider.http.request("GET","/api/documents?keyword="+title,null,false).body().size()).isZero();
            assertThat(outsider.http.request("GET",permissions,null,false).status()).isEqualTo(404);
            // OWNER and EDITOR submit the same version concurrently: exactly one may commit.
            var first=CompletableFuture.supplyAsync(()->put(a,path,update(title,1)));
            var second=CompletableFuture.supplyAsync(()->put(b,path,update(title,1)));
            assertThat(List.of(first.join(),second.join())).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("SELECT version FROM documents WHERE id=?",Integer.class,id)).isEqualTo(2);
            assertThat(a.http.request("PUT",permissions+"/"+b.id,Map.of("role","VIEWER"),true).status()).isEqualTo(200);
            assertThat(b.http.request("PUT",path,update(title,2),true).status()).isEqualTo(403);
            assertThat(a.http.request("PUT",permissions+"/"+b.id,Map.of("role","OWNER"),true).status()).isEqualTo(400);
            assertThat(a.http.request("DELETE",permissions+"/"+a.id,null,true).status()).isEqualTo(400);
            assertThat(a.http.request("PUT",permissions+"/"+outsider.id,Map.of("role","VIEWER"),true).status()).isEqualTo(404);
            assertThat(a.http.request("DELETE",permissions+"/"+c.id,null,true).status()).isEqualTo(204);
            assertThat(c.http.request("GET",path,null,false).status()).isEqualTo(404);
            assertThat(c.http.request("PUT",path,update(title,2),true).status()).isEqualTo(404);
            assertThat(c.http.request("GET","/api/documents",null,false).body().size()).isZero();
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO document_permissions(document_id,user_id,role) VALUES(?,?,?)",id,b.id,"EDITOR"));
            var invalidRole=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO document_permissions(document_id,user_id,role) VALUES(?,?,?)",id,outsider.id,"OWNER"));
            assertThat(((java.sql.SQLException)invalidRole.getRootCause()).getErrorCode()).isEqualTo(3819);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO document_permissions(document_id,user_id,role) VALUES(?,?,?)",-1,outsider.id,"EDITOR"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO document_permissions(document_id,user_id,role) VALUES(?,?,?)",id,-1,"EDITOR"));
            assertThat(a.http.request("DELETE",path,null,true).status()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_permissions WHERE document_id=?",Long.class,id)).isZero();
            assertThat(b.http.request("GET",path,null,false).status()).isEqualTo(404);
        }
    }

    private int put(Actor actor,String path,Object body) {
        try {return actor.http.request("PUT",path,body,true).status();}catch(Exception e){throw new RuntimeException(e);}
    }

    @Test void queuedSaveRechecksCommittedRevocationAndDowngrade() throws Exception {
        var support=new DocumentMySqlIntegrationTest();
        try(var context=support.start()) {
            var a=actor(support.base(context),"raceA_");var b=actor(support.base(context),"raceB_");
            var created=a.http.request("POST","/api/documents",Map.of("title","M2-permission-race","content",content("unchanged")),true);
            long id=created.body().get("id").asLong();String path="/api/documents/"+id;
            var sharing=context.getBean(PermissionService.class);
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var jdbc=context.getBean(JdbcTemplate.class);
            for(boolean revoke:new boolean[]{false,true}) {
                if(revoke) a.http.request("PUT",path+"/permissions/"+b.id,Map.of("role","EDITOR"),true);
                else assertThat(a.http.request("POST",path+"/permissions",Map.of("login",b.name,"role","EDITOR"),true).status()).isEqualTo(201);
                AtomicReference<CompletableFuture<Integer>> queued=new AtomicReference<>();
                tx.executeWithoutResult(status->{
                    // Production service joins this real transaction and keeps the document lock.
                    if(revoke) sharing.revoke(id,a.id,b.id);
                    else sharing.change(id,a.id,b.id,new SharingRequests.Change("VIEWER"));
                    CountDownLatch started=new CountDownLatch(1);
                    queued.set(CompletableFuture.supplyAsync(()->{started.countDown();return put(b,path,update("must not commit",1));}));
                    try {assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();Thread.sleep(250);}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                    assertThat(queued.get().isDone()).isFalse();
                });
                assertThat(queued.get().get(10,TimeUnit.SECONDS)).isEqualTo(revoke?404:403);
                assertThat(jdbc.queryForObject("SELECT version FROM documents WHERE id=?",Integer.class,id)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT content FROM documents WHERE id=?",String.class,id)).contains("unchanged");
            }
            assertThat(a.http.request("DELETE",path,null,true).status()).isEqualTo(204);
        }
    }
}
