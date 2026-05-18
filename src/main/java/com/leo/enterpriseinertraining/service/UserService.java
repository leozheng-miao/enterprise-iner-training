package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.vo.LoginVO;
import com.leo.enterpriseinertraining.vo.UserVO;

public interface UserService {
    UserVO register(UserRegisterRequest req);
    LoginVO login(UserLoginRequest req);
}
