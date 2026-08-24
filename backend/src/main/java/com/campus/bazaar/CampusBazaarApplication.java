package com.campus.bazaar;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.campus.bazaar.mapper")
@SpringBootApplication
public class CampusBazaarApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusBazaarApplication.class, args);
    }

}
