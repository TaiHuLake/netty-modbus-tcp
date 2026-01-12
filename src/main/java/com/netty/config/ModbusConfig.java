package com.netty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 20:43
 * {@code @description:}
 */
@Data
@Component
@ConfigurationProperties(prefix = "modbus.custom")
public class ModbusConfig {
    // 连接超时
    private int connectTimeout = 5000;

    // 响应超时
    private int readTimeout = 3000;

    // 重试连接数
    private int retryCount = 3;

    // 轮询间隔
    private int pollInterval = 10000;

    // 最大连接数
    private int maxConnections = 500;

    // CONSOLE, REDIS, RABBITMQ
    private String outputMode = "CONSOLE";
}