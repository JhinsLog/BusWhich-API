package com.jhinslog.buswhich.util;

public enum ApiOperation {
    // 서울특별시_버스도착정보조회_서비스
    GET_ARR_INFO_BY_ROUTE_ALL_LIST("getArrInfoByRouteAllList"), // 경유노선 전체 정류소 도착예정정보
    GET_ARR_INFO_BY_ROUTE_LIST("getArrInfoByRouteList"),        // 특정노선 특정정류소 도착예정정보
    GET_LOW_ARR_INFO_BY_ROUTE_LIST("getLowArrInfoByRouteList"), // 저상버스 특정노선 특정정류소 도착예정정보
    GET_ARR_INFO_BY_ST_ID_LIST("getArrInfoByStIdList"),         // 일반버스 특정정류소 도착예정정
    GET_LOW_ARR_INFO_BY_ST_ID_LIST("getLowArrInfoByStIdList"),  // 저상버스 특정정류소 도착예정정보

    // 서울특별시_정류소정보조회 서비스
    GET_STATION_BY_NAME_LIST("getStationByNameList"),           // 정류소명 검색
    GET_STATION_BY_UID_ITEM("getStationByUidItem"),             // 정류소 ARS번호로 정류소정보조회
    GET_ROUTE_BY_STATION_LIST("getRouteByStationList"),         // 정류소고유번호로 경유노선목록조회
    GET_BUS_TIME_BY_STATION_LIST("getBustimeByStationList"),    // 정류소고유번호로 버스 첫막차시간 조회
    GET_LOW_STATION_BY_NAME_LIST("getLowStationByNameList"),    // 저상버스 정류소명 검색
    GET_LOW_STATION_BY_UID_LIST("getLowStaionByUidList"),       // 저상버스 정류소 ARS번호로 정류소정보조회
    GET_STATIONS_BY_POS_LIST("getStaionsByPosList");            // 좌표기반 근접정류소 조회

    private final String operationName;

    ApiOperation(String operationName) {
        this.operationName = operationName;
    }

    public String getOperationName() {
        return operationName;
    }
}