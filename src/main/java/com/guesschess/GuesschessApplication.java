package com.guesschess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EnableScheduling active GameClockScheduler (etape 12, flag-fall) - le TaskScheduler
 * par defaut de Spring Boot est deja adosse aux threads virtuels via
 * spring.threads.virtual.enabled=true (application.properties), pas besoin d'un bean
 * dedie.
 */
@SpringBootApplication
@EnableScheduling
public class GuesschessApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuesschessApplication.class, args);
    }
}
