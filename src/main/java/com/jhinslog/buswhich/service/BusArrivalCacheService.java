package com.jhinslog.buswhich.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhinslog.buswhich.domain.cache.CachedStationArrival;
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.repository.cache.CachedStationArrivalRepository;
import com.jhinslog.buswhich.util.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class BusArrivalCacheService {

    private static final Logger logger = LoggerFactory.getLogger(BusArrivalCacheService.class);
    private static final ApiOperation TARGET_API_OPERATION = ApiOperation.GET_ARR_INFO_BY_ST_ID_LIST; //정류소 ID 기반 일반 버스 도착정보

    private final CachedStationArrivalRepository cacheRepository;
    private final BusArrivalService actualBusArrivalService;
    private final ObjectMapper objectMapper;
    private final ApiCallManager apiCallManager;


    public static final long CACHE_INTERNAL_TTL_MINUTES = 5; //테스트 코드에서 접근하기 위해 public으로 수정

    @Autowired
    public BusArrivalCacheService(CachedStationArrivalRepository cacheRepository,
                                  @Qualifier("seoulBusArrivalServiceImpl") BusArrivalService actualBusArrivalService,
                                  ObjectMapper objectMapper,
                                  ApiCallManager apiCallManager) {
        this.cacheRepository = cacheRepository;
        this.actualBusArrivalService = actualBusArrivalService;
        this.objectMapper = objectMapper;
        this.apiCallManager = apiCallManager;
    }

    @Transactional
    public List<SpecificStationArrivalDto> getArrivalsForStation(String stationId, boolean forceApiRefresh) {
        Optional<CachedStationArrival> cachedOpt = cacheRepository.findByStationId(stationId);
        LocalDateTime now = LocalDateTime.now();

        // 1. 강제 새로고침이 아닌 유효한 캐시가 존재할 경우 캐시 반환.
        if (!forceApiRefresh && cachedOpt.isPresent() && cachedOpt.get().getCacheExpiryTime().isAfter(now)) {
            logger.debug("Cache hit for stationId: {}. Returning cached data valid until {}.",
                    stationId, cachedOpt.get().getCacheExpiryTime());
            return deserializeArrivalData(cachedOpt.get().getArrivalDataJson());
        }

        // 2. 캐시 미스, 만료, 강제 새로고침 시 API 호출 시도
        // (첫 번째 if 문을 통과했다면, 이 지점은 API 호출을 고려해야 하는 상황임)
        logger.info("Cache miss, expired, or force refresh for stationId: {}. Attempting API call.", stationId);

        // API 호출 제한 확인
        if (!apiCallManager.canMakeCall(TARGET_API_OPERATION)) {
            logger.warn("API call limit reached for {}. Cannot refresh cache for stationId: {}.",
                    TARGET_API_OPERATION, stationId);
            // 호출 제한 시, 만료된 캐시라도 있으면 반환, 없으면 빈 리스트 반환
            return cachedOpt.map(c -> deserializeArrivalData(c.getArrivalDataJson())).orElse(Collections.emptyList());
        }

        try {
            List<SpecificStationArrivalDto> freshData = actualBusArrivalService.getArrivalsByStationId(stationId);

            apiCallManager.recordCall(TARGET_API_OPERATION);    //API 호출 성공 시, ApiCallManager를 통해 호출 횟수 카운트 증가

            if (freshData != null) {
                String jsonData = serializeArrivalData(freshData);
                LocalDateTime apiCallTime = LocalDateTime.now();
                CachedStationArrival newCache = new CachedStationArrival(
                        stationId,
                        jsonData,
                        apiCallTime,
                        apiCallTime.plusMinutes(CACHE_INTERNAL_TTL_MINUTES)
                );
                cacheRepository.save(newCache);
                logger.info("API data fetched and cached for stationId: {}. Cache expires at: {}",
                        stationId, newCache.getCacheExpiryTime());
                return freshData;
            } else {
                logger.info("API returned no data for stationId: {}. Returning empty list.", stationId);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Error fetching fresh data from API for stationId: {}. Returning stale cache if available.",
                    stationId, e);
            return cachedOpt.map(c -> deserializeArrivalData(c.getArrivalDataJson())).orElse(Collections.emptyList());
        }
        // 이 지점은 도달하지 않으므로 추가적인 return이나 로직이 필요 없습니다.
    }

    private String serializeArrivalData(List<SpecificStationArrivalDto> data) {
        if (data == null) { // null인 경우 빈 배열 문자열로 처리
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing arrival data for caching", e);
            return "[]";
        }
    }

    private List<SpecificStationArrivalDto> deserializeArrivalData(String jsonData) {
        if (jsonData == null || jsonData.isEmpty() || "[]".equals(jsonData)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(jsonData, new TypeReference<List<SpecificStationArrivalDto>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing cached arrival data", e);
            return Collections.emptyList();
        }
    }
}