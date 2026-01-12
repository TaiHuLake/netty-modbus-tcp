package com.netty.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-12 10:55
 * {@code @description:}
 */
public class JacksonSerializerConfig {

    public static Jackson2JsonRedisSerializer<Object> jacksonSerializer() {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 使用 PolymorphicTypeValidator 替代 enableDefaultTyping
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // 允许集合类
                .allowIfSubType("java.util")
                // 允许你自己的业务类包
                .allowIfSubType("com.netty")
                .build();

        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

        serializer.setObjectMapper(mapper);
        return serializer;
    }
}

