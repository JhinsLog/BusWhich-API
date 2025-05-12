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
@JsonIgnoreProperties(ignoreUnknown = true) // comMsgHeader 내부에 예상치 못한 필드가 있을 경우 무시
public class SeoulBusComMsgHeaderDto {

    @JsonProperty("errMsg")
    private String errMsg;

    @JsonProperty("responseTime")
    private String responseTime; // 실제 타입이 날짜/시간이라면 String으로 받고 필요시 변환

    @JsonProperty("requestMsgID")
    private String requestMsgID;

    @JsonProperty("responseMsgID")
    private String responseMsgID;

    @JsonProperty("successYN")
    private String successYN; // "Y", "N" 또는 boolean으로 매핑 가능

    @JsonProperty("returnCode")
    private String returnCode;
}