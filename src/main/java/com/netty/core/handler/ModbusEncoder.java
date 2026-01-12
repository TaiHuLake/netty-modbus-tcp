package com.netty.core.handler;

import com.netty.core.ModbusFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 20:53
 * {@code @description:}
 */
public class ModbusEncoder extends MessageToByteEncoder<ModbusFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ModbusFrame msg, ByteBuf out) {
        out.writeShort(msg.getTransactionId());
        out.writeShort(msg.getProtocolId());
        // Length = UnitId(1) + FunctionCode(1) + Payload(n)
        out.writeShort(2 + msg.getPayload().length);
        out.writeByte(msg.getUnitId());
        out.writeByte(msg.getFunctionCode());
        out.writeBytes(msg.getPayload());
    }
}