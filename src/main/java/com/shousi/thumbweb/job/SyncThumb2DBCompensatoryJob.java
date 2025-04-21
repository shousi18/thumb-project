package com.shousi.thumbweb.job;

import cn.hutool.core.collection.CollUtil;
import com.shousi.thumbweb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SyncThumb2DBJob syncThumb2DBJob;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        log.info("补偿任务：将 Redis中的临时点赞数据同步到数据库");
        Set<String> thumbKeys = redisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");
        Set<String> needHandleDataSet = new HashSet<>();
        thumbKeys.stream()
                .filter(Objects::nonNull)
                .forEach(thumbKey -> needHandleDataSet.add(thumbKey.replace(RedisKeyUtil.getTempThumbKey(""), "")));

        if (CollUtil.isEmpty(needHandleDataSet)) {
            log.info("补偿任务：暂无需要处理的数据");
            return;
        }
        for (String timeSlice : needHandleDataSet) {
            syncThumb2DBJob.syncThumb2DBByDate(timeSlice);
            log.info("补偿任务：同步完成，当前时间片：{}", timeSlice);
        }
        log.info("补偿任务：同步完成");
    }
}
