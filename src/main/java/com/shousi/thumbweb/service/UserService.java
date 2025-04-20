package com.shousi.thumbweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shousi.thumbweb.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author 86172
* @description 针对表【user】的数据库操作Service
* @createDate 2025-04-20 21:08:45
*/
public interface UserService extends IService<User> {

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
}
