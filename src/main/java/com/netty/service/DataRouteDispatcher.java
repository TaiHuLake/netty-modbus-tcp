package com.netty.service;

import com.alibaba.fastjson.JSON;
import com.netty.core.ModbusDataParser;
import com.netty.core.ModbusFrame;
import com.netty.core.common.DeviceConfig;
import io.netty.buffer.ByteBufUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Value("${modbus.output-mode:console}")
    private String outputMode;

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
        dataMap.put("deviceId", device.getId());
        dataMap.put("slaveId", device.getSlaveId());
        dataMap.put("fc", fc);
        dataMap.put("timestamp", System.currentTimeMillis());

        // 解析 Payload (根据功能码)
        byte[] payload = response.getPayload();
        if (fc == 3 || fc == 4) {
            // 寄存器读取解析：[字节计数(1byte), 数据(n bytes)]
            dataMap.put("values", ModbusDataParser.parseRegisters(payload));
        } else if (fc == 1 || fc == 2) {
            // 线圈解析
            dataMap.put("values", ModbusDataParser.parseBits(payload));
        } else {
            // 写入类响应通常返回起始地址和写入值，原样输出
            dataMap.put("raw", ByteBufUtil.hexDump(payload));
        }


        // 3. 执行分发
        String json = JSON.toJSONString(dataMap);

        // 3. 执行分发
        executeSend(json, device.getId());
    }

    private void executeSend(String json, String deviceId) {
        switch (outputMode.toLowerCase()) {
            case "redis":
                if (redisTemplate != null) {
                    redisTemplate.opsForValue().set("MODBUS:RT:" + deviceId, json);
                }
                break;
            case "rabbitmq":
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend("modbus.exchange", "modbus.key", json);
                }
                break;
            default:
                log.info("收到数据:" + json);
        }
    }

    private void handleModbusException(DeviceConfig device, ModbusFrame response) {
        int errCode = response.getPayload()[0] & 0xFF;
        log.warn("设备 {} 响应异常码: {} (可能地址越界或从站繁忙)", device.getId(), errCode);
    }
}
