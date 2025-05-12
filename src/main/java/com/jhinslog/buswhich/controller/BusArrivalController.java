package com.jhinslog.buswhich.controller;

import com.jhinslog.buswhich.dto.response.RouteAllArrivalsResponseDto;
import com.jhinslog.buswhich.service.BusArrivalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/bus-arrival")
@RequiredArgsConstructor
public class BusArrivalController {

    private final BusArrivalService busArrivalService; // 인터페이스 타입으로 주입

    @GetMapping("/route/{busRouteId}")
    public ResponseEntity<RouteAllArrivalsResponseDto> getArrivalsByRoute(
            @PathVariable String busRouteId
    ) {
        log.info("Request received for busRouteId: {}", busRouteId);
        RouteAllArrivalsResponseDto responseDto = busArrivalService.getArrivalsByRoute(busRouteId);
        if (responseDto.getStationArrivals() == null || responseDto.getStationArrivals().isEmpty()) {
            if ("정보 없음".equals(responseDto.getRouteName()) || "API 응답 없음".equals(responseDto.getRouteName()) || responseDto.getRouteName().startsWith("API 오류") || responseDto.getRouteName().startsWith("JSON 파싱 오류") || responseDto.getRouteName().startsWith("처리 중 오류 발생")) {
                log.warn("No arrival info or error for busRouteId: {}. Response: {}", busRouteId, responseDto.getRouteName());
                // 오류 상황이나 정보 없는 경우, 그대로 반환하거나 적절한 HTTP 상태 코드로 응답할 수 있습니다.
                // 여기서는 일단 성공(200 OK)으로 반환하고 클라이언트가 내용을 판단하도록 합니다.
            } else {
                log.info("No arrival items found for busRouteId: {}, but route info might exist.", busRouteId);
            }
        }
        return ResponseEntity.ok(responseDto);
    }
}
   