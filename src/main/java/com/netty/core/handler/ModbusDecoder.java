package com.netty.core.handler;

import com.netty.core.ModbusFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 20:53
 * {@code @description:}
 */
public class ModbusDecoder extends LengthFieldBasedFrameDecoder {
    // 参数说明：最大包长1024, 长度字段偏移量4, 长度字段2字节, 修正0, 跳过0字节直接读
    public ModbusDecoder() {
        super(1024, 4, 2, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            short tid = frame.readShort();
            short pid = frame.readShort();
            short len = frame.readShort();
            byte uid = frame.readByte();
            byte fc = frame.readByte();

            byte[] payload = new byte[frame.readableBytes()];
            frame.readBytes(payload);
            return new ModbusFrame(tid, pid, uid, fc, payload);
        } finally {
            frame.release();
        }
    }
}