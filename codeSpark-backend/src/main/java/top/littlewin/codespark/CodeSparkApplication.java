package top.littlewin.codespark;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("top.littlewin.codespark.mapper")
public class CodeSparkApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeSparkApplication.class, args);
    }

}
