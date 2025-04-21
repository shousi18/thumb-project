package com.shousi.thumbweb.service;

import com.shousi.thumbweb.model.dto.thumb.DoThumbRequest;
import com.shousi.thumbweb.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author 86172
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-04-20 21:08:45
*/
public interface ThumbService extends IService<Thumb> {

    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 是否已点赞
     * @param blogId
     * @param userId
     * @return
     */
    Boolean hasThumb(Long blogId, Long userId);
}
