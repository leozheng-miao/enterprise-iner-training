package com.leo.enterpriseinertraining.user.service;

import com.leo.enterpriseinertraining.user.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.user.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.user.vo.LoginVO;
import com.leo.enterpriseinertraining.user.vo.UserVO;

public interface UserService {
    UserVO register(UserRegisterRequest req);
    LoginVO login(UserLoginRequest req);
}
