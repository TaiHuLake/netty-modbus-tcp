package com.netty.core.handler;

import com.netty.core.ModbusFrame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:02
 * {@code @description:}
 */
@Slf4j
public class ModbusResponseHandler extends SimpleChannelInboundHandler<ModbusFrame> {

    // 全局静态映射表：Key = TransactionId
    public static final Map<Short, CompletableFuture<ModbusFrame>> futures = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ModbusFrame msg) {
        // 收到响应，根据事务ID找到对应的 Future 并唤醒
        CompletableFuture<ModbusFrame> future = futures.remove(msg.getTransactionId());
        if (future != null) {
            future.complete(msg);
        }
    }

//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        InetSocketAddress add = (InetSocketAddress) ctx.channel().remoteAddress();
//        Integer port = add.getPort();
//        String ip = add.getAddress().getHostAddress();
//        if (msg instanceof ModbusFrame) {
////            log.info("\n  {}:{}【成功解析对象】: \n {}", ip, port, msg);
//            System.out.println(ip + ":" + port + " 【成功解析对象】: " + msg);
//        } else if (msg instanceof ByteBuf) {
//            log.info("\n {}:{}【收到原始字节】: \n {}", ip, port, ByteBufUtil.hexDump((ByteBuf) msg));
//        }
//        super.channelRead(ctx, msg);
//    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
