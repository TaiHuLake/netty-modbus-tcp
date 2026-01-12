package com.netty.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:37
 * {@code @description:}
 */
@Configuration
public class RabbitConfig {
    @Bean
    public DirectExchange modbusExchange() {
        return new DirectExchange("modbus.exchange");
    }
}
