package com.netty.controller;

import com.netty.core.common.DeviceConfig;
import com.netty.service.ModbusMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:17
 * {@code @description:}
 */
@RestController
@Tag(name = "测试")
@RequestMapping("/api/modbus")
public class ModbusController {

    @Autowired
    private ModbusMasterService masterService;

    /**
     * 全量同步设备列表
     * 逻辑：传入的 List 是“当前应该运行的所有设备”
     * 已经在跑的且在 List 里的：保持不动
     * 已经在跑但不在 List 里的：停止并移除
     * 不在跑但在 List 里的：新建连接并启动轮询
     */
    @PostMapping("/sync")
    @Operation(summary = "插入设备")
    public String syncDevices(@RequestBody List<DeviceConfig> devices) {
        masterService.syncDevices(devices);
        return "Sync processed. Current active devices: " + devices.size();
    }

//    /**
//     * 单个设备立即写入指令示例
//     * 场景：不参与轮询，手动触发一次写操作
//     */
//    @PostMapping("/write")
//    @Operation(summary = "写入")
//    public CompletableFuture<String> write(@RequestParam String ip, @RequestParam int port,
//                                           @RequestParam byte slaveId, @RequestParam int addr, @RequestParam int val) {
//        return masterService.writeSingle(ip, port, slaveId, addr, val)
//                .thenApply(res -> "Write Success")
//                .exceptionally(ex -> "Write Failed: " + ex.getMessage());
//    }
}
