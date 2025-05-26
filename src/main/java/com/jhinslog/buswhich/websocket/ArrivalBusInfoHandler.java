package com.jhinslog.buswhich.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhinslog.buswhich.dto.request.WebSocketSubscriptionRequestDto;
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.service.BusArrivalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ArrivalBusInfoHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ArrivalBusInfoHandler.class);
    private final ObjectMapper objectMapper; // JSON 직렬화/역직렬화
    private final BusArrivalService busArrivalService; // 버스 도착 정보 서비스

    // 세션과 구독 중인 정류소 ID를 매핑하여 관리합니다.
    // Key: WebSocketSession, Value: stationId (구독 중인 정류소 ID)
    private final Map<WebSocketSession, String> sessionStationMap = new ConcurrentHashMap<>();

    // (선택 사항) 특정 정류소 ID를 구독하는 세션 목록을 관리할 수도 있습니다.
    // Key: stationId, Value: List<WebSocketSession>
    // private final Map<String, List<WebSocketSession>> stationSubscriptions = new ConcurrentHashMap<>();

    @Autowired
    public ArrivalBusInfoHandler(ObjectMapper objectMapper, BusArrivalService busArrivalService) {
        this.objectMapper = objectMapper;
        this.busArrivalService = busArrivalService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {} (ID: {})", session.getRemoteAddress(), session.getId());
        // sessionStationMap.put(session, null); // 초기에는 구독 정보 없음
        // 연결된 클라이언트에게 환영 메시지 또는 구독 요청 안내 메시지 전송 (선택 사항)
        session.sendMessage(new TextMessage("Welcome! Please send your station ID to subscribe. (e.g., {\"stationId\":\"12345\"})"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.info("Received message from {}: {}", session.getId(), payload);

        try {
            // 클라이언트로부터 받은 메시지를 DTO로 변환 (예: 구독 요청)
            // 여기서는 간단히 stationId를 받는다고 가정. 실제로는 JSON 객체 형태가 더 좋음.
            // 예: {"type": "subscribe", "stationId": "12345"}
            // 예: {"type": "unsubscribe"}

            // 임시로, 클라이언트가 stationId 문자열만 보낸다고 가정
            // String stationId = payload;

            // JSON 형태의 구독 요청을 처리하는 경우
            WebSocketSubscriptionRequestDto requestDto = objectMapper.readValue(payload, WebSocketSubscriptionRequestDto.class);

            if ("subscribe".equalsIgnoreCase(requestDto.getType()) && requestDto.getStationId() != null) {
                String stationId = requestDto.getStationId();
                // 기존 구독 정보가 있다면 제거 (다른 정류소로 변경 시)
                removePreviousSubscription(session);

                sessionStationMap.put(session, stationId);
                logger.info("Session {} subscribed to stationId: {}", session.getId(), stationId);
                session.sendMessage(new TextMessage("Subscribed to station: " + stationId));

                // 구독 즉시 해당 정류소의 현재 도착 정보 1회 전송
                sendInitialArrivalInfo(session, stationId);

            } else if ("unsubscribe".equalsIgnoreCase(requestDto.getType())) {
                removePreviousSubscription(session);
                logger.info("Session {} unsubscribed.", session.getId());
                session.sendMessage(new TextMessage("Unsubscribed successfully."));
            } else {
                logger.warn("Invalid message format from session {}: {}", session.getId(), payload);
                session.sendMessage(new TextMessage("Error: Invalid message format. Send e.g., {\"type\":\"subscribe\", \"stationId\":\"YOUR_STATION_ID\"}"));
            }

        } catch (IOException e) {
            logger.error("Error processing message from session {}: {}", session.getId(), payload, e);
            session.sendMessage(new TextMessage("Error processing your request: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error processing message from session {}: {}", session.getId(), payload, e);
            session.sendMessage(new TextMessage("An unexpected error occurred."));
        }
    }

    private void removePreviousSubscription(WebSocketSession session) {
        String previousStationId = sessionStationMap.remove(session);
        if (previousStationId != null) {
            logger.info("Session {} removed previous subscription to stationId: {}", session.getId(), previousStationId);
        }
    }

    // 특정 세션에 초기 도착 정보 전송
    private void sendInitialArrivalInfo(WebSocketSession session, String stationId) {
        try {
            session.sendMessage(new TextMessage("Subscription to " + stationId + " successful. Real-time data feed will start once available. (Service method pending)"));
            logger.warn("BusArrivalService.getArrivalsByStationId(String stationId) method is not yet implemented or called.");


        } catch (Exception e) {
            logger.error("Error sending initial arrival info for station {} to session {}: {}", stationId, session.getId(), e.getMessage(), e);
            try {
                session.sendMessage(new TextMessage("Error fetching initial data for station: " + stationId));
            } catch (IOException ex) {
                logger.error("Error sending error message to session {}: {}", session.getId(), ex.getMessage());
            }
        }
    }

    // 주기적으로 모든 구독자에게 업데이트된 정보를 보내는 메소드 (별도의 스케줄러에서 호출될 수 있음)
    // 또는 특정 이벤트 발생 시 호출
    public void broadcastArrivalUpdates(String stationId, List<SpecificStationArrivalDto> arrivalInfo) {
        if (arrivalInfo == null || arrivalInfo.isEmpty()) {
            // logger.debug("No updates to broadcast for stationId: {}", stationId);
            return;
        }

        String arrivalInfoJson;
        try {
            arrivalInfoJson = objectMapper.writeValueAsString(arrivalInfo);
        } catch (IOException e) {
            logger.error("Error serializing arrival info for stationId {}: {}", stationId, e.getMessage());
            return;
        }

        sessionStationMap.forEach((session, subscribedStationId) -> {
            if (stationId.equals(subscribedStationId) && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(arrivalInfoJson));
                    logger.info("Broadcasted arrival update for station {} to session {}", stationId, session.getId());
                } catch (IOException e) {
                    logger.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
                    // 세션이 닫혔거나 문제가 있을 수 있으므로, 여기서 세션 제거 로직을 고려할 수도 있습니다.
                    // sessions.remove(session);
                }
            }
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String removedStationId = sessionStationMap.remove(session);
        logger.info("WebSocket connection closed: {} (ID: {}) - Status: {}. Unsubscribed from station: {}",
                session.getRemoteAddress(), session.getId(), status, removedStationId != null ? removedStationId : "N/A");
    }
}
