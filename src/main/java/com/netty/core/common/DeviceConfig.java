package com.netty.core.common;

import lombok.Data;

import java.util.List;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:01
 * {@code @description:}
 */
@Data
public class DeviceConfig {
    // 逻辑ID
    private String id;
    private String ip;
    private int port;
    private byte slaveId;
    // 轮询间隔(ms)
    private int interval;

    private List<ModbusTask> tasks;
}
