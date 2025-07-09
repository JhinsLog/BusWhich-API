package com.jhinslog.buswhich.repository.cache;

import com.jhinslog.buswhich.domain.cache.CachedStationArrival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CachedStationArrivalRepository extends JpaRepository<CachedStationArrival, String> {
    Optional<CachedStationArrival> findByStationId(String stationId);
}