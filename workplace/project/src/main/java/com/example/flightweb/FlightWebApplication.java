package com.example.flightweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // 여기가 메인(테스트)
public class FlightWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightWebApplication.class, args);
    }
}
