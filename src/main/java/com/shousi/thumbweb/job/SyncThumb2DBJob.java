package com.shousi.thumbweb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shousi.thumbweb.mapper.BlogMapper;
import com.shousi.thumbweb.model.entity.Thumb;
import com.shousi.thumbweb.model.enums.ThumbTypeEnum;
import com.shousi.thumbweb.service.ThumbService;
import com.shousi.thumbweb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务：将 Redis中的临时点赞数据同步到数据库
 */
@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private ThumbService thumbService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private BlogMapper blogMapper;

    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {
        log.info("定时任务：将 Redis中的临时点赞数据同步到数据库");
        DateTime nowDate = DateUtil.date();
        // 如果秒数为0~9 则回到上一分钟的50秒
        int second = (DateUtil.second(nowDate) / 10 - 1) * 10;
        if (second == -10) {
            second = 50;
            // 回到上一分钟
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }
        String timeSlice = DateUtil.format(nowDate, "HH:mm:") + second;
        syncThumb2DBByDate(timeSlice);
        log.info("同步完成，当前时间片：{}", timeSlice);
    }

    public void syncThumb2DBByDate(String timeSlice) {
        // 获取到临时点赞和取消点赞数据
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEntry = CollUtil.isEmpty(allTempThumbMap);

        // 同步点赞到数据库
        if (thumbMapEntry) {
            return;
        }
        // 统计博客点赞数量
        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        // 批量插入点赞记录 列表
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;
        for (Object userIdBlogIdObj : allTempThumbMap.keySet()) {
            String userIdBlogId = userIdBlogIdObj.toString();
            String[] split = userIdBlogId.split(StrPool.COLON);
            Long userId = Long.valueOf(split[0]);
            Long blogId = Long.valueOf(split[1]);
            // -1:取消点赞  1:点赞
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());
            if (thumbType == ThumbTypeEnum.INCR.getValue()) {
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                thumbList.add(thumb);
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {
                // 批量删除
                needRemove = true;
                queryWrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);
            } else {
                if (thumbType != ThumbTypeEnum.NON.getValue()) {
                    log.warn("点赞类型错误：{}", thumbType);
                }
                continue;
            }
            // 计算点赞数量
            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
        }
        // 批量插入
        thumbService.saveBatch(thumbList);
        // 批量删除
        if (needRemove) {
            thumbService.remove(queryWrapper);
        }
        // 批量更新博客点赞数
        if (!CollUtil.isEmpty(blogThumbCountMap)) {
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);
        }
        // 异步删除
        Thread.startVirtualThread(() -> {
            redisTemplate.delete(tempThumbKey);
        });
    }
}
