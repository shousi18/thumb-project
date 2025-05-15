package com.shousi.thumbweb.manager.cache;

import cn.hutool.core.util.ObjectUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {
    private TopK hotKeyDetector;
    private Cache<String, Object> localCache;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Bean
    public TopK getHotKeyDetector() {
        hotKeyDetector = new HeavyKeeper(
                // 监控 Top 100 Key  
                100,
                // 宽度  
                100000,
                // 深度  
                5,
                // 衰减系数  
                0.92,
                // 最小出现 10 次才记录  
                10
        );
        return hotKeyDetector;
    }

    @Bean
    public Cache<String, Object> localCache() {
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public Object get(String hashKey, String key) {
        // 构造唯一的复合键
        String compositeKey = buildCacheKey(hashKey, key);

        // 1.先查本地缓存
        Object value = localCache.getIfPresent(compositeKey);
        if (value != null) {
            log.info("从本地缓存中获取数据：{} = {}", compositeKey, value);
            // 记录访问次数
            hotKeyDetector.add(compositeKey, 1);
            return value;
        }

        // 2.本地缓存未命中，查询 Redis
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        log.info("本地缓存未命中，查询 Redis = {}", redisValue);
        if (redisValue == null) {
            return null;
        }

        // 3.记录访问
        AddResult addResult = hotKeyDetector.add(compositeKey, 1);

        // 4.如果是热点 Key 并且不在本地缓存，则缓存数据
        if (addResult.isHotKey()) {
            localCache.put(compositeKey, redisValue);
        }

        return redisValue;
    }

    /**
     * 更新本地缓存
     *
     * @param hashKey
     * @param key
     * @param value
     */
    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = buildCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        if (object == null) {
            return;
        }
        localCache.put(compositeKey, value);
    }

    /**
     * 定期清理过期的热点 Key（20分钟）
     */
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.MINUTES)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }

    /**
     * 构造复合 key
     *
     * @param hashKey
     * @param key
     * @return
     */
    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }
}
