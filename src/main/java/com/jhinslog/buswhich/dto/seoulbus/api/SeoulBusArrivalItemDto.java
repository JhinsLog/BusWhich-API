package com.jhinslog.buswhich.dto.seoulbus.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeoulBusArrivalItemDto {

    @JsonProperty("arrmsg1")
    private String arrmsg1; // 첫번째 도착예정 버스의 도착정보메시지 (예: "출발대기", "5분후[3번째 전]", "곧 도착")

    @JsonProperty("arrmsg2")
    private String arrmsg2; // 두번째 도착예정 버스의 도착정보메시지

    @JsonProperty("arsId")
    private String arsId; // 정류소 번호 (ARS ID)

    @JsonProperty("avgCf1")
    private String avgCf1; // 첫번째 도착예정 버스의 이동평균 보정계수 (보통 숫자형 문자열)

    @JsonProperty("avgCf2")
    private String avgCf2; // 두번째 도착예정 버스의 이동평균 보정계수 (보통 숫자형 문자열)

    @JsonProperty("brdrde_Num1") // 재차구분 4일 때 혼잡도(0: 데이터없음, 3: 여유, 4: 보통, 5: 혼잡) 재차구분 2일 때 재차인원 또는 잔여좌석수(routeType = 6) 서울시 광역버스
    private String brdrdeNum1;

    @JsonProperty("brdrde_Num2") // 재차구분 4일 때 혼잡도(0: 데이터없음, 3: 여유, 4: 보통, 5: 혼잡) 재차구분 2일 때 재차인원 또는 잔여좌석수(routeType = 6) 서울시 광역버스
    private String brdrdeNum2;

    @JsonProperty("brerde_Div1") // brdrde_Num1 값의 의미 구분(0: 데이터 없음, 2: 재차인원, 4:혼잡도)
    private String brerdeDiv1;

    @JsonProperty("brerde_Div2") // brdrde_Num2 값의 의미 구분(0: 데이터 없음, 2: 재차인원, 4:혼잡도)
    private String brerdeDiv2;

    @JsonProperty("busRouteAbrv")
    private String busRouteAbrv; // 노선 약칭 (예: "753", "5515")

    @JsonProperty("busRouteId")
    private String busRouteId; // 노선ID (예: "100100118")

    @JsonProperty("busType1")
    private String busType1; // 첫번째도착예정버스의 차량유형 (0:일반버스, 1:저상버스, 2:굴절버스)

    @JsonProperty("busType2")
    private String busType2; // 두번째도착예정버스의 차량유형 (0:일반버스, 1:저상버스, 2:굴절버스)

    @JsonProperty("deTourAt")
    private String deTourAt; // 해당노선 우회여부(00: 정상, 11: 우회)

    @JsonProperty("dir")
    private String dir; // 방향 (예: "상암월드컵파크7단지")

    @JsonProperty("expCf1")
    private String expCf1; // 첫번째 도착예정 버스의 지수평활 보정계수

    @JsonProperty("expCf2")
    private String expCf2; // 두번째 도착예정 버스의 지수평활 보정계수

    @JsonProperty("exps1")
    private String exps1; // 첫번째 도착예정 버스의 지수평활 도착예정시간(초) (숫자형 문자열)

    @JsonProperty("exps2")
    private String exps2; // 두번째 도착예정 버스의 지수평활 도착예정시간(초) (숫자형 문자열)

    @JsonProperty("firstTm")
    private String firstTm; // 첫차시간 (예: "20230927041000")

    @JsonProperty("full1")
    private String full1; // 첫번째 도착예정 버스의 만차여부 (0 또는 1 등으로 예상, 명세에 상세값 없음)

    @JsonProperty("full2")
    private String full2; // 두번째 도착예정 버스의 만차여부

    @JsonProperty("goal1")
    private String goal1; // 첫번째 도착예정 버스의 종점 도착예정시간(초)

    @JsonProperty("goal2")
    private String goal2; // 두번째 도착예정 버스의 종점 도착예정시간(초)

    @JsonProperty("isArrive1")
    private String isArrive1; // 첫번째도착예정버스의 최종 정류소 도착출발여부 (0:운행중, 1:도착)

    @JsonProperty("isArrive2")
    private String isArrive2; // 두번째도착예정버스의 최종 정류소 도착출발여부 (0:운행중, 1:도착)

    @JsonProperty("isLast1")
    private String isLast1; // 첫번째도착예정버스의 막차여부 (0:막차아님, 1:막차)

    @JsonProperty("isLast2")
    private String isLast2; // 두번째도착예정버스의 막차여부 (0:막차아님, 1:막차)

    @JsonProperty("kalCf1")
    private String kalCf1; // 첫번째 도착예정 버스의 기타1평균 보정계수

    @JsonProperty("kalCf2")
    private String kalCf2; // 두번째 도착예정 버스의 기타1평균 보정계수

    @JsonProperty("kals1")
    private String kals1; // 첫번째 도착예정 버스의 기타1 도착예정시간(초)

    @JsonProperty("kals2")
    private String kals2; // 두번째 도착예정 버스의 기타1 도착예정시간(초)

    @JsonProperty("lastTm")
    private String lastTm; // 막차시간 (예: "20230927222000")

    @JsonProperty("mkTm")
    private String mkTm; // 제공시각 (예: "2023-09-27 16:51:36.0")

    @JsonProperty("namin2Sec1")
    private String namin2Sec1; // 첫번째 도착예정 버스의 2번째 주요정류소 예정여행시간

    @JsonProperty("namin2Sec2")
    private String namin2Sec2; // 두번째 도착예정 버스의 2번째 주요정류소 예정여행시간

    @JsonProperty("neuCf1")
    private String neuCf1; // 첫번째 도착예정 버스의 기타2평균 보정계수

    @JsonProperty("neuCf2")
    private String neuCf2; // 두번째 도착예정 버스의 기타2평균 보정계수

    @JsonProperty("neus1")
    private String neus1; // 첫번째 도착예정 버스의 기타2 도착예정시간(초)

    @JsonProperty("neus2")
    private String neus2; // 두번째 도착예정 버스의 기타2 도착예정시간(초)

    @JsonProperty("nextBus") // 명세에 따라 nextBus로 수정
    private String nextBus; // 막차운행여부 (N:막차아님, Y:막차)

    @JsonProperty("nmain2Ord1")
    private String nmain2Ord1; // 첫번째 도착예정 버스의 2번째 주요정류소 순번

    @JsonProperty("nmain2Ord2")
    private String nmain2Ord2; // 두번째 도착예정 버스의 2번째 주요정류소 순번

    @JsonProperty("nmain2Stnid1")
    private String nmain2Stnid1; // 첫번째 도착예정 버스의 2번째 주요정류소 ID

    @JsonProperty("nmain2Stnid2")
    private String nmain2Stnid2; // 두번째 도착예정 버스의 2번째 주요정류소 ID

    @JsonProperty("nmain3Ord1")
    private String nmain3Ord1; // 첫번째 도착예정 버스의 3번째 주요정류소 순번

    @JsonProperty("nmain3Ord2")
    private String nmain3Ord2; // 두번째 도착예정 버스의 3번째 주요정류소 순번

    @JsonProperty("nmain3Sec1")
    private String nmain3Sec1; // 첫번째 도착예정 버스의 3번째 주요정류소 예정여행시간

    @JsonProperty("nmain3Sec2")
    private String nmain3Sec2; // 두번째 도착예정 버스의 3번째 주요정류소 예정여행시간

    @JsonProperty("nmain3Stnid1")
    private String nmain3Stnid1; // 첫번째 도착예정 버스의 3번째 주요정류소 ID

    @JsonProperty("nmain3Stnid2")
    private String nmain3Stnid2; // 두번째 도착예정 버스의 3번째 주요정류소 ID

    @JsonProperty("nmainOrd1")
    private String nmainOrd1; // 첫번째 도착예정 버스의 1번째 주요정류소 순번

    @JsonProperty("nmainOrd2")
    private String nmainOrd2; // 두번째 도착예정 버스의 1번째 주요정류소 순번

    @JsonProperty("nmainSec1")
    private String nmainSec1; // 첫번째 도착예정 버스의 1번째 주요정류소 예정여행시간

    @JsonProperty("nmainSec2")
    private String nmainSec2; // 두번째 도착예정 버스의 1번째 주요정류소 예정여행시간

    @JsonProperty("nmainStnid1")
    private String nmainStnid1; // 첫번째 도착예정 버스의 1번째 주요정류소 ID

    @JsonProperty("nmainStnid2")
    private String nmainStnid2; // 두번째 도착예정 버스의 1번째 주요정류소 ID

    @JsonProperty("nstnId1")
    private String nstnId1; // 첫번째 도착예정 버스의 다음정류소 ID

    @JsonProperty("nstnId2")
    private String nstnId2; // 두번째 도착예정 버스의 다음정류소 ID

    @JsonProperty("nstnOrd1")
    private String nstnOrd1; // 첫번째 도착예정 버스의다음 정류소 순번

    @JsonProperty("nstnOrd2")
    private String nstnOrd2; // 두번째 도착예정 버스의다음 정류소 순번

    @JsonProperty("nstnSec1")
    private String nstnSec1; // 첫번째 도착예정 버스의 다음 정류소 예정여행시간(초)

    @JsonProperty("nstnSec2")
    private String nstnSec2; // 두번째 도착예정 버스의 다음 정류소 예정여행시간(초)

    @JsonProperty("nstnSpd1")
    private String nstnSpd1; // 첫번째 도착예정 버스의 다음 정류소까지의 평균속도

    @JsonProperty("nstnSpd2")
    private String nstnSpd2; // 두번째 도착예정 버스의 다음 정류소까지의 평균속도

    @JsonProperty("plainNo1")
    private String plainNo1; // 첫번째도착예정차량번호 (예: "서울70사1234")

    @JsonProperty("plainNo2")
    private String plainNo2; // 두번째도착예정차량번호

    @JsonProperty("rerdie_Div1")
    private String rerdieDiv1; // 첫번째 도착예정 버스내부 제공용 현재 재차 구분(0:데이터없음, 2:재차인원, 4:혼잡도)

    @JsonProperty("rerdie_Div2")
    private String rerdieDiv2; // 두번째 도착예정 버스내부 제공용 현재 재차 구분

    @JsonProperty("reride_Num1")
    private String rerideNum1; // 첫번째 도착예정 버스내부 제공용 현재 재차 인원 또는 혼잡도 (rerdie_Div1 값에 따라 해석)

    @JsonProperty("reride_Num2")
    private String rerideNum2; // 두번째 도착예정 버스내부 제공용 현재 재차 인원 또는 혼잡도

    @JsonProperty("routeType")
    private String routeType; // 노선유형 (1:공항, 2:마을, 3:간선, 4:지선, 5:순환, 6:광역, 7:인천, 8:경기, 9:폐지, 0:공용)

    @JsonProperty("rtNm")
    private String rtNm; // 노선명 (DB관리용, 예: "753")

    @JsonProperty("sectOrd1")
    private String sectOrd1; // 첫번째도착예정버스의 현재구간 순번

    @JsonProperty("sectOrd2")
    private String sectOrd2; // 두번째도착예정버스의 현재구간 순번

    @JsonProperty("stId")
    private String stId; // 정류소 고유 ID (예: "111000299")

    @JsonProperty("stNm")
    private String stNm; // 정류소명 (예: "구상동사거리")

    @JsonProperty("staOrd")
    private String staOrd; // 요청정류소순번 (해당 노선에서 이 정류소가 몇 번째 정류소인지)

    @JsonProperty("term")
    private String term; // 배차간격 (분) (숫자형 문자열)

    @JsonProperty("traSpd1")
    private String traSpd1; // 첫번째도착예정버스의 현재 구간 여행속도 (Km/h)

    @JsonProperty("traSpd2")
    private String traSpd2; // 두번째도착예정버스의 현재 구간 여행속도

    @JsonProperty("traTime1")
    private String traTime1; // 첫번째도착예정버스의 현재 구간 여행시간 (분)

    @JsonProperty("traTime2")
    private String traTime2; // 두번째도착예정버스의 현재 구간 여행시간

    @JsonProperty("vehId1")
    private String vehId1; // 첫번째도착예정버스ID (내부 관리용 ID)

    @JsonProperty("vehId2")
    private String vehId2; // 두번째 도착예정버스ID
}