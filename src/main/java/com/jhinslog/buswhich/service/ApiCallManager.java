package com.jhinslog.buswhich.service;

import com.jhinslog.buswhich.util.ApiOperation; // 수정된 import 경로
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ApiCallManager {

    private static final Logger logger = LoggerFactory.getLogger(ApiCallManager.class);
    private final Map<ApiOperation, AtomicInteger> dailyCallCounts = new ConcurrentHashMap<>();
    private static final int DAILY_LIMIT_PER_FUNCTION = 1000; // 기능별 일일 호출 제한

    @PostConstruct
    public void init() {
        for (ApiOperation functionName : ApiOperation.values()) {
            dailyCallCounts.put(functionName, new AtomicInteger(0));
        }
        logger.info("ApiCallManager initialized with {} API functions.", ApiOperation.values().length);
    }

    /**
     * 특정 API 기능에 대한 호출이 가능한지 확인합니다.
     * @param functionName 확인할 API 기능
     * @return 호출 가능하면 true, 아니면 false
     */
    public boolean canMakeCall(ApiOperation functionName) {
        AtomicInteger count = dailyCallCounts.get(functionName);
        if (count == null) {
            logger.warn("No call count found for API function: {}. Assuming limit not reached.", functionName);
            return true;
        }
        boolean canCall = count.get() < DAILY_LIMIT_PER_FUNCTION;
        if (!canCall) {
            logger.warn("Daily API call limit ({}) reached for function: {}", DAILY_LIMIT_PER_FUNCTION, functionName);
        }
        return canCall;
    }

    /**
     * 특정 API 기능의 호출 횟수를 1 증가시킵니다.
     * @param functionName 호출된 API 기능
     */
    public void recordCall(ApiOperation functionName) {
        recordCalls(functionName, 1);
    }

    /**
     * 특정 API 기능의 호출 횟수를 지정된 만큼 증가시킵니다.
     * @param functionName 호출된 API 기능
     * @param count 증가시킬 횟수
     */
    public void recordCalls(ApiOperation functionName, int count) {
        if (count <= 0) return;

        AtomicInteger currentCount = dailyCallCounts.get(functionName);
        if (currentCount != null) {
            int newCount = currentCount.addAndGet(count);
            logger.debug("API call recorded for {}. Count: {}, Total today: {}", functionName, count, newCount);
            if (newCount >= DAILY_LIMIT_PER_FUNCTION) {
                logger.warn("Daily API call limit ({}) just reached or exceeded for function: {}. Current count: {}",
                        DAILY_LIMIT_PER_FUNCTION, functionName, newCount);
            }
        } else {
            logger.warn("Attempted to record call for uninitialized API function: {}", functionName);
        }
    }

    /**
     * 매일 자정 모든 API 기능의 호출 횟수를 초기화합니다.
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 00:00:00에 실행
    public void resetDailyCounts() {
        logger.info("Resetting all daily API call counts.");
        for (ApiOperation functionName : ApiOperation.values()) {
            dailyCallCounts.get(functionName).set(0);
        }
        logger.info("All daily API call counts have been reset.");
    }

    public int getCurrentCallCount(ApiOperation functionName) {
        AtomicInteger count = dailyCallCounts.get(functionName);
        return (count != null) ? count.get() : 0;
    }
}