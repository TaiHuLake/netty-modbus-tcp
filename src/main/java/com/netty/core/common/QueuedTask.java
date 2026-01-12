package com.netty.core.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 22:18
 * {@code @description:}
 */
@Data
@AllArgsConstructor
public class QueuedTask {
    private DeviceConfig device;
    private ModbusTask task;
}
