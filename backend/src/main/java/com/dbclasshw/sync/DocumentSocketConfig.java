package com.dbclasshw.sync;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Configuration
@EnableWebSocket
@EnableScheduling
public class DocumentSocketConfig implements WebSocketConfigurer {
    private final DocumentSocketHandler handler;
    private final String[] origins;
    public DocumentSocketConfig(DocumentSocketHandler handler,
            @Value("${app.websocket.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:8080,http://localhost:8080}") String[] origins) {
        this.handler=handler; this.origins=origins;
    }

    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler,"/api/ws/documents").setAllowedOrigins(origins)
                .addInterceptors(new HandshakeInterceptor() {
                    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,
                                                              WebSocketHandler socket,Map<String,Object> attributes) {
                        if(!(request instanceof ServletServerHttpRequest servlet) || request.getPrincipal()==null
                                || request.getHeaders().getOrigin()==null) return false;
                        HttpSession session=servlet.getServletRequest().getSession(false);
                        if(session==null) return false;
                        attributes.put("httpSession",session);
                        return true;
                    }
                    @Override public void afterHandshake(ServerHttpRequest r,ServerHttpResponse s,WebSocketHandler h,Exception e) { }
                });
    }

    @Bean ServletListenerRegistrationBean<HttpSessionListener> socketSessionListener() {
        return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
            @Override public void sessionDestroyed(HttpSessionEvent event) { handler.closeSession(event.getSession().getId()); }
        });
    }
}
