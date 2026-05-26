package org.example.myplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.myplatform.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承BaseMapper后拥有基本的CRUD方法
    // 无需编写XML或注解SQL
}
