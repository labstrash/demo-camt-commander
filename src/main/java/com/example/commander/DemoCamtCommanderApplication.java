package com.example.commander;

import com.example.commander.adapter.batch.config.BatchPipelineProperties;
import com.example.commander.config.ReportConfigReadProperties;
import com.example.commander.config.SchedulingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    ReportConfigReadProperties.class,
    SchedulingProperties.class,
    BatchPipelineProperties.class
})
public class DemoCamtCommanderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoCamtCommanderApplication.class, args);
    }
}
