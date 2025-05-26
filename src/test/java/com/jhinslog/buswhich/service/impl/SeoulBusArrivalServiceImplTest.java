package com.jhinslog.buswhich.service.impl;

import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;
import com.jhinslog.buswhich.service.BusArrivalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest // Spring Boot 통합 테스트를 위한 어노테이션
class SeoulBusArrivalServiceImplTest {

    @Autowired
    private BusArrivalService busArrivalService; // 실제 빈을 주입받아 테스트

    @Test
    @DisplayName("특정 정류소(arsId)의 도착 정보 조회 테스트")
        //@Disabled("실제 API 호출을 하므로 CI 환경 등에서는 주의, 필요시 활성화")
    void getArrivalsByStationId_whenValidArsId_shouldReturnArrivalInfo() {
        // given
        String testArsId = "16557"; // 실제 운영 중인 정류소 arsId
        String expectedStationName = "강서구청입구"; // 예상되는 정류소 이름
        String expectedStId = "115900026"; // 예상되는 정류소 고유 ID
        // Integer expectedStationOrder = 48; // 예상되는 노선 순번 (만약 이 노선이 항상 첫번째로 온다면)

        // when
        List<SpecificStationArrivalDto> results = busArrivalService.getArrivalsByStationId(testArsId);

        // then
        assertNotNull(results, "결과 리스트는 null이 아니어야 합니다.");
        assertFalse(results.isEmpty(), "유효한 arsId '" + testArsId + "'에 대해 도착 정보가 있어야 합니다. (실제 운행 상황에 따라 달라질 수 있음)");

        // 결과가 여러 개일 수 있으므로, 모든 결과에 대해 공통적인 부분을 검증하거나,
        // 특정 조건(예: 특정 노선 ID)에 맞는 항목을 찾아 상세 검증할 수 있습니다.
        // 여기서는 첫 번째 결과를 기준으로 몇 가지 주요 항목을 검증합니다.
        if (!results.isEmpty()) {
            SpecificStationArrivalDto firstResult = results.get(0); // 첫 번째 결과 가져오기

            assertEquals(expectedStationName, firstResult.getStationName(), "정류소 이름이 예상과 일치해야 합니다.");
            assertEquals(testArsId, firstResult.getArsId(), "요청한 arsId와 결과의 arsId가 일치해야 합니다.");
            assertEquals(expectedStId, firstResult.getStationId(), "정류소 고유 ID(stId)가 예상과 일치해야 합니다.");
            assertNotNull(firstResult.getStationOrder(), "정류소 순번(stationOrder)은 null이 아니어야 합니다.");
            // assertEquals(expectedStationOrder, firstResult.getStationOrder(), "정류소 순번이 예상과 일치해야 합니다."); // 주석 처리: 노선 순번은 노선마다 다를 수 있음

            // 첫 번째 버스 도착 정보의 존재 여부 및 기본 형식 검증
            assertNotNull(firstResult.getFirstArrivalMsg(), "첫번째 버스 도착 메시지는 null이 아니어야 합니다.");
            assertTrue(firstResult.getFirstArrivalMsg().length() > 0, "첫번째 버스 도착 메시지는 비어있지 않아야 합니다.");
            assertNotNull(firstResult.getFirstCongestion(), "첫번째 버스 혼잡도 정보는 null이 아니어야 합니다.");
            // 차량 번호는 없을 수도 있으므로, 존재할 경우에 대한 검증 또는 null 허용 검증
            // assertNotNull(firstResult.getFirstPlainNo(), "첫번째 버스 차량번호는 null이 아니어야 합니다. (없을 수도 있음)");

            // 두 번째 버스 도착 정보는 없을 수도 있으므로, 존재할 경우에만 검증
            if (firstResult.getSecondArrivalMsg() != null && !firstResult.getSecondArrivalMsg().isEmpty()) {
                assertNotNull(firstResult.getSecondCongestion(), "두번째 버스 혼잡도 정보는 null이 아니어야 합니다.");
            }

            // detourYn 필드는 boolean 타입이므로 true 또는 false 여부 검증
            assertNotNull(firstResult.getDetourYn(), "우회 여부(detourYn) 정보는 null이 아니어야 합니다.");
        }

        // API 호출 결과를 로그로 확인 (디버깅 또는 수동 확인용)
        System.out.println("--- 유효한 arsId (" + testArsId + ") 조회 결과 ---");
        if (results.isEmpty()) {
            System.out.println("조회 결과: 해당 정류소에 도착 예정 버스 정보가 없습니다.");
        } else {
            System.out.println("조회된 버스 정보 개수: " + results.size());
            results.forEach(dto -> {
                System.out.println("------------------------------------");
                System.out.println("정류소 이름: " + dto.getStationName() + " (arsId: " + dto.getArsId() + ", stId: " + dto.getStationId() + ")");
                System.out.println("노선 순번: " + dto.getStationOrder());
                System.out.println("첫번째 버스: " + dto.getFirstArrivalMsg() + " (혼잡도: " + dto.getFirstCongestion() + ", 차량번호: " + dto.getFirstPlainNo() + ")");
                if (dto.getSecondArrivalMsg() != null && !dto.getSecondArrivalMsg().isEmpty()) {
                    System.out.println("두번째 버스: " + dto.getSecondArrivalMsg() + " (혼잡도: " + dto.getSecondCongestion() + ", 차량번호: " + dto.getSecondPlainNo() + ")");
                }
                System.out.println("우회 여부: " + dto.getDetourYn());
                System.out.println("------------------------------------");
            });
        }
        System.out.println("--- 조회 결과 끝 ---");
    }


    @Test
    @DisplayName("유효하지 않은 arsId로 조회 시 빈 리스트 반환 테스트")
    @Disabled("실제 API 호출을 하므로 CI 환경 등에서는 주의, 필요시 활성화")
    void getArrivalsByStationId_whenInvalidArsId_shouldReturnEmptyList() {
        // given
        String invalidArsId = "INVALID_ID"; // 존재하지 않는 arsId

        // when
        List<SpecificStationArrivalDto> results = busArrivalService.getArrivalsByStationId(invalidArsId);

        // then
        assertNotNull(results, "결과 리스트는 null이 아니어야 합니다.");
        assertTrue(results.isEmpty(), "유효하지 않은 arsId에 대해서는 빈 리스트가 반환되어야 합니다.");
    }
}