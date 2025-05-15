package com.shousi.thumbweb.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shousi.thumbweb.constant.ThumbConstant;
import com.shousi.thumbweb.mapper.BlogMapper;
import com.shousi.thumbweb.model.entity.Blog;
import com.shousi.thumbweb.model.entity.User;
import com.shousi.thumbweb.model.vo.BlogVO;
import com.shousi.thumbweb.service.BlogService;
import com.shousi.thumbweb.service.ThumbService;
import com.shousi.thumbweb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 86172
 * @description 针对表【blog】的数据库操作Service实现
 * @createDate 2025-04-20 21:08:45
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
        implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(loginUser)) {
            List<Object> blogIdList = blogList.stream().map(blog -> blog.getId().toString()).collect(Collectors.toList());
            // 获取该用户点赞的博客
            // hmget 有值返回值 没有值返回null
            List<Object> thumbList = redisTemplate.opsForHash().multiGet(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogIdList);
            for (int i = 0; i < thumbList.size(); i++) {
                if (thumbList.get(i) == null) {
                    continue;
                }
                blogIdHasThumbMap.put(Long.valueOf(blogIdList.get(i).toString()), true);
            }
        }

        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumbMap.get(blog.getId()));
                    return blogVO;
                })
                .toList();
    }


    private BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

        if (loginUser == null) {
            return blogVO;
        }

        // 如果用户登录，则添加到点赞表中
        Boolean exist = thumbService.hasThumb(blog.getId(), loginUser.getId());
        blogVO.setHasThumb(exist);
        return blogVO;
    }
}




