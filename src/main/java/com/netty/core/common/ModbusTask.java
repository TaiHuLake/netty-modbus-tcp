package com.netty.core.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:09
 * {@code @description:}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModbusTask {
    // 任务唯一标识
    private String taskId;

    // 功能码: 3-读寄存器, 6-写单个, 16-写多个, 1-读线圈
    private int type;

    // 起始地址
    private int startAddr;

    // 寄存器数量/线圈数量
    private int count;

    // 如果是写操作，存储待写入的数值
    private int[] writeValues;

    // 快速构造读取任务
    public static ModbusTask read(int startAddr, int count) {
        return ModbusTask.builder().type(3).startAddr(startAddr).count(count).build();
    }
}
