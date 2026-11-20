import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Optional browser fixture; only writes a new document for this run's test user. */
public class HistoryPlainFixture {
    public static void main(String[] args) throws Exception {
        Properties properties=new Properties();
        Path local=Path.of("application-local.properties");
        if(Files.exists(local)) try(var reader=Files.newBufferedReader(local)) { properties.load(reader); }
        String url=properties.getProperty("spring.datasource.url",System.getenv("DBCLASSHW_DB_URL"));
        if(url==null) url="jdbc:mysql://127.0.0.1:3306/dbclasshw?characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci";
        String username=properties.getProperty("spring.datasource.username",System.getenv("DBCLASSHW_DB_USERNAME"));
        String password=properties.getProperty("spring.datasource.password",System.getenv("DBCLASSHW_DB_PASSWORD"));
        try(var connection=DriverManager.getConnection(url,username,password)) {
            connection.setAutoCommit(false);
            long owner=Long.parseLong(args[0]);
            try(var user=connection.prepareStatement("SELECT username FROM users WHERE id=?")) {
                user.setLong(1,owner);
                try(var rs=user.executeQuery()) {
                    if(!rs.next() || !rs.getString(1).equals(args[1]) || !args[1].startsWith("histBrowser_"))
                        throw new IllegalArgumentException("Expected isolated browser test user");
                }
            }
            long id;
            try(var insert=connection.prepareStatement("INSERT INTO documents(owner_id,title,content,version) VALUES(?,?,?,7)",Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1,owner);insert.setString(2,args[2]);
                insert.setString(3,"{literal}\n<img src=x onerror=window.__unsafe=1>\n历史纯文本 😀");
                insert.executeUpdate();
                try(var rs=insert.getGeneratedKeys()) { rs.next();id=rs.getLong(1); }
            }
            try(var snapshot=connection.prepareStatement("INSERT INTO document_versions(document_id,version_number,title,content,content_format,editor_id,created_at,change_type) SELECT id,version,title,content,content_format,owner_id,updated_at,'BASELINE' FROM documents WHERE id=?")) {
                snapshot.setLong(1,id);snapshot.executeUpdate();
            }
            connection.commit();System.out.println(id);
        }
    }
}
