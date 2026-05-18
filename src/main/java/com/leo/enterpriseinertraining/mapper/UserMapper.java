package com.leo.enterpriseinertraining.mapper;

import com.mybatisflex.core.BaseMapper;
import com.leo.enterpriseinertraining.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
