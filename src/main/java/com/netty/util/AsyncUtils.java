package com.netty.util;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@code @author:} TaiHuLake
 * {@code @date:} 2026-01-11 21:28
 * {@code @description:}
 */
public class AsyncUtils {
    // 全局唯一时间轮，性能远高于传统的 Timer
    private static final HashedWheelTimer TIMER = new HashedWheelTimer();

    /**
     * 为 CompletableFuture 注入超时逻辑 (JDK 8 兼容)
     */
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long delay, TimeUnit unit) {
        Timeout timeout = TIMER.newTimeout(t -> {
            if (!future.isDone()) {
                future.completeExceptionally(new java.util.concurrent.TimeoutException("Modbus request timed out!"));
            }
        }, delay, unit);

        // 如果任务在超时前完成了，取消定时任务
        future.whenComplete((res, ex) -> timeout.cancel());
        return future;
    }
}
