package com.jhinslog.buswhich.dto.seoulbus.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SeoulBusMsgHeaderDto {

    @JsonProperty("headerCd")
    private String headerCd; // 결과 코드

    @JsonProperty("headerMsg")
    private String headerMsg; // 결과 메시지

    @JsonProperty("itemCount")
    private int itemCount; // 결과 항목 수
}