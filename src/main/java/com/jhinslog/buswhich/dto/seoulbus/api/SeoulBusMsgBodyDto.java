package com.jhinslog.buswhich.dto.seoulbus.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SeoulBusMsgBodyDto<T> { // 제네릭을 사용하여 다양한 itemList 타입을 받을 수 있도록 함

    @JsonProperty("itemList") // JSON 응답에서 "itemList"라는 키로 실제 데이터 리스트가 온다고 가정
    private List<T> itemList; // 실제 데이터 리스트

}
