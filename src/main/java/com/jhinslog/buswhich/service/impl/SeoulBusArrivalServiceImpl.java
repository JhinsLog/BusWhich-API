package com.jhinslog.buswhich.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhinslog.buswhich.dto.response.RouteAllArrivalsResponseDto;
import com.jhinslog.buswhich.dto.response.StationSpecificArrivalDto;
import com.jhinslog.buswhich.dto.seoulbus.api.SeoulBusArrivalItemDto;
import com.jhinslog.buswhich.dto.seoulbus.api.SeoulBusMsgBodyDto;
import com.jhinslog.buswhich.dto.seoulbus.api.SeoulBusMsgHeaderDto;
import com.jhinslog.buswhich.dto.seoulbus.api.SeoulBusResponseDto;
import com.jhinslog.buswhich.service.BusArrivalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeoulBusArrivalServiceImpl implements BusArrivalService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${public-api.seoul-bus.service-key}")
    private String serviceKey;

    // application-dev.yml에 정의된 operation URL
    @Value("${public-api.seoul-bus.operations.getArrInfoByRouteAllList}")
    private String arrInfoByRouteAllListUrl;    // 경유노선 전체 정류소 도착예정정보를 조회한다

    @Value("${public-api.seoul-bus.operations.getArrInfoByRouteList}")
    private String arrInfoByRouteListUrl;       // 한 정류소의 특정노선의 도착예정정보를 조회한다

    @Value("${public-api.seoul-bus.operations.getLowArrInfoByRouteList}")
    private String lowArrInfoByRouteListUrl;    // 정류소ID로 저상버스 도착예정정보를 조회한다

    @Value("${public-api.seoul-bus.operations.getLowArrInfoByStIdList}")
    private String lowArrInfoByStIdListUrl;     // 한 정류소의 특정노선의 저상버스 도착예정정보를 조회한다

    public SeoulBusArrivalServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RouteAllArrivalsResponseDto getArrivalsByRoute(String busRouteId) {

        // 1. API URL 구성
        URI apiUri = UriComponentsBuilder.fromHttpUrl(arrInfoByRouteAllListUrl)
                .queryParam("serviceKey", serviceKey)
                .queryParam("busRouteId", busRouteId)
                .queryParam("resultType", "json") // JSON 형태로 데이터 요청
                .build(true) // serviceKey가 이미 인코딩된 값일 경우 true
                .toUri();

        log.info("Requesting API URL (JSON): {}", apiUri.toString());

        try {
            // 2. API 호출 및 JSON 응답을 문자열로 받기
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUri, String.class);
            String jsonResponse = responseEntity.getBody();

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                log.error("API response is null or empty for busRouteId: {}", busRouteId);
                return buildErrorResponse(busRouteId, "API 응답 없음");
            }

            log.debug("JSON Response for busRouteId {}: {}", busRouteId, jsonResponse);

            // 3. JSON 문자열을 DTO로 변환 (SeoulBusResponseDto<SeoulBusArrivalItemDto> 사용)
            TypeReference<SeoulBusResponseDto<SeoulBusArrivalItemDto>> typeRef =
                    new TypeReference<SeoulBusResponseDto<SeoulBusArrivalItemDto>>() {};
            SeoulBusResponseDto<SeoulBusArrivalItemDto> apiResponse = objectMapper.readValue(jsonResponse, typeRef);

            // 4. API 응답 헤더 유효성 검사
            if (apiResponse == null || apiResponse.getMsgHeader() == null) {
                log.error("Failed to parse API response or msgHeader is null for busRouteId: {}", busRouteId);
                return buildErrorResponse(busRouteId, "API 응답 파싱 실패");
            }

            SeoulBusMsgHeaderDto msgHeader = apiResponse.getMsgHeader();
            if (!"0".equals(msgHeader.getHeaderCd())) { // "0"이 성공 코드
                log.error("API error for busRouteId: {}. HeaderCd: {}, HeaderMsg: {}",
                        busRouteId, msgHeader.getHeaderCd(), msgHeader.getHeaderMsg());
                return buildErrorResponse(busRouteId, "API 오류: " + msgHeader.getHeaderMsg());
            }

            // 5. itemList 추출
            SeoulBusMsgBodyDto<SeoulBusArrivalItemDto> msgBody = apiResponse.getMsgBody();
            if (msgBody == null || msgBody.getItemList() == null || msgBody.getItemList().isEmpty()) {
                log.info("No arrival information found for busRouteId: {} (itemList is null or empty)", busRouteId);
                // 아이템이 없는 경우, 노선 정보 없이 빈 도착 정보 리스트 반환
                return RouteAllArrivalsResponseDto.builder()
                        .routeId(busRouteId)
                        .routeName("정보 없음") // 또는 API에서 노선명을 별도로 가져오는 로직 추가 필요
                        .routeType("정보 없음")
                        .stationArrivals(Collections.emptyList())
                        .build();
            }

            List<SeoulBusArrivalItemDto> arrivalItems = msgBody.getItemList();

            // 6. RouteAllArrivalsResponseDto로 변환
            // 첫 번째 아이템에서 노선명과 노선 유형 추출 (모든 아이템이 동일 노선이라고 가정)
            String routeName = arrivalItems.get(0).getBusRouteAbrv(); // 안내용 노선 약칭 사용
            if (routeName == null || routeName.trim().isEmpty()) {
                routeName = arrivalItems.get(0).getRtNm(); // DB 관리용 노선명 사용
            }
            String routeTypeApi = arrivalItems.get(0).getRouteType();

            List<StationSpecificArrivalDto> stationArrivals = arrivalItems.stream()
                    .map(this::transformToStationSpecificArrivalDto)
                    .collect(Collectors.toList());

            return RouteAllArrivalsResponseDto.builder()
                    .routeId(busRouteId) // 입력받은 busRouteId 사용
                    .routeName(routeName)
                    .routeType(formatRouteType(routeTypeApi)) // 코드값을 문자열로 변환
                    .stationArrivals(stationArrivals)
                    .build();

        } catch (IOException e) { // ObjectMapper.readValue()는 IOException을 던질 수 있음
            log.error("Error parsing JSON response for busRouteId: {}", busRouteId, e);
            return buildErrorResponse(busRouteId, "JSON 파싱 오류: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing bus arrival info for busRouteId: {}", busRouteId, e);
            return buildErrorResponse(busRouteId, "처리 중 오류 발생: " + e.getMessage());
        }
    }

    private RouteAllArrivalsResponseDto buildErrorResponse(String busRouteId, String errorMessage) {
        return RouteAllArrivalsResponseDto.builder()
                .routeId(busRouteId)
                .routeName(errorMessage)
                .routeType("오류")
                .stationArrivals(Collections.emptyList())
                .build();
    }

    // SeoulBusArrivalItemDto -> StationSpecificArrivalDto 변환 헬퍼 메소드
    private StationSpecificArrivalDto transformToStationSpecificArrivalDto(SeoulBusArrivalItemDto item) {
        return StationSpecificArrivalDto.builder()
                .stationId(item.getStId())
                .stationName(item.getStNm())
                .arsId(item.getArsId())
                .stationOrder(item.getStaOrd())
                .direction(item.getDir())
                // 첫 번째 버스 정보
                .firstArrivalMsg(item.getArrmsg1())
                .firstRemainingSec(parseIntegerSafe(item.getExps1())) // exps1: 지수평활 도착예정시간(초)
                .firstBusType(formatBusType(item.getBusType1()))
                .firstPlainNo(item.getPlainNo1())
                .firstIsLowFloor(isLowFloor(item.getBusType1()))
                .firstCongestion(formatCongestion(item.getBrerdeDiv1(), item.getBrdrdeNum1(), item.getRouteType()))
                .firstIsLastBus(isLastBus(item.getIsLast1()))
                // 두 번째 버스 정보
                .secondArrivalMsg(item.getArrmsg2())
                .secondRemainingSec(parseIntegerSafe(item.getExps2()))
                .secondBusType(formatBusType(item.getBusType2()))
                .secondPlainNo(item.getPlainNo2())
                .secondIsLowFloor(isLowFloor(item.getBusType2()))
                .secondCongestion(formatCongestion(item.getBrerdeDiv2(), item.getBrdrdeNum2(), item.getRouteType()))
                .secondIsLastBus(isLastBus(item.getIsLast2()))
                // 우회 여부
                .detourYn("11".equals(item.getDeTourAt())) // "00": 정상, "11": 우회
                .build();
    }

    // --- 데이터 가공을 위한 헬퍼 메소드들 ---
    private Integer parseIntegerSafe(String value) {
        if (value == null || value.trim().isEmpty() || "0".equals(value)) {
            // "0"은 실제 0초일 수도 있고, 데이터 없음을 의미할 수도 있음. API 명세에 따라 해석.
            // 여기서는 도착 예정 시간이므로 0은 곧 도착 또는 정보 없음으로 간주될 수 있음. null로 반환하여 구분.
            return null;
        }
        try {
            // 숫자만 있는지 간단히 확인 (더 엄밀한 검증은 정규식 사용 가능)
            if (!value.matches("\\d+")) {
                log.warn("Non-numeric value encountered for time: '{}'", value);
                return null;
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Could not parse integer from string: '{}'", value, e);
            return null;
        }
    }

    // 버스 타입 정보
    private String formatBusType(String busTypeCode) {
        if (busTypeCode == null) return "정보없음";
        return switch (busTypeCode) {
            case "0" -> "일반버스";
            case "1" -> "저상버스";
            case "2" -> "굴절버스";
            default -> "기타(" + busTypeCode + ")";
        };
    }

    private Boolean isLowFloor(String busTypeCode) {
        return "1".equals(busTypeCode); // 1: 저상버스
    }

    // 혼잡도 변환 (brerdeDiv, brdrdeNum 사용)
    private String formatCongestion(String divCode, String numCode, String routeType) {
        if (divCode == null || numCode == null) return "정보없음";

        // brerde_Div1/2: brdrde_Num1/2 값의 의미 구분(0: 데이터 없음, 2: 재차인원, 4:혼잡도)
        // brdrde_Num1/2: 재차구분 4일 때 혼잡도(0: 데이터없음, 3: 여유, 4: 보통, 5: 혼잡)
        //                재차구분 2일 때 재차인원 또는 잔여좌석수(routeType = 6) 서울시 광역버스
        switch (divCode) {
            case "4": // 혼잡도
                return switch (numCode) {
                    case "3" -> "여유";
                    case "4" -> "보통";
                    case "5" -> "혼잡";
                    default -> "정보없음"; // "0" 또는 기타 값
                };
            case "2": // 재차인원 또는 잔여좌석
                if ("6".equals(routeType)) { // 광역버스
                    return "잔여좌석: " + numCode;
                } else {
                    return "재차인원: " + numCode;
                }
            default: // "0" 또는 기타 값
                return "정보없음";
        }
    }

    //막차 여부
    private Boolean isLastBus(String isLastCode) {
        return "1".equals(isLastCode); // 0:막차아님, 1:막차
    }

    //노선 유형
    private String formatRouteType(String routeTypeCode) {
        if (routeTypeCode == null) return "정보없음";
        return switch (routeTypeCode) {
            case "1" -> "공항버스";
            case "2" -> "마을버스";
            case "3" -> "간선버스";
            case "4" -> "지선버스";
            case "5" -> "순환버스";
            case "6" -> "광역버스";
            case "7" -> "인천버스";
            case "8" -> "경기버스";
            case "9" -> "폐지";
            case "0" -> "공용";
            default -> "기타(" + routeTypeCode + ")";
        };
    }

}
