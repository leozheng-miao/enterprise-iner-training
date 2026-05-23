package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.exception.ThrowUtils;
import com.leo.enterpriseinertraining.security.JwtUtils;
import com.leo.enterpriseinertraining.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.entity.User;
import com.leo.enterpriseinertraining.mapper.UserMapper;
import com.leo.enterpriseinertraining.service.UserService;
import com.leo.enterpriseinertraining.vo.LoginVO;
import com.leo.enterpriseinertraining.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.leo.enterpriseinertraining.entity.table.UserTableDef.USER;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    @Transactional
    public UserVO register(UserRegisterRequest req) {
        long existing = userMapper.selectCountByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(req.getUsername())));
        ThrowUtils.throwIf(existing > 0, ErrorCode.USER_EXIST);

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null ? req.getUsername() : req.getNickname());
        user.setRole("USER");
        user.setStatus(1);
        // 占位：未指定租户时先填 0，insert 后用自增 id 回填，保证每个新用户默认独占一个租户
        user.setTenantId(req.getTenantId() != null ? req.getTenantId() : 0L);
        userMapper.insert(user);

        if (req.getTenantId() == null) {
            User upd = new User();
            upd.setId(user.getId());
            upd.setTenantId(user.getId());
            userMapper.update(upd);            // 仅更新 tenant_id（MyBatis-Flex 默认忽略 null 字段）
            user.setTenantId(user.getId());
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public LoginVO login(UserLoginRequest req) {
        User user = userMapper.selectOneByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(req.getUsername())));
        if (user == null) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        ThrowUtils.throwIf(user.getStatus() != 1, ErrorCode.FORBIDDEN_ERROR);

        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());

        UserVO uv = new UserVO();
        BeanUtils.copyProperties(user, uv);
        return new LoginVO(token, uv);
    }
}
