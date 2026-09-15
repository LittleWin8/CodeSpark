package top.littlewin.codespark.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisCacheManagerConfig {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 修复：反序列化子类型白名单。
     * 原实现 LaissezFaireSubTypeValidator 放行任意 @class，Redis 值可被伪造为
     * gadget 类导致反序列化 RCE。现仅允许本项目 VO/实体与 JDK 常用容器、时间类型。
     */
    private static final PolymorphicTypeValidator TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType("java.util.Collection")
            .allowIfBaseType("java.util.Map")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("top.littlewin.codespark.")
            // 缓存值是 Page<AppVO>（@Cacheable 直接缓存 BaseResponse<Page<...>>），需放行框架分页容器
            .allowIfSubType("com.mybatisflex.core.")
            .build();

    @Bean
    public CacheManager cacheManager() {
        // 配置 ObjectMapper 支持 Java8 时间类型
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // 开启默认类型注入，序列化时把嵌套对象的类型信息写入 JSON，反序列化才能还原成真实类型；
        // 子类型校验收口到上面的白名单，白名单外的 @class 反序列化时直接抛异常
        objectMapper.activateDefaultTyping(
                TYPE_VALIDATOR,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        // 默认配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 默认 30 分钟过期
                .disableCachingNullValues() // 禁用 null 值缓存
                // key 使用 String 序列化器
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // value 使用 JSON 序列化器（支持复杂对象）
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                // 针对 good_app_page 配置5分钟过期
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}
