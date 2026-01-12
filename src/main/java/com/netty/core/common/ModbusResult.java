package com.netty.core.common;

import lombok.Data;

import java.util.List;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 23:27
 * {@code @description:}
 */
@Data
public class ModbusResult {

    private int slaveId;

    private int fc;

    private long timestamp;

    private String deviceId;

//    private int[] values;
    private List<Integer> values;
}
