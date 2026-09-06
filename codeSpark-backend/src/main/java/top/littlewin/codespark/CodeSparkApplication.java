package top.littlewin.codespark;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@EnableAsync
@EnableScheduling
@EnableCaching
@MapperScan("top.littlewin.codespark.mapper")
public class CodeSparkApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeSparkApplication.class, args);
    }

}
