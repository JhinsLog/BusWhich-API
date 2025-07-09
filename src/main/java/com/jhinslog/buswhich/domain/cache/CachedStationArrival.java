package com.jhinslog.buswhich.domain.cache; // 패키지 변경

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cached_station_arrivals")
@Getter
@Setter
@NoArgsConstructor
public class CachedStationArrival {

    @Id
    @Column(name = "station_id", length = 50)
    private String stationId;

    @Lob
    @Column(name = "arrival_data_json", columnDefinition = "TEXT")
    private String arrivalDataJson;

    @Column(name = "last_api_call_time")
    private LocalDateTime lastApiCallTime;

    @Column(name = "cache_expiry_time")
    private LocalDateTime cacheExpiryTime;

    public CachedStationArrival(String stationId, String arrivalDataJson, LocalDateTime lastApiCallTime, LocalDateTime cacheExpiryTime) {
        this.stationId = stationId;
        this.arrivalDataJson = arrivalDataJson;
        this.lastApiCallTime = lastApiCallTime;
        this.cacheExpiryTime = cacheExpiryTime;
    }
}