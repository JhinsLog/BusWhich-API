package com.jhinslog.buswhich;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BusWhichApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusWhichApplication.class, args);
    }

}
