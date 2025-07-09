package com.jhinslog.buswhich.scheduler;

import com.jhinslog.buswhich.service.BusArrivalCacheService; // 캐시 서비스 사용
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.websocket.ArrivalBusInfoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BusArrivalUpdateScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BusArrivalUpdateScheduler.class);

    private final BusArrivalCacheService cacheService;
    private final ArrivalBusInfoHandler arrivalBusInfoHandler;

    // 각 정류소별 마지막으로 클라이언트에게 정보를 브로드캐스팅한 시간
    private final Map<String, LocalDateTime> lastBroadcastTimeMap = new ConcurrentHashMap<>();

    @Autowired
    public BusArrivalUpdateScheduler(BusArrivalCacheService cacheService,
                                     ArrivalBusInfoHandler arrivalBusInfoHandler) {
        this.cacheService = cacheService;
        this.arrivalBusInfoHandler = arrivalBusInfoHandler;
    }

    // 이 스케줄러는 API를 직접 호출하지 않으므로 비교적 자주 실행 가능 (예: 10초)
    // 클라이언트에게 "실시간처럼 보이는" 업데이트를 제공하는 역할
    @Scheduled(fixedRate = 10000, initialDelay = 5000) // 5초 후 시작, 이후 10초 간격
    public void broadcastUpdatesFromCache() {
        Set<String> subscribedStationIds = arrivalBusInfoHandler.getSubscribedStationIds();
        LocalDateTime currentTime = LocalDateTime.now();

        if (subscribedStationIds.isEmpty()) {
            return; // 구독자가 없으면 작업 없음
        }

        for (String stationId : subscribedStationIds) {
            try {
                // API를 직접 호출하지 않고 캐시에서 데이터를 가져옴 (forceApiRefresh = false)
                // cacheService.getArrivalsForStation는 내부적으로 만료된 캐시라도 반환할 수 있음
                List<SpecificStationArrivalDto> cachedArrivalInfo = cacheService.getArrivalsForStation(stationId, false);

                if (cachedArrivalInfo == null) { // 혹시 모를 null 반환에 대비
                    cachedArrivalInfo = Collections.emptyList();
                }

                // 동적 업데이트 주기 로직 (남은 시간 기준)
                long broadcastIntervalSeconds = calculateBroadcastInterval(cachedArrivalInfo);
                LocalDateTime lastBroadcast = lastBroadcastTimeMap.get(stationId);

                if (lastBroadcast == null || lastBroadcast.plusSeconds(broadcastIntervalSeconds).isBefore(currentTime)) {
                    // cachedArrivalInfo가 비어있더라도 (예: 정보 없음, API 제한으로 데이터 못 가져옴)
                    // 클라이언트에게는 현재 상태(빈 정보)를 알려주는 것이 좋을 수 있음.
                    arrivalBusInfoHandler.broadcastArrivalUpdates(stationId, cachedArrivalInfo);
                    lastBroadcastTimeMap.put(stationId, currentTime);
                    if (!cachedArrivalInfo.isEmpty()) {
                        logger.debug("Broadcasted cached updates for stationId: {} (Interval: {}s, {} items)", stationId, broadcastIntervalSeconds, cachedArrivalInfo.size());
                    } else {
                        logger.debug("Broadcasted empty info for stationId: {} (Interval: {}s) - No data in cache or from service.", stationId, broadcastIntervalSeconds);
                    }
                }
            } catch (Exception e) {
                logger.error("Error during cache-based broadcast for stationId {}: {}", stationId, e.getMessage(), e);
            }
        }
    }

    private long calculateBroadcastInterval(List<SpecificStationArrivalDto> arrivalInfo) {
        if (arrivalInfo.isEmpty()) {
            return 60L; // 정보 없으면 1분 간격으로 체크 (클라이언트에게 no_info 계속 보내는 것 방지 위함)
        }
        // 첫 번째 버스 기준으로 판단 (더 정교하게 하려면 모든 버스 고려)
        SpecificStationArrivalDto firstBus = arrivalInfo.get(0);
        if (firstBus.getFirstRemainingSec() == null) {
            return 60L; // 남은 시간 정보 없으면 1분
        }

        int remainingSec = firstBus.getFirstRemainingSec();

        if (remainingSec < 0) return 10L; // 이미 도착했거나 지나간 경우 빠르게 다음 정보 확인

        if (remainingSec < 180) { // 3분 미만
            return 10L; // 10초 간격
        } else if (remainingSec < 300) { // 5분 미만 (3분 이상)
            return 30L; // 30초 간격
        } else { // 5분 이상
            return 60L; // 1분 간격
        }
    }
}