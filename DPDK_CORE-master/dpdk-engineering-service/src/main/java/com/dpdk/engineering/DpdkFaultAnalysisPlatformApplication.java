package com.dpdk.engineering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.dpdk")
@EntityScan(basePackages = {
        "com.dpdk.collector.entity",
        "com.dpdk.ai.knowledge.entity",
        "com.dpdk.ai.incremental.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.dpdk.collector.repository",
        "com.dpdk.ai.knowledge.repository",
        "com.dpdk.ai.incremental.repository"
})
public class DpdkFaultAnalysisPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DpdkFaultAnalysisPlatformApplication.class, args);
    }
}
