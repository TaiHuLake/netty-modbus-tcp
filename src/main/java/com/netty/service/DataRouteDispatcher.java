package com.netty.service;

import com.alibaba.fastjson.JSON;
import com.netty.core.ModbusFrame;
import com.netty.core.common.DeviceConfig;
import com.netty.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 22:29
 * {@code @description:}
 */
@Slf4j
@Component
public class DataRouteDispatcher {

    // 静态持有者，供非 Spring 类使用
    private static DataRouteDispatcher instance;

    private final RedisUtils redisUtils;
    private final RabbitTemplate rabbitTemplate;

    @Value("${modbus.output-mode:console}")
    private String outputMode;

    public DataRouteDispatcher(RedisUtils redisUtils, RabbitTemplate rabbitTemplate) {
        this.redisUtils = redisUtils;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * 供 Coordinator 调用的静态入口
     */
    public static void staticDispatch(DeviceConfig device, ModbusFrame response) {
        if (instance != null) {
            instance.dispatch(device, response);
        } else {
            log.error("Dispatcher 未初始化!");
        }
    }

    public void dispatch(DeviceConfig device, ModbusFrame response) {
        // 1. 检查是否为异常报文 (功能码 > 0x80)
        int fc = response.getFunctionCode() & 0xFF;
        if (fc > 0x80) {
            handleModbusException(device, response);
            return;
        }

        // 2. 正常报文解析
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("ip", device.getIp());
        dataMap.put("port", device.getPort());
        dataMap.put("slaveId", device.getSlaveId());
        dataMap.put("fc", fc);
        dataMap.put("timestamp", System.currentTimeMillis());

        executeSend(dataMap, device.getId());
    }

    private void executeSend(Map<String, Object> dataMap, String deviceId) {
        switch (outputMode.toLowerCase()) {
            case "redis":
                    redisUtils.set("MODBUS:RT:" + deviceId, dataMap);
                break;
            case "rabbitmq":
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend("modbus.exchange", "modbus.key", dataMap);
                }
                break;
            default:
                String json = JSON.toJSONString(dataMap);
                log.info("收到数据:{}", json);
        }
    }

    private void handleModbusException(DeviceConfig device, ModbusFrame response) {
        int errCode = response.getPayload()[0] & 0xFF;
        log.warn("设备 {} 响应异常码: {} (可能地址越界或从站繁忙)", device.getId(), errCode);
    }
}
