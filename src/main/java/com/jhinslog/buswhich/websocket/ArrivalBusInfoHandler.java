package com.jhinslog.buswhich.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final ObjectMapper objectMapper;
    private final BusArrivalService busArrivalService;

    // 세션과 해당 세션이 구독한 정류소 ID를 매핑하여 관리
    private final Map<WebSocketSession, String> sessionStationMap = new ConcurrentHashMap<>();

    @Autowired
    public ArrivalBusInfoHandler(ObjectMapper objectMapper, BusArrivalService busArrivalService) {
        this.objectMapper = objectMapper;
        this.busArrivalService = busArrivalService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {} (ID: {})", session.getRemoteAddress(), session.getId());
        // 클라이언트에게 연결 성공 및 구독 안내 메시지 전송
        sendMessage(session, Map.of(
                "type", "connection_success",
                "message", "Welcome! Send a message like {\"type\":\"subscribe\", \"stationId\":\"YOUR_ARS_ID\"} to get bus arrival info."
        ));
    }

    /*구독, 비구독에 따라 분기 처리*/
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.info("Received message from session {}: {}", session.getId(), payload);

        try {
            WebSocketSubscriptionRequestDto requestDto = objectMapper.readValue(payload, WebSocketSubscriptionRequestDto.class);

            if (requestDto.getType() == null) {
                sendErrorMessage(session, "Message type is missing.");
                return;
            }

            switch (requestDto.getType().toLowerCase()) {
                case "subscribe":
                    handleSubscription(session, requestDto.getStationId());
                    break;
                case "unsubscribe":
                    handleUnsubscription(session);
                    break;
                default:
                    sendErrorMessage(session, "Invalid message type: " + requestDto.getType() +
                            ". Supported types are 'subscribe' or 'unsubscribe'.");
                    break;
            }
        } catch (JsonProcessingException e) { //Json 파싱 실패시 오류 메시지 처리
            logger.error("Error parsing JSON message from session {}: {}", session.getId(), payload, e);
            sendErrorMessage(session, "Invalid JSON format. Please send a valid JSON message.");
        } catch (Exception e) {
            logger.error("Unexpected error processing message from session {}: {}", session.getId(), payload, e);
            sendErrorMessage(session, "An unexpected error occurred while processing your request.");
        }
    }

    private void handleSubscription(WebSocketSession session, String stationId) throws IOException {
        if (stationId == null || stationId.trim().isEmpty()) {
            sendErrorMessage(session, "Station ID (arsId) is required for subscription.");
            return;
        }

        // 기존 구독 정보가 있다면 제거 (다른 정류소로 변경 시)
        String previousStationId = sessionStationMap.get(session);
        if (previousStationId != null && !previousStationId.equals(stationId)) {
            logger.info("Session {} changing subscription from {} to {}", session.getId(), previousStationId, stationId);
        }
        sessionStationMap.put(session, stationId);
        logger.info("Session {} subscribed to stationId: {}", session.getId(), stationId);

        // 구독 성공 응답 전송
        sendMessage(session, Map.of(
                "type", "subscribe_success",
                "stationId", stationId,
                "message", "Successfully subscribed to station " + stationId + "."
        ));

        // 구독 즉시 해당 정류소의 현재 도착 정보 1회 전송
        sendInitialArrivalInfo(session, stationId);
    }

    private void handleUnsubscription(WebSocketSession session) throws IOException {
        String removedStationId = sessionStationMap.remove(session);
        if (removedStationId != null) {
            logger.info("Session {} unsubscribed from stationId: {}", session.getId(), removedStationId);
            sendMessage(session, Map.of(
                    "type", "unsubscribe_success",
                    "stationId", removedStationId,
                    "message", "Successfully unsubscribed from station " + removedStationId + "."
            ));
        } else {
            logger.warn("Session {} tried to unsubscribe but was not subscribed.", session.getId());
            sendErrorMessage(session, "You are not currently subscribed to any station.");
        }
    }

    // 특정 세션에 초기 도착 정보 전송
    private void sendInitialArrivalInfo(WebSocketSession session, String stationId) {
        try {
            List<SpecificStationArrivalDto> arrivalInfo = busArrivalService.getArrivalsByStationId(stationId);

            if (arrivalInfo != null && !arrivalInfo.isEmpty()) {
                sendMessage(session, Map.of(
                        "type", "arrival_info",
                        "stationId", stationId,
                        "data", arrivalInfo
                ));
                logger.info("Sent initial arrival info for stationId {} to session {}", stationId, session.getId());
            } else {
                logger.info("No arrival info found for stationId {} or list is empty. Notifying client.", stationId);
                sendMessage(session, Map.of(
                        "type", "no_arrival_info",
                        "stationId", stationId,
                        "message", "No arrival information currently available for station " + stationId + "."
                ));
            }
        } catch (Exception e) {
            logger.error("Error fetching or sending initial arrival info for station {} to session {}: {}",
                    stationId, session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "Error fetching initial arrival data for station: " + stationId + ".");
        }
    }

    // 주기적으로 모든 구독자에게 업데이트된 정보를 보내는 메소드
    public void broadcastArrivalUpdates(String stationId, List<SpecificStationArrivalDto> arrivalInfo) {
        if (arrivalInfo == null) { // 빈 리스트도 보낼 수 있도록 null 체크만
            logger.debug("No updates (null list) to broadcast for stationId: {}", stationId);
            return;
        }

        sessionStationMap.forEach((session, subscribedStationId) -> {
            if (stationId.equals(subscribedStationId) && session.isOpen()) {
                try {
                    // 도착 정보가 비어있을 수도 있으므로, 그 경우에도 메시지를 보낼 수 있게 함
                    if (arrivalInfo.isEmpty()) {
                        sendMessage(session, Map.of(
                                "type", "no_arrival_info", // 또는 "arrival_update_empty" 등
                                "stationId", stationId,
                                "message", "No arrival information currently available for station " + stationId + "."
                        ));
                        logger.info("Broadcasted empty arrival update for station {} to session {}", stationId, session.getId());
                    } else {
                        sendMessage(session, Map.of(
                                "type", "arrival_info_update", // 초기 정보와 구분하기 위해 다른 type 사용 가능
                                "stationId", stationId,
                                "data", arrivalInfo
                        ));
                        logger.info("Broadcasted arrival update for station {} to session {}", stationId, session.getId());
                    }
                } catch (Exception e) {
                    logger.error("Error broadcasting message to session {}: {}", session.getId(), e.getMessage(), e);
                    // 여기서 세션 제거 로직을 고려할 수 있지만, 동시성 문제에 주의해야 합니다.
                    // sessionStationMap.remove(session); // 직접 제거 시 ConcurrentModificationException 발생 가능성
                }
            }
        });
    }


    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {} (ID: {}): {}",
                session.getRemoteAddress(), session.getId(), exception.getMessage(), exception);
        // 전송 오류 발생 시, 해당 세션의 구독 정보를 제거하는 것이 안전할 수 있습니다.
        sessionStationMap.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String removedStationId = sessionStationMap.remove(session);
        logger.info("WebSocket connection closed: {} (ID: {}) - Status: {}. Unsubscribed from station: {}",
                session.getRemoteAddress(), session.getId(), status, removedStationId != null ? removedStationId : "N/A");
    }

    // 클라이언트에게 메시지를 보내는 헬퍼 메소드 (JSON 변환 포함)
    private void sendMessage(WebSocketSession session, Map<String, Object> messagePayload) {
        try {
            if (session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(messagePayload);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (IOException e) {
            logger.error("Error serializing or sending message to session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    // 클라이언트에게 오류 메시지를 보내는 헬퍼 메소드
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        sendMessage(session, Map.of("type", "error", "message", errorMessage));
    }
}
