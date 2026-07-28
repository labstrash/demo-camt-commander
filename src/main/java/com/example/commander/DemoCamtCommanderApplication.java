package com.example.commander;

import com.example.commander.config.ReportConfigReadProperties;
import com.example.commander.config.SchedulingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ReportConfigReadProperties.class, SchedulingProperties.class})
public class DemoCamtCommanderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoCamtCommanderApplication.class, args);
    }
}
