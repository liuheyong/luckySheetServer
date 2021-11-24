package com.xc.luckysheet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * @author cr
 */
@Slf4j
@EnableScheduling
@Configuration
@SpringBootApplication
@ComponentScan(basePackages = {"com.xc"})
public class LuckySheetWebApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(LuckySheetWebApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(LuckySheetWebApplication.class);
    }
}
