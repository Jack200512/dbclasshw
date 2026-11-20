package com.dbclasshw.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="DBCLASSHW_MYSQL_TEST",matches="true")
class RichDocumentMySqlTest {
    private Map<String,Object> rich(String text) {
        return Map.of("type","doc","content",List.of(Map.of("type","paragraph","content",List.of(
                Map.of("type","text","text",text,"marks",List.of(Map.of("type","bold"),Map.of("type","italic")))))));
    }

    @Test void crudSearchLegacyAndAtomicConflicts() throws Exception {
        var support=new DocumentMySqlIntegrationTest();
        try(var context=support.start()) {
            String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
            String password=UUID.randomUUID()+"Aa1!";
            var a=new DocumentMySqlIntegrationTest.Client(support.base(context));
            var b=new DocumentMySqlIntegrationTest.Client(support.base(context));
            for(var entry:Map.of(a,"richA_"+suffix,b,"richB_"+suffix).entrySet()) {
                var client=entry.getKey();String name=entry.getValue();client.csrf();
                assertThat(client.request("POST","/api/auth/register",Map.of("username",name,"email",name+"@example.test","password",password),true).status()).isEqualTo(201);
                client.login(name,password);
            }
            var jdbc=context.getBean(JdbcTemplate.class);
            long owner=a.request("GET","/api/users/me",null,false).body().get("id").asLong();
            String title="M2-rich-"+suffix+" %_! ' OR 1=1";
            var created=a.request("POST","/api/documents",Map.of("title",title,"content",rich("初稿 <script>literal</script> 😀")),true);
            assertThat(created.status()).isEqualTo(201);
            long id=created.body().get("id").asLong();String path="/api/documents/"+id;
            assertThat(created.body().get("contentFormat").asText()).isEqualTo("TIPTAP_JSON");
            assertThat(DocumentMySqlIntegrationTest.JSON.readTree(created.body().get("content").asText())).isEqualTo(DocumentMySqlIntegrationTest.JSON.valueToTree(rich("初稿 <script>literal</script> 😀")));
            assertThat(a.request("GET",path,null,false).status()).isEqualTo(200);
            assertThat(a.request("POST","/api/documents",Map.of("title","bad","content","not JSON"),true).status()).isEqualTo(400);
            assertThat(a.request("POST","/api/documents",Map.of("title","bad","content",Map.of("type","doc","content",List.of(Map.of("type","image")))),true).status()).isEqualTo(400);
            assertThat(a.request("GET","/api/documents?keyword=%25_!",null,false).body().size()).isEqualTo(1);
            assertThat(b.request("GET","/api/documents?keyword="+java.net.URLEncoder.encode(suffix,java.nio.charset.StandardCharsets.UTF_8),null,false).body().size()).isZero();
            var update=Map.of("title",title,"content",rich("first writer"),"version",1);
            assertThat(b.request("PUT",path,update,true).status()).isEqualTo(404);
            assertThat(b.request("DELETE",path,null,true).status()).isEqualTo(404);
            assertThat(b.request("GET",path,null,false).status()).isEqualTo(404);
            assertThat(a.request("PUT",path,update,false).status()).isEqualTo(403);
            assertThat(a.request("DELETE",path,null,false).status()).isEqualTo(403);
            var first=a.request("PUT",path,update,true);
            assertThat(first.status()).isEqualTo(200);
            assertThat(first.body().get("version").asInt()).isEqualTo(2);
            assertThat(a.request("PUT",path,Map.of("title","stale","content",rich("must not win"),"version",1),true).status()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT content FROM documents WHERE id=?",String.class,id)).contains("first writer").doesNotContain("must not win");
            var parallel=Map.of("title",title,"content",rich("parallel"),"version",2);
            java.util.function.Supplier<Integer> task=()->{
                try {return a.request("PUT",path,parallel,true).status();} catch(Exception e){throw new RuntimeException(e);}
            };
            var one=CompletableFuture.supplyAsync(task);var two=CompletableFuture.supplyAsync(task);
            assertThat(List.of(one.join(),two.join())).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("SELECT version FROM documents WHERE id=?",Integer.class,id)).isEqualTo(3);
            assertThat(a.request("PUT",path,Map.of("title",title,"content",rich("forged"),"version",3,"ownerId",owner),true).status()).isEqualTo(400);
            // Add only a uniquely marked legacy fixture owned by this test user.
            String legacyTitle="legacy_"+suffix;
            String legacy="{not JSON}\n<img src=x onerror=alert(1)>\n末行";
            jdbc.update("INSERT INTO documents(owner_id,title,content) VALUES(?,?,?)",owner,legacyTitle,legacy);
            long legacyId=jdbc.queryForObject("SELECT id FROM documents WHERE owner_id=? AND title=?",Long.class,owner,legacyTitle);
            var legacyResponse=a.request("GET","/api/documents/"+legacyId,null,false);
            assertThat(legacyResponse.body().get("contentFormat").asText()).isEqualTo("PLAIN_TEXT");
            assertThat(legacyResponse.body().get("content").asText()).isEqualTo(legacy);
            assertThat(a.request("GET","/api/documents?keyword=",null,false).body().size()).isEqualTo(2);
            assertThat(a.request("PUT","/api/documents/"+legacyId,Map.of("title",legacyTitle,"content",rich(legacy),"version",1),true).status()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT content_format FROM documents WHERE id=?",String.class,legacyId)).isEqualTo("TIPTAP_JSON");
            var anonymous=new DocumentMySqlIntegrationTest.Client(support.base(context));anonymous.csrf();
            assertThat(anonymous.request("PUT",path,parallel,true).status()).isEqualTo(401);
            assertThat(anonymous.request("DELETE",path,null,true).status()).isEqualTo(401);
            assertThat(a.request("DELETE",path,null,true).status()).isEqualTo(204);
            assertThat(a.request("GET",path,null,false).status()).isEqualTo(404);
            assertThat(a.request("DELETE",path,null,true).status()).isEqualTo(404);
            assertThat(a.request("GET","/api/documents/"+legacyId,null,false).status()).isEqualTo(200);
            assertThat(a.request("DELETE","/api/documents/"+legacyId,null,true).status()).isEqualTo(204);
        }
    }
}
