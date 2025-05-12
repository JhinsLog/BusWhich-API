package com.jhinslog.buswhich.service;

import com.jhinslog.buswhich.dto.response.RouteAllArrivalsResponseDto;

public interface BusArrivalService {

    RouteAllArrivalsResponseDto getArrivalsByRoute(String busRouteId);

}
