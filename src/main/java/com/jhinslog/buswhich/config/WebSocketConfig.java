package com.jhinslog.buswhich.config;

import com.jhinslog.buswhich.websocket.ArrivalBusInfoHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ArrivalBusInfoHandler arrivalBusInfoHandler;

    @Autowired
    public WebSocketConfig(ArrivalBusInfoHandler arrivalBusInfoHandler) {
        this.arrivalBusInfoHandler = arrivalBusInfoHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(arrivalBusInfoHandler, "/ws/arrival-info")
                .setAllowedOrigins("*"); // 테스트 단계에서만 전부 허용. -> 서비스 운용시 특정 경로만 지정!!!
    }
}
