package com.jhinslog.buswhich.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecificStationArrivalDto {
    private String stationId;           // 정류소 ID (stId)
    private String stationName;         // 정류소명 (stNm)
    private String arsId;               // 정류소 번호 (arsId)
    private String stationOrder;        // 해당 노선에서의 정류소 순번 (staOrd)
    private String direction;           // 진행 방향 (dir)

    private String firstArrivalMsg;     // 첫 번째 버스 도착 메시지 (arrmsg1)
    private Integer firstRemainingSec;  // 첫 번째 버스 남은 시간(초) (traTime1 또는 exps1 등에서 변환)
    private String firstBusType;        // 첫 번째 버스 타입 (busType1 -> "저상", "일반" 등으로 변환)
    private String firstPlainNo;        // 첫 번째 버스 차량 번호 (plainNo1)
    private Boolean firstIsLowFloor;     // 첫 번째 버스 저상 여부
    private String firstCongestion;     // 첫 번째 버스 혼잡도 (reride_Num1, rerdie_Div1 참고하여 변환)

    private String secondArrivalMsg;    // 두 번째 버스 도착 메시지 (arrmsg2)
    private Integer secondRemainingSec; // 두 번째 버스 남은 시간(초)
    private String secondBusType;       // 두 번째 버스 타입
    private String secondPlainNo;       // 두 번째 버스 차량 번호
    private Boolean secondIsLowFloor;    // 두 번째 버스 저상 여부
    private String secondCongestion;    // 두 번째 버스 혼잡도

    // 필요에 따라 추가 정보 (예: 막차 여부, 우회 여부 등)
    private Boolean firstIsLastBus;      // 첫 번째 버스 막차 여부 (isLast1)
    private Boolean secondIsLastBus;     // 두 번째 버스 막차 여부 (isLast2)
    private Boolean detourYn;            // 우회 여부 (deTourAt)
}