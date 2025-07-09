package com.jhinslog.buswhich.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhinslog.buswhich.dto.response.RouteAllArrivalsResponseDto;
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.dto.seoulbus.api.*;
import com.jhinslog.buswhich.service.ApiCallManager;
import com.jhinslog.buswhich.service.BusArrivalService;
import com.jhinslog.buswhich.util.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service("seoulBusArrivalServiceImpl")
public class SeoulBusArrivalServiceImpl implements BusArrivalService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiCallManager apiCallManager; // ApiCallManager 주입

    @Value("${public-api.seoul-bus.service-key}")
    private String serviceKey;

    @Value("${public-api.seoul-bus.operations.getArrInfoByRouteAllList}")
    private String arrInfoByRouteAllListUrl;

    @Value("${public-api.seoul-bus.operations.getRouteByStationList}")
    private String routeByStationListUrl;

    public SeoulBusArrivalServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiCallManager apiCallManager) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiCallManager = apiCallManager;
    }

    @Override
    public RouteAllArrivalsResponseDto getArrivalsByRoute(String busRouteId) {
        if (!apiCallManager.canMakeCall(ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST)) {
            log.warn("API call limit reached for {}. Aborting call for busRouteId: {}", ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST, busRouteId);
            return buildErrorResponse(busRouteId, "일일 API 호출 한도 초과 [" + ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST.getOperationName() + "]");
        }

        URI apiUri = UriComponentsBuilder.fromHttpUrl(arrInfoByRouteAllListUrl)
                .queryParam("serviceKey", serviceKey)
                .queryParam("busRouteId", busRouteId)
                .queryParam("resultType", "json")
                .build(true)
                .toUri();

        log.info("Requesting API URL (JSON): {}", apiUri.toString());

        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUri, String.class);
            apiCallManager.recordCall(ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST);
            String jsonResponse = responseEntity.getBody();

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                log.error("API response is null or empty for busRouteId: {}", busRouteId);
                return buildErrorResponse(busRouteId, "API 응답 없음");
            }

            log.debug("JSON Response for busRouteId {}: {}", busRouteId, jsonResponse);

            TypeReference<SeoulBusResponseDto<SeoulBusArrivalItemDto>> typeRef =
                    new TypeReference<SeoulBusResponseDto<SeoulBusArrivalItemDto>>() {};
            SeoulBusResponseDto<SeoulBusArrivalItemDto> apiResponse = objectMapper.readValue(jsonResponse, typeRef);

            if (apiResponse == null || apiResponse.getMsgHeader() == null) {
                log.error("Failed to parse API response or msgHeader is null for busRouteId: {}", busRouteId);
                return buildErrorResponse(busRouteId, "API 응답 파싱 실패");
            }

            SeoulBusMsgHeaderDto msgHeader = apiResponse.getMsgHeader();
            if (!"0".equals(msgHeader.getHeaderCd())) {
                log.error("API error for busRouteId: {}. HeaderCd: {}, HeaderMsg: {}",
                        busRouteId, msgHeader.getHeaderCd(), msgHeader.getHeaderMsg());
                return buildErrorResponse(busRouteId, "API 오류: " + msgHeader.getHeaderMsg());
            }

            SeoulBusMsgBodyDto<SeoulBusArrivalItemDto> msgBody = apiResponse.getMsgBody();
            if (msgBody == null || msgBody.getItemList() == null || msgBody.getItemList().isEmpty()) {
                log.info("No arrival information found for busRouteId: {} (itemList is null or empty)", busRouteId);
                return RouteAllArrivalsResponseDto.builder()
                        .routeId(busRouteId)
                        .routeName(null)
                        .routeType(null)
                        .stationArrivals(Collections.emptyList())
                        .build();
            }

            List<SeoulBusArrivalItemDto> arrivalItems = msgBody.getItemList();
            String routeName = arrivalItems.get(0).getBusRouteAbrv();
            if (routeName == null || routeName.trim().isEmpty()) {
                routeName = arrivalItems.get(0).getRtNm();
            }
            String routeTypeApi = arrivalItems.get(0).getRouteType();

            List<SpecificStationArrivalDto> stationArrivals = arrivalItems.stream()
                    .map(this::transformToStationSpecificArrivalDto)
                    .collect(Collectors.toList());

            return RouteAllArrivalsResponseDto.builder()
                    .routeId(busRouteId)
                    .routeName(routeName)
                    .routeType(formatRouteType(routeTypeApi))
                    .stationArrivals(stationArrivals)
                    .build();

        } catch (IOException e) {
            log.error("Error parsing JSON response for busRouteId: {}", busRouteId, e);
            return buildErrorResponse(busRouteId, "JSON 파싱 오류: " + e.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP Error {} for getArrInfoByRouteAllList for busRouteId {}. Response: {}", e.getStatusCode(), busRouteId, e.getResponseBodyAsString(), e);
            return buildErrorResponse(busRouteId, "API 통신 오류 (HTTP " + e.getStatusCode() + ")");
        } catch (RestClientException e) {
            log.error("Error calling API getArrInfoByRouteAllList for busRouteId {}: {}", busRouteId, e.getMessage());
            return buildErrorResponse(busRouteId, "API 호출 중 오류 발생: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing bus arrival info for busRouteId: {}", busRouteId, e);
            return buildErrorResponse(busRouteId, "처리 중 오류 발생: " + e.getMessage());
        }
    }

    @Override
    public List<SpecificStationArrivalDto> getArrivalsByStationId(String arsId) {
        List<String> busRouteIds = getBusRouteIdsForStation(arsId);

        if (busRouteIds.isEmpty()) {
            log.info("No bus routes found passing through station with arsId: {} or API limit reached for getRouteByStationList.", arsId);
            return Collections.emptyList();
        }

        List<SpecificStationArrivalDto> allArrivalsAtStation = new ArrayList<>();

        for (String busRouteId : busRouteIds) {
            if (!apiCallManager.canMakeCall(ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST)) {
                log.warn("API call limit reached for {}. Skipping route {} for station {}",
                        ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST, busRouteId, arsId);
                continue;
            }

            try {
                URI routeDetailApiUri = UriComponentsBuilder.fromHttpUrl(arrInfoByRouteAllListUrl)
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("busRouteId", busRouteId)
                        .queryParam("resultType", "json")
                        .build(true)
                        .toUri();

                log.debug("Requesting all stops for route {} to find info for arsId {}: {}", busRouteId, arsId, routeDetailApiUri);
                ResponseEntity<String> responseEntity = restTemplate.getForEntity(routeDetailApiUri, String.class);
                apiCallManager.recordCall(ApiOperation.GET_ARR_INFO_BY_ROUTE_ALL_LIST);
                String jsonResponse = responseEntity.getBody();

                if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                    log.warn("API response for getArrInfoByRouteAllList is null or empty for busRouteId: {}", busRouteId);
                    continue;
                }

                TypeReference<SeoulBusResponseDto<SeoulBusArrivalItemDto>> typeRef = new TypeReference<>() {};
                SeoulBusResponseDto<SeoulBusArrivalItemDto> apiResponse = objectMapper.readValue(jsonResponse, typeRef);

                if (apiResponse == null || apiResponse.getMsgHeader() == null || !"0".equals(apiResponse.getMsgHeader().getHeaderCd())) {
                    log.warn("API error or invalid response for getArrInfoByRouteAllList for busRouteId: {}. Header: {}",
                            busRouteId, apiResponse != null ? apiResponse.getMsgHeader() : "null");
                    continue;
                }

                SeoulBusMsgBodyDto<SeoulBusArrivalItemDto> msgBody = apiResponse.getMsgBody();
                if (msgBody != null && msgBody.getItemList() != null) {
                    msgBody.getItemList().stream()
                            .filter(item -> arsId.equals(item.getArsId()))
                            .findFirst()
                            .ifPresent(item -> allArrivalsAtStation.add(transformToStationSpecificArrivalDto(item)));
                }
            } catch (IOException e) {
                log.error("Error parsing JSON for getArrInfoByRouteAllList for busRouteId {}: {}", busRouteId, e.getMessage());
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.error("HTTP Error {} for getArrInfoByRouteAllList for busRouteId {}. Response: {}", e.getStatusCode(), busRouteId, e.getResponseBodyAsString(), e);
            } catch (RestClientException e) {
                log.error("Error calling API getArrInfoByRouteAllList for busRouteId {}: {}", busRouteId, e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error processing getArrInfoByRouteAllList for busRouteId {}: {}", busRouteId, e.getMessage(), e);
            }
        }
        return allArrivalsAtStation;
    }

    private List<String> getBusRouteIdsForStation(String arsId) {
        if (!apiCallManager.canMakeCall(ApiOperation.GET_ROUTE_BY_STATION_LIST)) {
            log.warn("API call limit reached for {}. Aborting call for arsId: {}", ApiOperation.GET_ROUTE_BY_STATION_LIST, arsId);
            return Collections.emptyList();
        }

        URI apiUri = UriComponentsBuilder.fromHttpUrl(routeByStationListUrl)
                .queryParam("serviceKey", serviceKey)
                .queryParam("arsId", arsId)
                .queryParam("resultType", "json")
                .build(true)
                .toUri();

        log.debug("Requesting routes for station (arsId: {}): {}", arsId, apiUri);
        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUri, String.class);
            apiCallManager.recordCall(ApiOperation.GET_ROUTE_BY_STATION_LIST);
            String jsonResponse = responseEntity.getBody();

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                log.warn("API response for getRouteByStationList is null or empty for arsId: {}", arsId);
                return Collections.emptyList();
            }

            TypeReference<SeoulBusResponseDto<SeoulBusRouteByStationItemDto>> typeRef = new TypeReference<>() {};
            SeoulBusResponseDto<SeoulBusRouteByStationItemDto> apiResponse = objectMapper.readValue(jsonResponse, typeRef);

            if (apiResponse == null || apiResponse.getMsgHeader() == null || !"0".equals(apiResponse.getMsgHeader().getHeaderCd())) {
                log.warn("API error or invalid response for getRouteByStationList for arsId: {}. Header: {}",
                        arsId, apiResponse != null ? apiResponse.getMsgHeader() : "null");
                return Collections.emptyList();
            }

            SeoulBusMsgBodyDto<SeoulBusRouteByStationItemDto> msgBody = apiResponse.getMsgBody();
            if (msgBody != null && msgBody.getItemList() != null) {
                return msgBody.getItemList().stream()
                        .map(SeoulBusRouteByStationItemDto::getBusRouteId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Error parsing JSON for getRouteByStationList for arsId {}: {}", arsId, e.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP Error {} for getRouteByStationList for arsId {}. Response: {}", e.getStatusCode(), arsId, e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            log.error("Error calling API getRouteByStationList for arsId {}: {}", arsId, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching routes for arsId {}: {}", arsId, e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    private RouteAllArrivalsResponseDto buildErrorResponse(String busRouteId, String errorMessage) {
        return RouteAllArrivalsResponseDto.builder()
                .routeId(busRouteId)
                .routeName(errorMessage)
                .routeType("오류")
                .stationArrivals(Collections.emptyList())
                .build();
    }

    private SpecificStationArrivalDto transformToStationSpecificArrivalDto(SeoulBusArrivalItemDto item) {
        return SpecificStationArrivalDto.builder()
                .stationId(item.getStId())
                .stationName(item.getStNm())
                .arsId(item.getArsId())
                .stationOrder(item.getStaOrd())
                .direction(item.getDir())
                .firstArrivalMsg(item.getArrmsg1())
                .firstRemainingSec(parseIntegerSafe(item.getExps1()))
                .firstBusType(formatBusType(item.getBusType1()))
                .firstPlainNo(item.getPlainNo1())
                .firstIsLowFloor(isLowFloor(item.getBusType1()))
                .firstCongestion(formatCongestion(item.getBrerdeDiv1(), item.getBrdrdeNum1(), item.getRouteType()))
                .firstIsLastBus(isLastBus(item.getIsLast1()))
                .secondArrivalMsg(item.getArrmsg2())
                .secondRemainingSec(parseIntegerSafe(item.getExps2()))
                .secondBusType(formatBusType(item.getBusType2()))
                .secondPlainNo(item.getPlainNo2())
                .secondIsLowFloor(isLowFloor(item.getBusType2()))
                .secondCongestion(formatCongestion(item.getBrerdeDiv2(), item.getBrdrdeNum2(), item.getRouteType()))
                .secondIsLastBus(isLastBus(item.getIsLast2()))
                .detourYn("11".equals(item.getDeTourAt()))
                .build();
    }

    private Integer parseIntegerSafe(String value) {
        if (value == null || value.trim().isEmpty() || "0".equals(value)) {
            return null;
        }
        try {
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
        return "1".equals(busTypeCode);
    }

    private String formatCongestion(String divCode, String numCode, String routeType) {
        if (divCode == null || numCode == null) return "정보없음";
        switch (divCode) {
            case "4":
                return switch (numCode) {
                    case "3" -> "여유";
                    case "4" -> "보통";
                    case "5" -> "혼잡";
                    default -> "정보없음";
                };
            case "2":
                if ("6".equals(routeType)) {
                    return "잔여좌석: " + numCode;
                } else {
                    return "재차인원: " + numCode;
                }
            default:
                return "정보없음";
        }
    }

    private Boolean isLastBus(String isLastCode) {
        return "1".equals(isLastCode);
    }

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