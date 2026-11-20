package com.dbclasshw.sync;

import com.dbclasshw.document.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentSocketHandler extends TextWebSocketHandler {
    private static final Logger log=LoggerFactory.getLogger(DocumentSocketHandler.class);
    private final Map<String,Connection> connections=new ConcurrentHashMap<>();
    private final DocumentService documents;
    private final ObjectMapper json;
    private final TransactionTemplate delivery;

    static class Connection {
        final WebSocketSession socket;
        final HttpSession session;
        final String sessionId;
        final long userId;
        volatile Long documentId;
        boolean active=true;
        Connection(WebSocketSession socket) {
            this.socket=new ConcurrentWebSocketSessionDecorator(socket,1000,8192);
            this.session=(HttpSession)socket.getAttributes().get("httpSession");
            this.sessionId=session.getId();
            this.userId=Long.parseLong(socket.getPrincipal().getName());
        }
        boolean authenticated() {
            try {
                if(!active || !sessionId.equals(session.getId())) return false;
                int timeout=session.getMaxInactiveInterval();
                if(timeout>0 && System.currentTimeMillis()-session.getLastAccessedTime()>=timeout*1000L) return false;
                Object value=session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                return value instanceof SecurityContext context && context.getAuthentication()!=null
                        && context.getAuthentication().isAuthenticated()
                        && Long.toString(userId).equals(context.getAuthentication().getName());
            } catch(IllegalStateException e) { return false; }
        }
    }

    public DocumentSocketHandler(DocumentService documents,ObjectMapper json,PlatformTransactionManager manager) {
        this.documents=documents; this.json=json;
        delivery=new TransactionTemplate(manager);
        delivery.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public void afterConnectionEstablished(WebSocketSession socket) {
        socket.setTextMessageSizeLimit(1024);
        var connection=new Connection(socket);
        connections.put(socket.getId(),connection);
        synchronized(connection) { if(!connection.authenticated()) close(connection); }
    }

    @Override protected void handleTextMessage(WebSocketSession socket,TextMessage message) {
        Connection connection=connections.get(socket.getId());
        if(connection==null) return;
        try {
            var body=json.readTree(message.getPayload());
            if(body==null || !body.isObject()) throw new IllegalArgumentException();
            String type=body.path("type").asText();
            if("SUBSCRIBE".equals(type) && body.size()==2 && body.path("documentId").canConvertToLong()
                    && body.path("documentId").isIntegralNumber() && body.get("documentId").asLong()>0) {
                long id=body.get("documentId").asLong();
                withAccess(connection,id,document -> {
                    connection.documentId=id;
                    send(connection,Map.of("type","SUBSCRIBED","documentId",id,"version",document.getVersion(),"currentRole",document.getCurrentRole()));
                });
            } else if("UNSUBSCRIBE".equals(type) && body.size()==1) {
                synchronized(connection) { connection.documentId=null; }
            } else if("PING".equals(type) && body.size()==1) {
                synchronized(connection) {
                    if(connection.authenticated()) send(connection,Map.of("type","PONG")); else close(connection);
                }
            } else close(connection); // No SEND/content writes or client-generated server events.
        } catch(Exception e) { close(connection); }
    }

    /** Lock the document before checking permission and sending; revocation uses the same lock. */
    private void withAccess(Connection connection,long id,java.util.function.Consumer<Document> action) {
        delivery.executeWithoutResult(status -> {
            Document document=documents.lockReadable(id,connection.userId);
            synchronized(connection) {
                if(!connection.authenticated()) { close(connection); return; }
                action.accept(document);
            }
        });
    }

    @TransactionalEventListener
    public void committed(DocumentEvent event) {
        for(Connection connection:connections.values()) {
            if(!Objects.equals(connection.documentId,event.documentId())) continue;
            if(event.targetId()!=null && event.targetId()!=connection.userId) continue;
            try {
                withAccess(connection,event.documentId(),document -> {
                    if(!Objects.equals(connection.documentId,event.documentId())) return;
                    Map<String,Object> message=new LinkedHashMap<>();
                    message.put("type",event.type()); message.put("documentId",event.documentId());
                    message.put("version",document.getVersion()); message.put("currentRole",document.getCurrentRole());
                    if(event.updatedAt()!=null) message.put("updatedAt",document.getUpdatedAt());
                    if(event.actorId()!=null && Objects.equals(event.version(),document.getVersion())) message.put("actorId",event.actorId());
                    send(connection,message);
                });
            } catch(DocumentNotFoundException | org.springframework.security.access.AccessDeniedException e) {
                close(connection);
            } catch(RuntimeException e) {
                // A failed notification must not turn an already committed HTTP save into failure.
                close(connection); log.warn("Document notification unavailable; connection closed");
            }
        }
    }

    private void send(Connection connection,Object message) {
        try {
            if(connection.socket.isOpen()) connection.socket.sendMessage(new TextMessage(json.writeValueAsString(message)));
        } catch(Exception e) { close(connection); }
    }
    private void close(Connection connection) {
        synchronized(connection) {
            connection.active=false; connection.documentId=null;
            connections.remove(connection.socket.getId());
            try { connection.socket.close(new CloseStatus(1008,"Subscription or session no longer available")); }
            catch(Exception ignored) { }
        }
    }
    public void closeSession(String sessionId) {
        for(Connection connection:connections.values()) if(connection.sessionId.equals(sessionId)) close(connection);
    }
    @Scheduled(fixedDelay=5000)
    public void expireSessions() {
        for(Connection connection:connections.values()) synchronized(connection) {
            if(!connection.authenticated()) close(connection);
        }
    }
    @Override public void afterConnectionClosed(WebSocketSession socket,CloseStatus status) {
        Connection connection=connections.remove(socket.getId());
        if(connection!=null) synchronized(connection) { connection.active=false; connection.documentId=null; }
    }
    @Override public void handleTransportError(WebSocketSession socket,Throwable error) {
        Connection connection=connections.get(socket.getId()); if(connection!=null) close(connection);
    }
}
