package com.example.commander;

import com.example.commander.adapter.batch.config.BatchPipelineProperties;
import com.example.commander.adapter.message.MqProperties;
import com.example.commander.adapter.message.MqResilienceProperties;
import com.example.commander.adapter.message.ondemand.OnDemandProperties;
import com.example.commander.adapter.message.pht.PhtProperties;
import com.example.commander.adapter.persistence.ReportConfigReadProperties;
import com.example.commander.adapter.scheduling.AuditRetentionProperties;
import com.example.commander.adapter.scheduling.SchedulingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
@EnableConfigurationProperties({
    ReportConfigReadProperties.class,
    SchedulingProperties.class,
    BatchPipelineProperties.class,
    MqResilienceProperties.class,
    MqProperties.class,
    AuditRetentionProperties.class,
    OnDemandProperties.class,
    PhtProperties.class
})
public class DemoCamtCommanderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoCamtCommanderApplication.class, args);
    }
}
