package com.jhinslog.buswhich.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WebSocketSubscriptionRequestDto {
    private String type; // subscribe or unsubscribe
    private String stationId; // 구독할 정류소 ID (type이 "subscribe"일 때 필요)
}
