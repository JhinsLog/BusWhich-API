package com.jhinslog.buswhich.service;

import com.jhinslog.buswhich.dto.response.RouteAllArrivalsResponseDto;
import com.jhinslog.buswhich.dto.response.SpecificStationArrivalDto;

import java.util.List;

public interface BusArrivalService {

    RouteAllArrivalsResponseDto getArrivalsByRoute(String busRouteId);

    List<SpecificStationArrivalDto> getArrivalsByStationId(String stationId);

}
