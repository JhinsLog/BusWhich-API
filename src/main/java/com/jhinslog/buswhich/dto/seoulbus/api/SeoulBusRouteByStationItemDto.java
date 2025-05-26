package com.jhinslog.buswhich.dto.seoulbus.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) //Json 데이터와 일치하지 않는 필드 무시
public class SeoulBusRouteByStationItemDto {
    private String busRouteId;    // 노선ID
    private String busRouteNm;    // 노선명
    private String busRouteType;  // 노선유형
    private String term;          // 배차간격 (분)
    private String firstBusTm;    // 금일첫차시간
    private String lastBusTm;     // 금일막차시간
}
