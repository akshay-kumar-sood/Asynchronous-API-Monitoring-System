package com.example.apimonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ApiMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiMonitoringApplication.class, args);
        System.out.println("hello");
    }
}
