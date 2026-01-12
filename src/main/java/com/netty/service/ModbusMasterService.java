package com.netty.service;
import com.netty.core.common.DeviceConfig;
import com.netty.core.common.ModbusTask;
import com.netty.manager.ConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;


/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:03
 * {@code @description:}
 */

@Service
@Slf4j
public class ModbusMasterService {

    @Autowired
    private ConnectionManager connectionManager;

    // 线程池调度器，用于处理 500 个设备的定时轮询请求
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    // Key: deviceKey (ip:port:slaveId), Value: 正在运行的调度任务引用
    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    // 防止同一个设备并发执行
    private final Set<String> processingDevices = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @PostConstruct
    public void init() {
        // 设置线程池大小，500个设备建议 20-50 个调度线程即可（因为是非阻塞发送）
        taskScheduler.setPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        taskScheduler.setThreadNamePrefix("ModbusPoll-");
        taskScheduler.initialize();
    }

    /**
     * 核心功能：动态同步设备列表
     * 满足需求：新增则启动，删除则踢掉，连接池复用
     */
    public synchronized void syncDevices(List<DeviceConfig> newList) {
        if (newList == null) {
            return;
        }

        // 记录当前传入的所有设备 Key
        Set<String> newKeys = new HashSet<>();

        Random random = new Random();
        for (DeviceConfig device : newList) {
            // ip:port:slaveId
            String dKey = device.getIp() + ":" + device.getPort() + ":" + device.getSlaveId();
            newKeys.add(dKey);

            // 如果设备不在运行列表中，则启动它
            if (!runningTasks.containsKey(dKey)) {

                // 生成 0 到 3000ms 之间的随机延迟
                int initialDelay = random.nextInt(3000);

                // 按照设备配置的 interval 启动定时轮询
                ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
                        () -> safePoll(device),
//                        () -> pollDevice(device),
                        // 随机延迟启动
                        new Date(System.currentTimeMillis() + initialDelay),
                        (long) device.getInterval());
                runningTasks.put(dKey, future);
            }
        }

        // 停止并清理那些不在新列表中的设备
        Iterator<Map.Entry<String, ScheduledFuture<?>>> it = runningTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ScheduledFuture<?>> entry = it.next();
            if (!newKeys.contains(entry.getKey())) {
                log.info("设备已移除，停止采集任务: {}", entry.getKey());
                // 停止定时器
                entry.getValue().cancel(true);
                it.remove();
                // 注意：ConnectionManager 中的物理连接会由内部的 channelInactive 自动清理
            }
        }
    }

    private void safePoll(DeviceConfig device) {
        String dKey = device.getIp() + ":" + device.getPort() + ":" + device.getSlaveId();

        // CAS 锁，防止同一个设备被多个线程同时调度
        if (!processingDevices.add(dKey)) {
            // 如果该设备上一次还没跑完，直接跳过本次
            return;
        }

        try {
            pollDevice(device);
        } finally {
            processingDevices.remove(dKey);
        }
    }

    /**
     * 轮询单个设备的所有任务
     */
    private void pollDevice(DeviceConfig device) {
        if (device.getTasks() == null || device.getTasks().isEmpty()) {
            return;
        }

        for (ModbusTask task : device.getTasks()) {
            try {
                // 通过连接管理器发送，内部会自动处理 IP:Port 复用和单链路顺序排队
                connectionManager.send(device, task);
            } catch (Exception e) {
                String deviceKey = device.getIp() + ":" + device.getPort() + ":" + device.getSlaveId();
                log.error("调度设备任务失败: {} - {}", deviceKey, e.getMessage());
            }
        }
    }

    /**
     * 功能码 06
     * 开放接口：手动触发单次写入
     */
    public void writeSingle(DeviceConfig device, int address, int value) {
        ModbusTask writeTask = ModbusTask.builder()
                .type(6)
                .startAddr(address)
                .writeValues(new int[]{value})
                .build();
        connectionManager.send(device, writeTask);
    }

}
