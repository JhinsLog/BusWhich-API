package com.jhinslog.buswhich.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhinslog.buswhich.domain.cache.CachedStationArrival;
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.repository.cache.CachedStationArrivalRepository;
import com.jhinslog.buswhich.util.ApiOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class BusArrivalCacheServiceTest {

    private static final ApiOperation TARGET_API_OPERATION = ApiOperation.GET_ARR_INFO_BY_ST_ID_LIST;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public BusArrivalService seoulBusArrivalServiceImpl(BusArrivalCacheServiceTest testInstance) {
            return testInstance.actualBusArrivalService;
        }

        @Bean
        @Primary
        public ApiCallManager apiCallManager(BusArrivalCacheServiceTest testInstance) {
            return testInstance.apiCallManager;
        }
    }

    @Autowired
    private BusArrivalCacheService busArrivalCacheService;

    @Autowired
    private CachedStationArrivalRepository cacheRepository;

    @Mock
    private BusArrivalService actualBusArrivalService;

    @Mock
    private ApiCallManager apiCallManager;

    @Autowired
    private ObjectMapper objectMapper;

    private final String TEST_STATION_ID_1 = "STATION_001";

    @BeforeEach
    void setUp() {
        cacheRepository.deleteAllInBatch();
        // reset은 @MockBean으로 생성된 모의 객체에도 동일하게 사용할 수 있습니다.
        reset(actualBusArrivalService, apiCallManager);
    }

    private SpecificStationArrivalDto createMockDto(String stationId, String routeName) {
        SpecificStationArrivalDto dto = new SpecificStationArrivalDto();
        dto.setStationId(stationId);
        dto.setArsId(stationId);
        dto.setStationName("테스트정류소-" + stationId);
        dto.setRouteName(routeName);
        dto.setFirstArrivalMsg("3분후[1번째 전]");
        dto.setFirstCongestion("0");
        dto.setDetourYn(false);
        return dto;
    }

    @Test
    @DisplayName("캐시 히트: 유효한 캐시가 존재하면 API 호출 없이 캐시된 데이터를 반환한다")
    void getArrivalsForStation_whenCacheHit_shouldReturnCachedDataWithoutApiCall() throws JsonProcessingException {
        // given
        List<SpecificStationArrivalDto> cachedDtos = List.of(createMockDto(TEST_STATION_ID_1, "100번"));
        String jsonData = objectMapper.writeValueAsString(cachedDtos);
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(BusArrivalCacheService.CACHE_INTERNAL_TTL_MINUTES);
        CachedStationArrival cacheEntry = new CachedStationArrival(TEST_STATION_ID_1, jsonData, LocalDateTime.now().minusMinutes(1), expiryTime);
        cacheRepository.save(cacheEntry);

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getRouteName()).isEqualTo("100번");
        verify(actualBusArrivalService, never()).getArrivalsByStationId(anyString());
        verify(apiCallManager, never()).canMakeCall(any(ApiOperation.class));
        verify(apiCallManager, never()).recordCall(any(ApiOperation.class));
    }

    @Test
    @DisplayName("캐시 미스 (데이터 없음): 캐시가 없으면 API를 호출하고 결과를 캐시에 저장 후 반환한다")
    void getArrivalsForStation_whenCacheMiss_shouldCallApiSaveToCacheAndReturnFreshData() {
        // given
        List<SpecificStationArrivalDto> freshDtosFromApi = List.of(createMockDto(TEST_STATION_ID_1, "200번"));
        when(actualBusArrivalService.getArrivalsByStationId(TEST_STATION_ID_1)).thenReturn(freshDtosFromApi);
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(true); // API 호출 가능

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isEqualTo(freshDtosFromApi);
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, times(1)).getArrivalsByStationId(TEST_STATION_ID_1);
        verify(apiCallManager, times(1)).recordCall(TARGET_API_OPERATION); // 호출 기록

        Optional<CachedStationArrival> savedCache = cacheRepository.findByStationId(TEST_STATION_ID_1);
        assertThat(savedCache).isPresent();
        assertThat(savedCache.get().getArrivalDataJson()).isEqualToIgnoringWhitespace(serializeQuietly(freshDtosFromApi));
        assertThat(savedCache.get().getCacheExpiryTime()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("캐시 만료: 캐시가 만료되었으면 API를 호출하고 캐시를 갱신 후 새 데이터를 반환한다")
    void getArrivalsForStation_whenCacheExpired_shouldCallApiUpdateCacheAndReturnFreshData() throws JsonProcessingException {
        // given
        List<SpecificStationArrivalDto> expiredDtos = List.of(createMockDto(TEST_STATION_ID_1, "300번_만료됨"));
        String expiredJsonData = objectMapper.writeValueAsString(expiredDtos);
        LocalDateTime veryOldTime = LocalDateTime.now().minusHours(1);
        CachedStationArrival expiredCacheEntry = new CachedStationArrival(TEST_STATION_ID_1, expiredJsonData, veryOldTime, veryOldTime.plusMinutes(1));
        cacheRepository.save(expiredCacheEntry);

        List<SpecificStationArrivalDto> freshDtosFromApi = List.of(createMockDto(TEST_STATION_ID_1, "300번_갱신됨"));
        when(actualBusArrivalService.getArrivalsByStationId(TEST_STATION_ID_1)).thenReturn(freshDtosFromApi);
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(true);

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isEqualTo(freshDtosFromApi);
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, times(1)).getArrivalsByStationId(TEST_STATION_ID_1);
        verify(apiCallManager, times(1)).recordCall(TARGET_API_OPERATION);

        Optional<CachedStationArrival> updatedCache = cacheRepository.findByStationId(TEST_STATION_ID_1);
        assertThat(updatedCache).isPresent();
        assertThat(updatedCache.get().getArrivalDataJson()).isEqualToIgnoringWhitespace(serializeQuietly(freshDtosFromApi));
        assertThat(updatedCache.get().getCacheExpiryTime()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("강제 새로고침: 캐시 유효성과 관계없이 API를 호출하고 캐시를 갱신 후 새 데이터를 반환한다")
    void getArrivalsForStation_whenForceRefresh_shouldCallApiUpdateCacheAndReturnFreshData() throws JsonProcessingException {
        // given
        List<SpecificStationArrivalDto> existingCachedDtos = List.of(createMockDto(TEST_STATION_ID_1, "400번_기존캐시"));
        String existingJsonData = objectMapper.writeValueAsString(existingCachedDtos);
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(BusArrivalCacheService.CACHE_INTERNAL_TTL_MINUTES);
        CachedStationArrival cacheEntry = new CachedStationArrival(TEST_STATION_ID_1, existingJsonData, LocalDateTime.now().minusMinutes(1), expiryTime);
        cacheRepository.save(cacheEntry);

        List<SpecificStationArrivalDto> freshDtosFromApi = List.of(createMockDto(TEST_STATION_ID_1, "400번_강제갱신"));
        when(actualBusArrivalService.getArrivalsByStationId(TEST_STATION_ID_1)).thenReturn(freshDtosFromApi);
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(true);

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, true);

        // then
        assertThat(result).isEqualTo(freshDtosFromApi);
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, times(1)).getArrivalsByStationId(TEST_STATION_ID_1);
        verify(apiCallManager, times(1)).recordCall(TARGET_API_OPERATION);

        Optional<CachedStationArrival> updatedCache = cacheRepository.findByStationId(TEST_STATION_ID_1);
        assertThat(updatedCache).isPresent();
        assertThat(updatedCache.get().getArrivalDataJson()).isEqualToIgnoringWhitespace(serializeQuietly(freshDtosFromApi));
    }

    @Test
    @DisplayName("API 호출 실패: API 호출 중 예외 발생 시, 만료된 캐시라도 있으면 반환하고 호출 기록 안함")
    void getArrivalsForStation_whenApiCallFails_shouldReturnStaleCacheIfExistsAndNotRecordCall() throws JsonProcessingException {
        // given
        List<SpecificStationArrivalDto> staleDtos = List.of(createMockDto(TEST_STATION_ID_1, "500번_오래됨"));
        String staleJsonData = objectMapper.writeValueAsString(staleDtos);
        LocalDateTime veryOldTime = LocalDateTime.now().minusHours(1);
        CachedStationArrival staleCacheEntry = new CachedStationArrival(TEST_STATION_ID_1, staleJsonData, veryOldTime, veryOldTime.plusMinutes(1));
        cacheRepository.save(staleCacheEntry);

        when(actualBusArrivalService.getArrivalsByStationId(TEST_STATION_ID_1)).thenThrow(new RuntimeException("API 통신 오류!"));
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(true);

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isEqualTo(staleDtos);
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, times(1)).getArrivalsByStationId(TEST_STATION_ID_1);
        verify(apiCallManager, never()).recordCall(TARGET_API_OPERATION); // API 실패 시 호출 기록 안 함
    }

    @Test
    @DisplayName("API 호출 실패 (캐시 없음): API 호출 중 예외 발생하고 캐시도 없으면 빈 리스트 반환하고 호출 기록 안함")
    void getArrivalsForStation_whenApiCallFailsAndNoCache_shouldReturnEmptyListAndNotRecordCall() {
        // given
        when(actualBusArrivalService.getArrivalsByStationId(TEST_STATION_ID_1)).thenThrow(new RuntimeException("API 통신 오류!"));
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(true);

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isNotNull().isEmpty();
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, times(1)).getArrivalsByStationId(TEST_STATION_ID_1);
        verify(apiCallManager, never()).recordCall(TARGET_API_OPERATION);
    }


    @Test
    @DisplayName("API가 null 반환: API가 null을 반환하면 빈 리스트를 반환하고 호출 기록은 한다")
    void getArrivalsForStation_whenApiReturnsNull_shouldReturnEmptyListAndRecordCall() {
        // given
        when(actualBusArrivalService.getArrivalsByStationId(TEST_STATION_ID_1)).thenReturn(null); // API가 null 반환
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(true);

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isNotNull().isEmpty();
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, times(1)).getArrivalsByStationId(TEST_STATION_ID_1);
        verify(apiCallManager, times(1)).recordCall(TARGET_API_OPERATION); // null 반환도 호출 성공으로 간주 (BusArrivalCacheService 로직에 따름)

        Optional<CachedStationArrival> cacheEntry = cacheRepository.findByStationId(TEST_STATION_ID_1);
        assertThat(cacheEntry).isNotPresent(); // null 데이터는 캐시하지 않음
    }

    @Test
    @DisplayName("API 호출 제한 도달: API 호출 제한에 도달하면 API를 호출하지 않고, 유효한 캐시가 있으면 반환")
    void getArrivalsForStation_whenApiLimitReachedAndValidCache_shouldReturnCachedDataWithoutApiCall() throws JsonProcessingException {
        // given: 유효한 캐시 데이터 준비
        List<SpecificStationArrivalDto> cachedDtos = List.of(createMockDto(TEST_STATION_ID_1, "600번_캐시있음"));
        String jsonData = objectMapper.writeValueAsString(cachedDtos);
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(BusArrivalCacheService.CACHE_INTERNAL_TTL_MINUTES);
        CachedStationArrival cacheEntry = new CachedStationArrival(TEST_STATION_ID_1, jsonData, LocalDateTime.now().minusMinutes(1), expiryTime);
        cacheRepository.save(cacheEntry);

        // when: 캐시 서비스를 호출 (강제 새로고침 아님)
        // 이 경우, 캐시 히트 로직이 먼저 동작하므로 apiCallManager.canMakeCall은 호출되지 않음
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then: 캐시된 데이터 반환, API 호출 관련 메소드 호출 안됨
        assertThat(result).isEqualTo(cachedDtos);
        verify(apiCallManager, never()).canMakeCall(any(ApiOperation.class));
        verify(actualBusArrivalService, never()).getArrivalsByStationId(anyString());
        verify(apiCallManager, never()).recordCall(any(ApiOperation.class));
    }


    @Test
    @DisplayName("API 호출 제한 도달 (캐시 만료): API 호출 제한 시 만료된 캐시라도 있으면 반환")
    void getArrivalsForStation_whenApiLimitReachedAndCacheExpired_shouldReturnStaleCacheWithoutApiCall() throws JsonProcessingException {
        // given: 만료된 캐시 데이터 준비
        List<SpecificStationArrivalDto> staleDtos = List.of(createMockDto(TEST_STATION_ID_1, "700번_만료캐시"));
        String staleJsonData = objectMapper.writeValueAsString(staleDtos);
        LocalDateTime veryOldTime = LocalDateTime.now().minusHours(1);
        CachedStationArrival staleCacheEntry = new CachedStationArrival(TEST_STATION_ID_1, staleJsonData, veryOldTime, veryOldTime.plusMinutes(1));
        cacheRepository.save(staleCacheEntry);

        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(false); // API 호출 제한!

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isEqualTo(staleDtos); // 만료된 캐시 반환
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION); // 호출 가능 여부 확인
        verify(actualBusArrivalService, never()).getArrivalsByStationId(anyString()); // 실제 API 호출 안 함
        verify(apiCallManager, never()).recordCall(any(ApiOperation.class)); // 호출 기록 안 함
    }

    @Test
    @DisplayName("API 호출 제한 도달 (캐시 없음): API 호출 제한 시 캐시도 없으면 빈 리스트 반환")
    void getArrivalsForStation_whenApiLimitReachedAndNoCache_shouldReturnEmptyListWithoutApiCall() {
        // given: 캐시 없음
        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(false); // API 호출 제한!

        // when
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, false);

        // then
        assertThat(result).isNotNull().isEmpty(); // 빈 리스트 반환
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, never()).getArrivalsByStationId(anyString());
        verify(apiCallManager, never()).recordCall(any(ApiOperation.class));
    }

    @Test
    @DisplayName("API 호출 제한 도달 (강제 새로고침): 강제 새로고침 시에도 API 호출 제한이면 API 호출 안함 (만료 캐시 반환)")
    void getArrivalsForStation_whenApiLimitReachedAndForceRefresh_shouldReturnStaleCacheWithoutApiCall() throws JsonProcessingException {
        // given: 만료된 캐시 데이터 준비
        List<SpecificStationArrivalDto> staleDtos = List.of(createMockDto(TEST_STATION_ID_1, "800번_만료캐시_강제"));
        String staleJsonData = objectMapper.writeValueAsString(staleDtos);
        LocalDateTime veryOldTime = LocalDateTime.now().minusHours(1);
        CachedStationArrival staleCacheEntry = new CachedStationArrival(TEST_STATION_ID_1, staleJsonData, veryOldTime, veryOldTime.plusMinutes(1));
        cacheRepository.save(staleCacheEntry);

        when(apiCallManager.canMakeCall(TARGET_API_OPERATION)).thenReturn(false); // API 호출 제한!

        // when: 강제 새로고침 true
        List<SpecificStationArrivalDto> result = busArrivalCacheService.getArrivalsForStation(TEST_STATION_ID_1, true);

        // then: 강제 새로고침이어도 API 호출 제한이면 API 호출 안하고 만료된 캐시 반환
        assertThat(result).isEqualTo(staleDtos);
        verify(apiCallManager, times(1)).canMakeCall(TARGET_API_OPERATION);
        verify(actualBusArrivalService, never()).getArrivalsByStationId(anyString());
        verify(apiCallManager, never()).recordCall(any(ApiOperation.class));
    }


    private String serializeQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing object to JSON for test: " + value, e);
        }
    }
}