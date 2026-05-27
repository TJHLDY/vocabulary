package org.gduf.config;

import org.gduf.pk.PkWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PkWebSocketHandler pkWebSocketHandler;

    public WebSocketConfig(PkWebSocketHandler pkWebSocketHandler) {
        this.pkWebSocketHandler = pkWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pkWebSocketHandler, "/ws/pk").setAllowedOrigins("*");
    }
}
