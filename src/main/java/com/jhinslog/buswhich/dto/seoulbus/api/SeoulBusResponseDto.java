package com.jhinslog.buswhich.dto.seoulbus.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // API 응답의 최상위 객체에 예상치 못한 필드가 있을 경우 무시
public class SeoulBusResponseDto<T> {

    @JsonProperty("comMsgHeader")
    private SeoulBusComMsgHeaderDto comMsgHeader;

    @JsonProperty("msgHeader")
    private SeoulBusMsgHeaderDto msgHeader;

    @JsonProperty("msgBody")
    private SeoulBusMsgBodyDto<T> msgBody;

    // 주석들은 설명을 위한 것이므로 실제 코드 동작에는 영향을 주지 않습니다.
    // 현재 JSON 응답 구조에 따르면 이 DTO는 정확하게 매핑됩니다.
}