package com.smartqueue;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@Slf4j
public class SmartQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartQueueApplication.class, args);
    }

    /**
     * Defensive Engineering: Force the global JVM timezone to UTC on startup.
     * 
     * Why?
     * This prevents nasty, hard-to-reproduce time-drift bugs where local dev
     * environments save timestamps differently than remote cloud servers, or
     * when the database timezone doesn't match the application server timezone.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        log.info("System TimeZone globally initialized to UTC");
    }
}
