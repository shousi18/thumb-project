package com.shousi.thumbweb.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shousi.thumbweb.constant.RedisLuaScriptConstant;
import com.shousi.thumbweb.constant.ThumbConstant;
import com.shousi.thumbweb.mapper.ThumbMapper;
import com.shousi.thumbweb.model.dto.thumb.DoThumbRequest;
import com.shousi.thumbweb.model.entity.Thumb;
import com.shousi.thumbweb.model.entity.User;
import com.shousi.thumbweb.model.enums.LuaStatusEnum;
import com.shousi.thumbweb.service.ThumbService;
import com.shousi.thumbweb.service.UserService;
import com.shousi.thumbweb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * @author 86172
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-04-20 21:08:45
 */
@Service("thumbServiceRedis")
@Slf4j
public class ThumbRedisServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        String timeSlice = getTimeSlice();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 执行lua脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );

        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户已点赞");
        }

        // 更新成功才执行
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        String timeSlice = getTimeSlice();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 执行lua脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );

        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户未点赞");
        }

        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    private String getTimeSlice() {
        DateTime nowDate = DateUtil.date();
        // 获取到到当前时间前最近的整数秒，比如当前时间为 11:20:33 ，则返回 11:20:30
        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId);
    }
}




