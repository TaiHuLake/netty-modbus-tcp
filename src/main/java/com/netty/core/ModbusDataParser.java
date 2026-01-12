package com.netty.core;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 22:31
 * {@code @description:}
 */
public class ModbusDataParser {

    /**
     * 解析线圈/离散输入数据 (功能码 01, 02)
     * 响应格式: [字节计数(1b), 位数据(nb)...]
     * 注意：位数据在字节内是从低位到高位排列的
     */
    public static boolean[] parseBits(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return new boolean[0];
        }
        int byteCount = payload[0] & 0xFF;
        boolean[] bits = new boolean[byteCount * 8];

        for (int i = 0; i < byteCount; i++) {
            byte b = payload[i + 1];
            for (int j = 0; j < 8; j++) {
                // Modbus 规范：字节内的第一位（最低位）对应起始地址
                bits[i * 8 + j] = ((b >> j) & 0x01) == 1;
            }
        }
        return bits;
    }

    public static Object[] parseBits(byte[] payload, int count) {
        // payload[0] 是字节计数，payload[1] 开始是数据
        Object[] bits = new Object[count];
        for (int i = 0; i < count; i++) {
            // 第几个字节
            int byteIdx = i / 8;
            // 字节内的第几位
            int bitIdx = i % 8;

            // 这里的 payload[byteIdx + 1] 跳过第一个字节计数位
            int val = (payload[byteIdx + 1] & (1 << bitIdx)) != 0 ? 1 : 0;

            // 根据你想要的结果格式，存为 Boolean 或 Integer
            bits[i] = (val == 1);
        }
        return bits;
    }

    /**
     * 解析寄存器数据 (功能码 03, 04)
     * 响应格式: [字节计数(1b), 数据H(1b), 数据L(1b), ...]
     */
    public static int[] parseRegisters(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return new int[0];
        }
        // 第一个字节是字节长度，后面是数据
        int byteCount = payload[0] & 0xFF;
        int registerCount = byteCount / 2;
        int[] result = new int[registerCount];

        for (int i = 0; i < registerCount; i++) {
            // 拼接两个字节为一个 16 位整数 (大端序)
            // ((高位 & 0xFF) << 8) | (低位 & 0xFF)
            result[i] = ((payload[i * 2 + 1] & 0xFF) << 8) | (payload[i * 2 + 2] & 0xFF);
        }
        return result;
    }

    /**
     * 高级解析：将两个连续寄存器转为 32 位浮点数 (Float)
     * 常用在电力仪表、流量计
     */
    public static float registersToFloat(int high, int low) {
        int combined = (high << 16) | (low & 0xFFFF);
        return Float.intBitsToFloat(combined);
    }
}