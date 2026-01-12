package com.netty.util;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:04
 * {@code @description:}
 */
public class ModbusProtocolUtils {

    /**
     * 通用读取 PDU 构建 (支持 01, 02, 03, 04)
     */
    public static byte[] buildReadPDU(int startAddr, int count) {
        return new byte[]{
                (byte) (startAddr >> 8), (byte) (startAddr & 0xFF),
                (byte) (count >> 8),     (byte) (count & 0xFF)
        };
    }


    // 0x03: 读取保持寄存器请求 Payload
    public static byte[] buildReadRegisters(int startAddr, int quantity) {
        return new byte[]{
                (byte) (startAddr >> 8), (byte) (startAddr & 0xFF),
                (byte) (quantity >> 8),  (byte) (quantity & 0xFF)
        };
    }



    // 0x05: 写单个线圈
    public static byte[] buildWriteSingleCoil(int address, boolean status) {
        return new byte[]{
                (byte) (address >> 8), (byte) (address & 0xFF),
                (byte) (status ? 0xFF : 0x00), (byte) 0x00
        };
    }

    // 0x10: 预置多个寄存器请求 Payload
    public static byte[] buildWriteMultipleRegisters(int startAddr, int[] values) {
        int quantity = values.length;
        int byteCount = quantity * 2;
        byte[] payload = new byte[5 + byteCount];

        payload[0] = (byte) (startAddr >> 8);
        payload[1] = (byte) (startAddr & 0xFF);
        payload[2] = (byte) (quantity >> 8);
        payload[3] = (byte) (quantity & 0xFF);
        payload[4] = (byte) byteCount;

        for (int i = 0; i < quantity; i++) {
            payload[5 + i * 2] = (byte) (values[i] >> 8);
            payload[6 + i * 2] = (byte) (values[i] & 0xFF);
        }
        return payload;
    }
}
