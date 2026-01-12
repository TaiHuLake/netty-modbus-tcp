package com.netty.manager;

import com.netty.core.common.DeviceConfig;
import com.netty.core.common.ModbusTask;
import com.netty.core.handler.ModbusDecoder;
import com.netty.core.handler.ModbusEncoder;
import com.netty.core.handler.ModbusResponseHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:01
 * {@code @description:}
 */
@Component
@Slf4j
public class ConnectionManager {
    // Key: "ip:port"
    private final Map<String, ChannelTaskCoordinator> coordinatorMap = new ConcurrentHashMap<>();
    private final EventLoopGroup group = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

    public void send(DeviceConfig device, ModbusTask task) {
        String key = device.getIp() + ":" + device.getPort();
        ChannelTaskCoordinator coordinator = coordinatorMap.get(key);

        if (coordinator == null) {
            synchronized (this) {
                coordinator = coordinatorMap.get(key);
                if (coordinator == null) {
                    try {
                        Channel ch = connect(device.getIp(), device.getPort());
                        coordinator = new ChannelTaskCoordinator(ch);
                        coordinatorMap.put(key, coordinator);
                    } catch (Exception e) {
                        log.error("无法连接到 {}:{}", device.getIp(), device.getPort());
                        return;
                    }
                }
            }
        }
        coordinator.enqueue(device, task);
    }

    private Channel connect(String ip, int port) throws Exception {
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ModbusDecoder(), new ModbusEncoder(), new ModbusResponseHandler());
                        // 监听链路断开
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                String key = ip + ":" + port;
                                log.warn("链路断开: {}", key);
                                ChannelTaskCoordinator c = coordinatorMap.remove(key);
                                if (c != null) {
                                    c.clear();
                                }
                            }
                        });
                    }
                });
        return b.connect(ip, port).sync().channel();
    }
}