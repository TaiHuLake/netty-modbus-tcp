# Modbus TCP 高并发采集系统设计方案

## 1. 架构概览

本系统采用非阻塞 I/O (Netty) 与 内存状态机 (Redis) 相结合的架构，旨在解决 多台 Modbus TCP 设备在同 IP 多从站、高频率轮询下的数据对齐与网络拥塞问题。

### 核心设计思想：

* **读写解耦**：采集端只负责将数据“填入” Redis，不负责复杂的业务逻辑。
* **状态对齐**：通过在 Redis 存储批次时间戳，将数据一致性判断交给业务层。
* **顺序调度**：通过 `ChannelTaskCoordinator` 确保单条 TCP 链路上的请求“一发一收”，防止 TransactionID 冲突。

---

## 2. 核心组件详解

### 2.1 任务调度模块 (`ChannelTaskCoordinator`)

每个物理连接（IP:Port）对应一个 Coordinator，内部维护一个 `BlockingQueue`。

* **顺序保证**：使用 `AtomicBoolean isWaiting` 锁。只有收到响应或超时后，才会触发 `trySendNext()`。
* **间隔补偿**：在两次请求间强制加入 `50ms` 延迟，保护从站协议栈不被冲垮。
* **超时处理**：采用 `CompletableFuture` 配合定时调度，确保单个任务挂起不影响整条链路。

### 2.2 数据解析模块 (`ModbusDataParser`)

严格处理字节对齐，防止位数据（01/02）超发。

| 功能码 | 含义 | 解析单位 | 存储映射前缀 |
| --- | --- | --- | --- |
| **01** | 读线圈 | Bit (1/0) | `0:` |
| **02** | 读离散输入 | Bit (1/0) | `1:` |
| **03** | 读保持寄存器 | Word (16bit) | `4:` |
| **04** | 读输入寄存器 | Word (16bit) | `3:` |

### 2.3 数据分发模块 (`DataRouteDispatcher`)

实现“数据填空”逻辑。

```json lines
// Redis 存储示例 (Hash 结构)
Key: DEVICE:DATA:{deviceId}
Field: "4:1" -> Value: "123"           // 寄存器值
Field: "TS_4:1_100" -> Value: "1768189688000" // 该批次(1-100)更新时间
Field: "_LIVE_TIME" -> Value: "1768189688050" // 设备最后活跃时间
```
## 3. 核心代码实现
### 3.1 采集任务定义
```java
@Data
@Builder
public class ModbusTask {
    private int type;          // 功能码 (1,2,3,4,6,16)
    private int startAddr;     // 起始地址
    private int count;         // 数量
    private int[] writeValues; // 写入值（仅限写入任务）

    // 自动生成的元数据
    private long batchTimestamp;
}
```
### 3.2 数据解析器 (解决位超发)
```java
@Data
@Builder
public class ModbusTask {
    private int type;          // 功能码 (1,2,3,4,6,16)
    private int startAddr;     // 起始地址
    private int count;         // 数量
    private int[] writeValues; // 写入值（仅限写入任务）

    // 自动生成的元数据
    private long batchTimestamp;
}
```
### 3.3 调度控制逻辑
```java
// 在 Coordinator 中的发送逻辑
private void trySendNext() {
    if (queue.isEmpty() || isWaiting.get()) return;

    QueuedTask qTask = queue.poll();
    isWaiting.set(true);

    // 发送报文...

    // 异步等待结果
    future.whenComplete((res, ex) -> {
        // 1. 数据填空到 Redis
        dispatcher.dispatch(qTask.getDevice(), res, qTask.getTask());

        // 2. 释放锁并延迟执行下一条
        isWaiting.set(false);
        channel.eventLoop().schedule(this::trySendNext, 50, TimeUnit.MILLISECONDS);
    });
}
```

## 4.业务侧数据对齐算法
业务后端从 Redis 获取数据时，需执行以下逻辑： 

    1.拉取全量 Hash：HGETALL DEVICE:DATA:A。
    2.获取时间戳集合：筛选所有以 TS_ 开头的 Fields。
    3.对齐校验：
        ·若时间戳一致：数据视为同一采集周期。
        ·若时间戳不一致：数据存在跨周期错位，应保持旧值或标记失效。
    4.离线检查：若 now - _LIVE_TIME > 10000ms，判定设备通讯中断。
## 5.多台设备压力参数建议(尚未测试..建议不超过100+)

---

## 6. 常见故障处理
>Q: 为什么 TransactionID 还是乱跳？  
>A: 检查 ConnectionManager 是否为每个 IP 创建了唯一的 Coordinator。

>Q: 为什么 Redis 数据不更新？  
>A: 检查 ResponseHandler 是否正确移除了已完成的 future，防止 TID 耗尽。

>Q: 采集变慢了？  
>A: 观察 RTT 时间。若单个连接下 Slave 过多，应增加物理网关或缩减单次轮询的任务数。
