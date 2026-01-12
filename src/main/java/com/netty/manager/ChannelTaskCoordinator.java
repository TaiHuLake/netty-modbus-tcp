package com.netty.manager;

import com.netty.core.ModbusFrame;
import com.netty.core.common.DeviceConfig;
import com.netty.core.common.ModbusTask;
import com.netty.core.common.QueuedTask;
import com.netty.core.handler.ModbusResponseHandler;
import com.netty.service.DataRouteDispatcher;
import com.netty.util.AsyncUtils;
import com.netty.util.ModbusProtocolUtils;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 22:17
 * {@code @description:}
 */
@Slf4j
public class ChannelTaskCoordinator {
    private final Channel channel;
    // 该连接专属的异步任务队列
    private final BlockingQueue<QueuedTask> queue = new LinkedBlockingQueue<>();
    // 锁：标记当前连接是否正在等待回包
    private final AtomicBoolean isWaiting = new AtomicBoolean(false);
    // 事务ID生成器
    private final AtomicInteger tidGenerator = new AtomicInteger(0);

    public ChannelTaskCoordinator(Channel channel) {
        this.channel = channel;
    }

    public void enqueue(DeviceConfig device, ModbusTask task) {

        // 检查队列里是否已经有这个任务了（防止堆积）
        // 只有在队列长度小于阈值（例如 50）时才添加，或者按任务唯一标识去重
        if (queue.size() > 100) {
            log.warn("IP:{} 任务队列积压过载，舍弃旧请求", device.getIp());
            queue.clear(); // 强制清理，防止雪崩
        }

        queue.offer(new QueuedTask(device, task));
        trySendNext();
    }

    private synchronized void trySendNext() {
        // 如果正在等待响应，或者队列为空，则跳过（由下一个回调触发）
        if (isWaiting.get() || queue.isEmpty()) {
            return;
        }

        QueuedTask qTask = queue.poll();
        if (qTask == null) {
            return;
        }

        // 上锁
        isWaiting.set(true);

        short tid = (short) (tidGenerator.incrementAndGet() & 0xFFFF);

        // 构建 PDU 并封装成 Frame
        byte[] pdu = buildPdu(qTask.getTask());
        ModbusFrame frame = ModbusFrame.builder()
                .transactionId(tid)
                .unitId(qTask.getDevice().getSlaveId())
                .functionCode((byte) qTask.getTask().getType())
                .payload(pdu)
                .build();

        CompletableFuture<ModbusFrame> future = new CompletableFuture<>();
        ModbusResponseHandler.futures.put(tid, future);

        // 使用之前提供的 AsyncUtils (JDK8) 增加超时控制
        AsyncUtils.withTimeout(future, 5000, TimeUnit.MILLISECONDS).whenComplete((res, ex) -> {
            // 1. 无论成功失败，第一时间清理事务 ID，防止内存泄漏和 ID 冲突
            ModbusResponseHandler.futures.remove(tid);
            try {
                if (ex != null) {
                    log.error("IP:{} Slave:{} 请求失败:{}",
                            qTask.getDevice().getIp(), qTask.getDevice().getSlaveId(), ex.getMessage());
                } else {
                    // 数据分发（这里改为你之前的 Dispatcher）
                    DataRouteDispatcher.staticDispatch(qTask.getDevice(), res, qTask.getTask());
                }
            } finally {
                // 2. 释放当前通道的“忙碌”状态
                isWaiting.set(false);

                // 3. 【核心步骤 2】延迟 50ms 再发送下一个请求
                // 给从站（Slave）协议栈留出处理缓冲区的时间，防止“连珠炮”式请求压垮从站
                channel.eventLoop().schedule(this::trySendNext, 50, TimeUnit.MILLISECONDS);
            }
        });

        channel.writeAndFlush(frame);
    }

    // 适配各功能码的 PDU 构建
    private byte[] buildPdu(ModbusTask task) {
        // 使用之前实现的 ModbusProtocolUtils
        switch (task.getType()) {
            case 1:
                return ModbusProtocolUtils.buildReadPDU(task.getStartAddr(), task.getCount());
            case 2:
                return ModbusProtocolUtils.buildReadPDU(task.getStartAddr(), task.getCount());
            case 3:
                return ModbusProtocolUtils.buildReadPDU(task.getStartAddr(), task.getCount());
            case 4:
                return ModbusProtocolUtils.buildReadPDU(task.getStartAddr(), task.getCount());
            case 5:
                return ModbusProtocolUtils.buildWriteSingleCoil(task.getStartAddr(), task.getWriteValues()[0] == 1);
//            case 6:
//                // 增加判断，防止写入值为空
//                int val = (task.getWriteValues() != null && task.getWriteValues().length > 0)
//                        ? task.getWriteValues()[0] : 0;
//                return ModbusProtocolUtils.buildWriteMultipleRegisters(task.getStartAddr(), task.getWriteValues()[0]);
            case 16:
                int[] vals = (task.getWriteValues() != null) ? task.getWriteValues() : new int[0];
                return ModbusProtocolUtils.buildWriteMultipleRegisters(task.getStartAddr(), vals);
            default:
                // 默认兜底读取逻辑
                return ModbusProtocolUtils.buildReadRegisters(task.getStartAddr(), Math.max(1, task.getCount()));
        }
    }

    public void clear() {
        queue.clear();
        isWaiting.set(false);
    }
}