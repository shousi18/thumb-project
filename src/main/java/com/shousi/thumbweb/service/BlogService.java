package com.shousi.thumbweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shousi.thumbweb.model.entity.Blog;
import com.shousi.thumbweb.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author 86172
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-04-20 21:08:45
*/
public interface BlogService extends IService<Blog> {

    /**
     * 根据id获取博客
     * @param blogId
     * @param request
     * @return
     */
    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    /**
     * 获取博客列表
     * @param blogList
     * @param request
     * @return
     */
    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
