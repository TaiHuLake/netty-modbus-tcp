package com.netty.core;

import lombok.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModbusFrame {

    // 事务ID：用于异步匹配响应
    private short transactionId;

    // 协议标识：固定0
    private short protocolId = 0;

    // 从站ID (SlaveId)
    private byte unitId;

    // 功能码 (0x03, 0x10等)
    private byte functionCode;

    // 数据载荷 (起始地址、寄存器数、具体值等)
    private byte[] payload;
}
