package com.shousi.thumbweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shousi.thumbweb.constant.ThumbConstant;
import com.shousi.thumbweb.manager.cache.CacheManager;
import com.shousi.thumbweb.mapper.ThumbMapper;
import com.shousi.thumbweb.model.dto.thumb.DoThumbRequest;
import com.shousi.thumbweb.model.entity.Blog;
import com.shousi.thumbweb.model.entity.Thumb;
import com.shousi.thumbweb.model.entity.User;
import com.shousi.thumbweb.service.BlogService;
import com.shousi.thumbweb.service.ThumbService;
import com.shousi.thumbweb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author 86172
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-04-20 21:08:45
 */
@Service("thumbService")
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private BlogService blogService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CacheManager cacheManager;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        synchronized (loginUser.getId().toString().intern()) {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                // 判断是否点赞
                boolean result = this.hasThumb(blogId, loginUser.getId());
                if (result) {
                    throw new RuntimeException("用户已点赞");
                }
                // 没有点赞则进行点赞
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();
                Thumb thumb = new Thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setBlogId(blogId);
                boolean success = update && this.save(thumb);
                // 数据库保存成功
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    // 将点赞记录存入redis
                    redisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                    // 存入本地缓存
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                }
                // 更新成功才执行
                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        synchronized (loginUser.getId().toString().intern()) {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
                if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                    throw new RuntimeException("用户未点赞");
                }

                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                boolean success = update && this.removeById((Long) thumbIdObj);
                // 将点赞记录从redis中删除
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                // 更新成功才执行
                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
        if (thumbIdObj == null) {
            return false;
        }
        Long thumbId = (Long) thumbIdObj;
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }
}




