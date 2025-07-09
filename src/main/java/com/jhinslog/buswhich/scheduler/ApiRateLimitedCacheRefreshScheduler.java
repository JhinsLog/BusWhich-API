package com.jhinslog.buswhich.scheduler;

import com.jhinslog.buswhich.service.BusArrivalCacheService;
import com.jhinslog.buswhich.websocket.ArrivalBusInfoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class ApiRateLimitedCacheRefreshScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimitedCacheRefreshScheduler.class);

    private final BusArrivalCacheService cacheService;
    private final ArrivalBusInfoHandler arrivalBusInfoHandler;
    // ApiCallManager는 BusArrivalCacheService -> SeoulBusArrivalServiceImpl을 통해 간접적으로 사용됩니다.

    // 하루에 API를 통해 캐시를 갱신할 수 있는 최대 정류소 수 (매우 보수적인 예시)
    // 실제 SeoulBusArrivalServiceImpl에서 ApiCallManager가 호출을 제어하므로,
    // 이 스케줄러의 역할은 "어떤 정류소의 캐시를 갱신 시도할 것인가"에 더 초점.
    private static final int MAX_STATIONS_TO_ATTEMPT_REFRESH_PER_DAY = 100; // 예시 값
    private int attemptedRefreshesToday = 0;

    @Autowired
    public ApiRateLimitedCacheRefreshScheduler(BusArrivalCacheService cacheService,
                                               ArrivalBusInfoHandler arrivalBusInfoHandler) {
        this.cacheService = cacheService;
        this.arrivalBusInfoHandler = arrivalBusInfoHandler;
    }

    // 자정마다 오늘 갱신 시도한 정류소 개수 초기화
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정
    public void resetDailyRefreshAttemptCount() {
        logger.info("Resetting daily station cache refresh attempt count.");
        attemptedRefreshesToday = 0;
    }

    // 예: 15분마다 실행. 구독 중인 역 중 일부를 순차적으로 캐시 갱신 시도
    @Scheduled(fixedRate = 900000, initialDelay = 120000) // 2분 후 시작, 이후 15분 간격
    public void refreshSubscribedStationCaches() {
        Set<String> subscribedStationIds = arrivalBusInfoHandler.getSubscribedStationIds();
        if (subscribedStationIds.isEmpty()) {
            logger.debug("No active subscriptions. Skipping cache refresh attempt cycle.");
            return;
        }

        if (attemptedRefreshesToday >= MAX_STATIONS_TO_ATTEMPT_REFRESH_PER_DAY) {
            logger.info("Max stations to attempt refresh per day ({}) reached. Skipping further cache refresh attempts for today.", MAX_STATIONS_TO_ATTEMPT_REFRESH_PER_DAY);
            return;
        }

        List<String> stationsToProcess = new ArrayList<>(subscribedStationIds);
        Collections.shuffle(stationsToProcess); // 매번 같은 순서로 처리되는 것을 방지

        int refreshedThisCycle = 0;
        for (String stationId : stationsToProcess) {
            if (attemptedRefreshesToday >= MAX_STATIONS_TO_ATTEMPT_REFRESH_PER_DAY) {
                logger.info("Daily limit for attempting cache refreshes reached during this cycle.");
                break;
            }

            logger.info("Attempting to refresh cache from API (via CacheService) for stationId: {}", stationId);
            try {
                // forceApiRefresh = true로 호출하여 캐시 갱신 시도.
                // 실제 API 호출 여부는 BusArrivalCacheService -> SeoulBusArrivalServiceImpl -> ApiCallManager가 결정.
                cacheService.getArrivalsForStation(stationId, true);
                attemptedRefreshesToday++;
                refreshedThisCycle++;
                logger.info("Cache refresh attempt initiated for stationId: {}. Total attempts today: {}", stationId, attemptedRefreshesToday);
            } catch (Exception e) {
                // cacheService.getArrivalsForStation 내부에서 예외가 발생할 수 있으나,
                // 해당 메소드는 API 호출 실패 시에도 가능한 이전 캐시를 반환하거나 빈 리스트를 반환하도록 설계됨.
                // 스케줄러 레벨에서는 이로 인해 스케줄러 자체가 중단되지 않도록 로깅만 처리.
                logger.error("Error during scheduled cache refresh attempt for stationId {}: {}", stationId, e.getMessage());
            }

            // 한 번의 스케줄링 주기에서 너무 많은 시도를 방지 (선택적)
            // if (refreshedThisCycle >= 5) { // 예: 한 주기에 최대 5개 역만 시도
            //    logger.info("Attempted to refresh {} stations this cycle. Pausing until next cycle.", refreshedThisCycle);
            //    break;
            // }
        }
        logger.info("Finished cache refresh attempt cycle. Attempted {} stations this cycle. Total attempts today: {}", refreshedThisCycle, attemptedRefreshesToday);
    }
}