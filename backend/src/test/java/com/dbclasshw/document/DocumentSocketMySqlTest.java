package com.dbclasshw.document;

import com.dbclasshw.DbclasshwApplication;
import com.dbclasshw.sync.DocumentSocketHandler;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named="DBCLASSHW_MYSQL_TEST",matches="true")
class DocumentSocketMySqlTest {
    static final Map<String,HttpSession> sessions=new ConcurrentHashMap<>();
    @TestConfiguration static class SessionCapture {
        @Bean ServletListenerRegistrationBean<HttpSessionListener> capturedSessions() {
            return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
                @Override public void sessionCreated(HttpSessionEvent event) { sessions.put(event.getSession().getId(),event.getSession()); }
            });
        }
    }
    record Actor(DocumentMySqlIntegrationTest.Client client,long id) { }
    private Actor actor(String base) throws Exception {
        String name="socket_"+UUID.randomUUID().toString().replace("-","").substring(0,16),password=UUID.randomUUID()+"Aa1!";
        var client=new DocumentMySqlIntegrationTest.Client(base);client.csrf();
        var result=client.request("POST","/api/auth/register",Map.of("username",name,"email",name+"@example.test","password",password),true);
        assertThat(result.status()).isEqualTo(201);client.login(name,password);
        return new Actor(client,result.body().get("id").asLong());
    }
    private Map<String,Object> content(String text) { return Map.of("type","doc","content",List.of(Map.of("type","paragraph","content",List.of(Map.of("type","text","text",text))))); }
    private Map<String,Object> update(int version) { return Map.of("title","WS-verified","content",content("version "+version),"version",version); }
    static class Socket implements WebSocket.Listener,AutoCloseable {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        final StringBuilder partial=new StringBuilder();
        WebSocket ws;
        Socket(DocumentMySqlIntegrationTest.Client client,String origin) {
            ws=client.http.newWebSocketBuilder().header("Origin",origin).connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(client.base.replace("http:","ws:")+"/api/ws/documents"),this).join();
        }
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence value,boolean last) {
            partial.append(value);if(last){messages.add(partial.toString());partial.setLength(0);}ws.request(1);return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason) { closed.complete(code);return null; }
        @Override public void onError(WebSocket ws,Throwable error) { closed.completeExceptionally(error); }
        void subscribe(long id) throws Exception { ws.sendText("{\"type\":\"SUBSCRIBE\",\"documentId\":"+id+"}",true).join(); }
        com.fasterxml.jackson.databind.JsonNode next() throws Exception {
            String value=messages.poll(5,TimeUnit.SECONDS);assertThat(value).isNotNull();return DocumentMySqlIntegrationTest.JSON.readTree(value);
        }
        void quiet() throws Exception { assertThat(messages.poll(200,TimeUnit.MILLISECONDS)).isNull(); }
        @Override public void close() { ws.abort(); }
    }

    @Test void realSubscriptionsCommitRollbackPermissionsAndLogout() throws Exception {
        var support=new DocumentMySqlIntegrationTest();
        try(var context=support.start()) {
            var a=actor(support.base(context));var b=actor(support.base(context));var c=actor(support.base(context));var other=actor(support.base(context));
            long id=a.client.request("POST","/api/documents",Map.of("title","WS-test","content",content("initial")),true).body().get("id").asLong();
            String path="/api/documents/"+id;
            String bName=b.client.request("GET","/api/users/me",null,false).body().get("username").asText();
            String cName=c.client.request("GET","/api/users/me",null,false).body().get("username").asText();
            a.client.request("POST",path+"/permissions",Map.of("login",bName,"role","EDITOR"),true);
            a.client.request("POST",path+"/permissions",Map.of("login",cName,"role","VIEWER"),true);
            var anonymous=new DocumentMySqlIntegrationTest.Client(support.base(context));
            assertThrows(CompletionException.class,()->new Socket(anonymous,"http://127.0.0.1:5173"));
            assertThrows(CompletionException.class,()->new Socket(a.client,"https://untrusted.example"));
            try(var denied=new Socket(other.client,"http://127.0.0.1:5173")) {
                denied.subscribe(id);assertThat(denied.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);denied.quiet();
            }
            try(var editor=new Socket(b.client,"http://127.0.0.1:5173");var viewer=new Socket(c.client,"http://127.0.0.1:5173")) {
                editor.subscribe(id);assertThat(editor.next().path("type").asText()).isEqualTo("SUBSCRIBED");
                viewer.subscribe(id);assertThat(viewer.next().path("currentRole").asText()).isEqualTo("VIEWER");
                var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                var service=context.getBean(DocumentService.class);
                var json=DocumentMySqlIntegrationTest.JSON.valueToTree(content("transaction"));
                tx.executeWithoutResult(status->{
                    service.update(id,a.id,new UpdateDocumentRequest("WS-commit",json,1));
                    try {editor.quiet();viewer.quiet();}catch(Exception e){throw new RuntimeException(e);}
                });
                var event=editor.next();assertThat(event.path("type").asText()).isEqualTo("DOCUMENT_UPDATED");
                assertThat(event.path("version").asInt()).isEqualTo(2);assertThat(event.has("content")).isFalse();viewer.next();
                assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT version FROM documents WHERE id=?",Integer.class,id)).isEqualTo(2);
                tx.executeWithoutResult(status->{ service.update(id,a.id,new UpdateDocumentRequest("rollback",json,2));status.setRollbackOnly(); });
                editor.quiet();viewer.quiet();
                assertThat(a.client.request("PUT",path,update(1),true).status()).isEqualTo(409);editor.quiet();viewer.quiet();
                assertThat(b.client.request("PUT",path,update(2),true).status()).isEqualTo(200);
                assertThat(editor.next().path("version").asInt()).isEqualTo(3);viewer.next();
                assertThat(c.client.request("PUT",path,update(3),true).status()).isEqualTo(403);
                a.client.request("PUT",path+"/permissions/"+b.id,Map.of("role","VIEWER"),true);
                assertThat(editor.next().path("currentRole").asText()).isEqualTo("VIEWER");
                assertThat(b.client.request("PUT",path,update(3),true).status()).isEqualTo(403);
                a.client.request("DELETE",path+"/permissions/"+b.id,null,true);
                assertThat(editor.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);
                assertThat(a.client.request("PUT",path,update(3),true).status()).isEqualTo(200);
                editor.quiet();assertThat(viewer.next().path("version").asInt()).isEqualTo(4);
                c.client.request("POST","/api/auth/logout",null,true);
                assertThat(viewer.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);
                a.client.request("PUT",path,update(4),true);viewer.quiet();
            }
            long otherId=a.client.request("POST","/api/documents",Map.of("title","WS-switch","content",content("other")),true).body().get("id").asLong();
            try(var switched=new Socket(a.client,"http://127.0.0.1:5173")) {
                switched.subscribe(id);switched.next();switched.subscribe(otherId);switched.next();
                assertThat(a.client.request("PUT",path,update(5),true).status()).isEqualTo(200);switched.quiet();
                a.client.request("DELETE","/api/documents/"+otherId,null,true);
                assertThat(switched.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);
            }
            try(var forged=new Socket(a.client,"http://127.0.0.1:5173")) {
                forged.subscribe(id);forged.next();forged.ws.sendText("{\"type\":\"DOCUMENT_UPDATED\",\"documentId\":"+id+"}",true).join();
                assertThat(forged.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);
            }
            try(var deleted=new Socket(a.client,"http://127.0.0.1:5173")) {
                deleted.subscribe(id);deleted.next();a.client.request("DELETE",path,null,true);
                assertThat(deleted.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);
            }
        }
    }

    @Test void expiredHttpSessionCannotKeepBusinessSubscription() throws Exception {
        sessions.clear();
        try(var context=new SpringApplicationBuilder(DbclasshwApplication.class,SessionCapture.class).run("--server.port=0")) {
            var support=new DocumentMySqlIntegrationTest();var a=actor(support.base(context));
            long id=a.client.request("POST","/api/documents",Map.of("title","WS-expiry","content",content("expiry")),true).body().get("id").asLong();
            try(var socket=new Socket(a.client,"http://127.0.0.1:5173")) {
                socket.subscribe(id);socket.next();
                HttpSession session=sessions.values().stream().filter(s->s.getId().equals(a.client.session())).findFirst().orElseThrow();
                session.setMaxInactiveInterval(1);Thread.sleep(1200);
                context.getBean(DocumentSocketHandler.class).expireSessions();
                assertThat(socket.closed.get(5,TimeUnit.SECONDS)).isEqualTo(1008);
                assertThat(a.client.request("GET","/api/users/me",null,false).status()).isEqualTo(401);
                socket.quiet();
            }
            context.getBean(DocumentService.class).delete(id,a.id);
        }
    }
}
