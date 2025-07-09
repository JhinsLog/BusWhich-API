package com.jhinslog.buswhich.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhinslog.buswhich.dto.request.WebSocketSubscriptionRequestDto; // 이 DTO를 사용합니다.
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.service.BusArrivalCacheService; // BusArrivalCacheService 사용
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap; // HashMap import 추가
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ArrivalBusInfoHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ArrivalBusInfoHandler.class);
    private final ObjectMapper objectMapper;
    private final BusArrivalCacheService cacheService; // BusArrivalService 대신 BusArrivalCacheService 주입

    // stationId를 키로 하고, 해당 정류장을 구독하는 WebSocketSession 세트를 값으로 가짐
    private final Map<String, Set<WebSocketSession>> stationSubscriptions = new ConcurrentHashMap<>();
    // WebSocketSession을 키로 하고, 해당 세션이 구독하는 stationId 세트를 값으로 가짐 (연결 종료 시 구독 해제용)
    private final Map<WebSocketSession, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Autowired
    public ArrivalBusInfoHandler(ObjectMapper objectMapper, BusArrivalCacheService cacheService) { // 생성자 수정
        this.objectMapper = objectMapper;
        this.cacheService = cacheService; // cacheService 주입
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {} (ID: {})", session.getRemoteAddress(), session.getId());
        sessionSubscriptions.put(session, new CopyOnWriteArraySet<>()); // 세션별 구독 목록 초기화
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
            String stationId = requestDto.getStationId(); // WebSocketSubscriptionRequestDto 사용
            String actionType = requestDto.getType();     // WebSocketSubscriptionRequestDto 사용

            if (actionType == null || actionType.trim().isEmpty()) {
                sendErrorMessage(session, "Message type (subscribe/unsubscribe) is missing.");
                return;
            }

            // stationId는 구독/구독해제 시 모두 필요
            if (stationId == null || stationId.trim().isEmpty()) {
                sendErrorMessage(session, "Station ID (arsId) is required.");
                return;
            }

            switch (actionType.toLowerCase()) {
                case "subscribe":
                    handleSubscription(session, stationId);
                    break;
                case "unsubscribe":
                    handleUnsubscription(session, stationId); // stationId 전달
                    break;
                default:
                    sendErrorMessage(session, "Invalid message type: " + actionType +
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
        // stationSubscriptions 맵 업데이트
        stationSubscriptions.computeIfAbsent(stationId, k -> new CopyOnWriteArraySet<>()).add(session);

        // sessionSubscriptions 맵 업데이트
        Set<String> stationsForSession = sessionSubscriptions.get(session);
        // afterConnectionEstablished에서 초기화되므로 null일 가능성은 낮지만 방어 코드
        if (stationsForSession == null) {
            stationsForSession = new CopyOnWriteArraySet<>();
            sessionSubscriptions.put(session, stationsForSession);
        }
        stationsForSession.add(stationId);

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

    private void handleUnsubscription(WebSocketSession session, String stationId) throws IOException {
        boolean removedFromStation = false;
        Set<WebSocketSession> sessionsForStation = stationSubscriptions.get(stationId);
        if (sessionsForStation != null) {
            removedFromStation = sessionsForStation.remove(session);
            if (sessionsForStation.isEmpty()) {
                stationSubscriptions.remove(stationId); // 해당 정류소를 구독하는 세션이 없으면 맵에서 제거
            }
        }

        boolean removedFromSession = false;
        Set<String> stationsForSession = sessionSubscriptions.get(session);
        if (stationsForSession != null) {
            removedFromSession = stationsForSession.remove(stationId);
            // 이 세션이 더 이상 아무것도 구독하지 않으면 sessionSubscriptions에서 제거할 수도 있음 (선택적)
            // if (stationsForSession.isEmpty()) {
            //     sessionSubscriptions.remove(session);
            // }
        }

        if (removedFromStation || removedFromSession) {
            logger.info("Session {} unsubscribed from stationId: {}", session.getId(), stationId);
            sendMessage(session, Map.of(
                    "type", "unsubscribe_success",
                    "stationId", stationId,
                    "message", "Successfully unsubscribed from station " + stationId + "."
            ));
        } else {
            logger.warn("Session {} tried to unsubscribe from stationId {} but was not fully subscribed.", session.getId(), stationId);
            sendErrorMessage(session, "You were not subscribed to station " + stationId + " or it was already removed.");
        }
    }

    // 특정 세션에 초기 도착 정보 전송
    private void sendInitialArrivalInfo(WebSocketSession session, String stationId) {
        try {
            // BusArrivalCacheService를 사용하여 캐시에서 데이터 조회 (API 직접 호출 X)
            List<SpecificStationArrivalDto> arrivalInfo = cacheService.getArrivalsForStation(stationId, false);

            if (arrivalInfo == null) { // cacheService가 null을 반환할 경우 대비
                arrivalInfo = Collections.emptyList();
            }

            if (!arrivalInfo.isEmpty()) {
                sendMessage(session, Map.of(
                        "type", "initial_arrival_info", // 메시지 타입 명확화
                        "stationId", stationId,
                        "data", arrivalInfo
                ));
                logger.info("Sent initial arrival info for stationId {} to session {}", stationId, session.getId());
            } else {
                logger.info("No initial arrival info found for stationId {} or list is empty. Notifying client.", stationId);
                sendMessage(session, Map.of(
                        "type", "no_initial_arrival_info", // 초기 정보 없음을 명확히
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
        if (arrivalInfo == null) {
            logger.debug("Received null arrivalInfo for stationId: {}. Broadcasting as empty.", stationId);
            arrivalInfo = Collections.emptyList(); // null 대신 빈 리스트로 처리
        }

        Set<WebSocketSession> subscribedSessions = stationSubscriptions.get(stationId);
        if (subscribedSessions != null && !subscribedSessions.isEmpty()) {
            String type = arrivalInfo.isEmpty() ? "no_arrival_info_update" : "arrival_info_update";
            String messageLog = arrivalInfo.isEmpty() ? "empty arrival update" : "arrival update";

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            payload.put("stationId", stationId);

            if (arrivalInfo.isEmpty()) {
                payload.put("message", "No arrival information currently available for station " + stationId + ".");
            } else {
                payload.put("data", arrivalInfo);
            }

            for (WebSocketSession session : subscribedSessions) {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, payload);
                        // 로그 레벨을 debug로 변경하여 너무 많은 로그 방지
                        logger.debug("Broadcasted {} for station {} to session {}", messageLog, stationId, session.getId());
                    } catch (Exception e) {
                        logger.error("Error broadcasting message to session {}: {}", session.getId(), e.getMessage(), e);
                        // 오류 발생 시 해당 세션 정리 고려 (주의: 반복자 내에서 컬렉션 수정 시 문제 발생 가능)
                        // cleanupSession(session); // 직접 호출 시 동시성 문제 주의, 별도 처리 권장
                    }
                } else {
                    // 세션이 닫혔으면 구독 목록에서 제거 (반복자 외부에서 처리하거나, 안전한 방식으로)
                    // 이 부분은 afterConnectionClosed에서 주로 처리되지만, 여기서도 감지 가능
                    logger.warn("Session {} for station {} was closed but still in subscription list during broadcast. Will be cleaned up.", session.getId(), stationId);
                }
            }
        }
    }


    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {} (ID: {}): {}",
                session.getRemoteAddress(), session.getId(), exception.getMessage(), exception);
        cleanupSession(session); // 전송 오류 시 세션 정리
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket connection closed: {} (ID: {}) - Status: {}",
                session.getRemoteAddress(), session.getId(), status);
        cleanupSession(session); // 연결 종료 시 세션 정리
    }

    // 세션과 관련된 모든 구독 정보 정리
    private void cleanupSession(WebSocketSession session) {
        Set<String> subscribedStationsForSession = sessionSubscriptions.remove(session);
        if (subscribedStationsForSession != null) {
            for (String stationId : subscribedStationsForSession) {
                Set<WebSocketSession> sessionsForStation = stationSubscriptions.get(stationId);
                if (sessionsForStation != null) {
                    sessionsForStation.remove(session);
                    if (sessionsForStation.isEmpty()) {
                        stationSubscriptions.remove(stationId);
                        logger.info("Removed stationId {} from subscriptions as no sessions are listening.", stationId);
                    }
                }
            }
        }
        logger.info("Cleaned up all subscriptions for closed/error session: {}", session.getId());
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

    /**
     * 현재 구독 중인 모든 고유한 정류소 ID 목록을 반환합니다.
     * @return 구독 중인 정류소 ID의 Set
     */
    public Set<String> getSubscribedStationIds() {
        // stationSubscriptions의 키셋 자체가 현재 구독 중인 모든 고유한 정류소 ID 목록임
        return Collections.unmodifiableSet(stationSubscriptions.keySet());
    }
}