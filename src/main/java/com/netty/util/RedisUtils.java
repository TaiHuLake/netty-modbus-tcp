package com.netty.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author TaiHuLake
 * {@code @description} Redis工具类
 * 封装了Spring Data Redis的常用操作，支持String/Hash/Set/List等数据结构
 * 以及分布式锁功能，提供线程安全的Redis操作接口
 */
@Component
public class RedisUtils {

    private static final Logger log = LoggerFactory.getLogger(RedisUtils.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    // ==================== 通用操作 ====================

    /**
     * 设置键的过期时间（秒）
     * @param key      Redis键名
     * @param time     过期时间值（必须大于0）
     * @param unit     时间单位（如SECONDS/Minutes等）
     * true 成功设置过期时间，false 失败或time<=0
     */
    public void expire(String key, long time, TimeUnit unit) {
        if (time <= 0) {
            return;
        }
        try {
            redisTemplate.expire(key, time, unit);
        } catch (Exception e) {
            log.error("expire error", e);
        }
    }

    /**
     * 获取键的剩余过期时间（秒）
     * @param key Redis键名
     * 剩余时间（秒），-2表示异常
     */
    public long getExpire(String key) {
        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("getExpire error", e);
            return -2L;
        }
    }

    /**
     * 判断键是否存在
     * @param key Redis键名
     * true 存在，false 不存在或异常
     */
    public boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("hasKey error", e);
            return false;
        }
    }

    /**
     * 删除多个键
     * @param keys 要删除的键数组（可为null）
     */
    public void del(String... keys) {
        if (keys != null && keys.length > 0) {
            redisTemplate.delete(Arrays.asList(keys));
        }
    }

    // ==================== String ====================

    /**
     * 获取键对应的值（String类型）
     * @param key Redis键名
     * 键对应的值，key为null时返回null
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置键值对（无过期时间）
     * @param key Redis键名
     * @param value 值（Object类型）
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("set error", e);
        }
    }

    /**
     * 设置键值对并指定过期时间
     * @param key      Redis键名
     * @param value    值（Object类型）
     * @param time     过期时间值（>0时生效）
     * @param unit     时间单位
     * @return true 成功，false 失败
     */
    public boolean set(String key, Object value, long time, TimeUnit unit) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, unit);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("set error", e);
            return false;
        }
    }

    /**
     * 递增数值（支持负数递减）
     * @param key   Redis键名
     * @param delta 递增值（不能为0）
     * @return 递增后的结果值
     */
    public long incr(String key, long delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("delta must not be 0");
        }
        try {
            Long increment = redisTemplate.opsForValue().increment(key, delta);
            if (increment == null) {
                return 0;
            }
            return increment;
        } catch (Exception e) {
            log.error("incr error", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== Hash ====================
    /**
     * 获取Hash结构中指定字段的值
     * @param key   Redis键名
     * @param field Hash字段名
     * 字段对应的值
     */
    public Object hget(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 获取整个Hash结构的所有字段值
     * @param key Redis键名
     * Map形式的Hash数据（key为字段名，value为值）
     */
    public Map<Object, Object> hmget(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 批量设置Hash结构的字段值并指定过期时间
     * @param key      Redis键名
     * @param map      字段值Map（String->Object）
     * @param time     过期时间值（>0时生效）
     * @param unit     时间单位
     * 成功，false 失败
     */
    public boolean hmset(String key, Map<String, Object> map, long time, TimeUnit unit) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                expire(key, time, unit);
            }
            return true;
        } catch (Exception e) {
            log.error("hmset error", e);
            return false;
        }
    }

    // ==================== Set ====================

    /**
     * 获取Set集合所有元素
     * @param key Redis键名
     * Set集合（空Set表示异常或无数据）
     */
    public Set<Object> sGet(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("sGet error", e);
            return Collections.emptySet();
        }
    }

    /**
     * 向Set集合添加多个元素并指定过期时间
     * @param key      Redis键名
     * @param time     过期时间值（>0时生效）
     * @param unit     时间单位
     * @param values   要添加的元素数组
     * 添加的元素数量
     */
    public long sSet(String key, long time, TimeUnit unit, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                expire(key, time, unit);
            }
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("sSet error", e);
            return 0;
        }
    }

    // ==================== List ====================

    /**
     * 获取List区间元素
     * @param key   Redis键名
     * @param start 起始索引（0开始）
     * @param end   结束索引（-1表示最后一个）
     * 指定区间的元素列表（空列表表示异常或无数据）
     */
    public List<Object> lGet(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            log.error("lGet error", e);
            return Collections.emptyList();
        }
    }

    /**
     * 向List尾部添加元素并指定过期时间
     * @param key      Redis键名
     * @param value    要添加的元素
     * @param time     过期时间值（>0时生效）
     * @param unit     时间单位
     * true 成功，false 失败
     */
    public boolean lRightPush(String key, Object value, long time, TimeUnit unit) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0) {
                expire(key, time, unit);
            }
            return true;
        } catch (Exception e) {
            log.error("lRightPush error", e);
            return false;
        }
    }

    // ==================== Keys ====================

    /**
     * 模糊匹配获取Redis键（使用SCAN命令）
     * @param pattern 匹配模式（如"user:*"）
     * 符合条件的键集合（空集合表示异常）
     */
    public Set<String> scan(String pattern) {
        try {
            Set<String> keys = new HashSet<>();
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                Cursor<byte[]> cursor = connection.scan(
                        ScanOptions.scanOptions().match(pattern).count(1000).build()
                );
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
                return null;
            });
            return keys;
        } catch (Exception e) {
            log.error("scan error", e);
            return Collections.emptySet();
        }
    }

    // ==================== 分布式锁 ====================
    /**
     * 尝试获取分布式锁（SETNX操作）
     * @param key      锁的Redis键名
     * @param value    锁的唯一标识（建议使用UUID）
     * @param expireSeconds 锁的过期时间（秒）
     * true 获取成功，false 获取失败（可能已被占用或异常）
     */
    public boolean tryLock(String key, String value, long expireSeconds) {
        try {
            return Boolean.TRUE.equals(redisTemplate.execute(
                    (RedisCallback<Boolean>) connection -> {
                        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
                        return connection.set(
                                Objects.requireNonNull(serializer.serialize(key)),
                                Objects.requireNonNull(serializer.serialize(value)),
                                Expiration.seconds(expireSeconds),
                                RedisStringCommands.SetOption.SET_IF_ABSENT
                        );
                    }
            ));
        } catch (Exception e) {
            log.error("tryLock error", e);
            return false;
        }
    }


    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "   return redis.call('del', KEYS[1]) " +
                    "else return 0 end";
    /**
     * 释放分布式锁（使用Lua脚本保证原子性）
     * @param key      锁的Redis键名
     * @param value    锁的唯一标识（必须与加锁时一致）
     * true 释放成功（锁属于当前线程），false 释放失败（锁已被占用或异常）
     */
    public boolean unlock(String key, String value) {
        try {
            Long result = redisTemplate.execute(
                    (RedisCallback<Long>) connection -> connection.eval(
                            UNLOCK_SCRIPT.getBytes(),
                            ReturnType.INTEGER,
                            1,
                            key.getBytes(),
                            value.getBytes()
                    )
            );
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("unlock error", e);
            return false;
        }
    }
}
