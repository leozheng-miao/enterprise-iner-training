package com.leo.enterpriseinertraining.security;

import com.leo.enterpriseinertraining.user.entity.User;
import com.leo.enterpriseinertraining.user.mapper.UserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import static com.leo.enterpriseinertraining.user.entity.table.UserTableDef.USER;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userMapper.selectOneByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(username))
        );
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }
}
