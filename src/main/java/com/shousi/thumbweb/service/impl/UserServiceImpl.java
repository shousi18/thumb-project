package com.shousi.thumbweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shousi.thumbweb.constant.UserConstant;
import com.shousi.thumbweb.mapper.UserMapper;
import com.shousi.thumbweb.model.entity.User;
import com.shousi.thumbweb.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author 86172
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-04-20 21:08:45
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }
}




