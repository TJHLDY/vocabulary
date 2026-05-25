package org.gduf;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.gduf.mapper")
@EnableScheduling
public class VocabularyApplication {
    public static void main(String[] args) {
        SpringApplication.run(VocabularyApplication.class, args);
    }
}
