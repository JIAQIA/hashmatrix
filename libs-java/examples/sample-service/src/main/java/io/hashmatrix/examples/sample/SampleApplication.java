package io.hashmatrix.examples.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 最小 Spring Boot 子仓样例：依赖公共 starter，演示只 clone 子仓即可构建运行。 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
