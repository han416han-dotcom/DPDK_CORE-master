package com.dpdk.engineering.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class UploadTempDirInit {

    @Bean
    public ApplicationRunner ensureUploadTempDirExists(
            @Value("${spring.servlet.multipart.location}") String multipartLocation,
            @Value("${server.tomcat.basedir}") String tomcatBaseDir
    ) {
        return args -> {
            createDirsIfPresent(multipartLocation);
            createDirsIfPresent(tomcatBaseDir);
        };
    }

    private static void createDirsIfPresent(String dir) throws Exception {
        if (dir == null || dir.isBlank()) {
            return;
        }
        Files.createDirectories(Path.of(dir));
    }
}

