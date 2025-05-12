package com.jhinslog.buswhich.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteAllArrivalsResponseDto {
    private String routeId;     // 노선 ID (busRouteId)
    private String routeName;   // 노선명 (rtNm 또는 busRouteAbrv)
    private String routeType;   // 노선 유형 (routeType)
    private List<StationSpecificArrivalDto> stationArrivals; // 해당 노선이 경유하는 정류소들의 도착 정보 목록
}
