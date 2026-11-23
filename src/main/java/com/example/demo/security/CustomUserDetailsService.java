package com.example.demo.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.SysUser;
import com.example.demo.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;

    public CustomUserDetailsService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String code) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, code));
        if (user == null) {
            throw new UsernameNotFoundException("账号不存在");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new UsernameNotFoundException("账号已被禁用");
        }
        return User.withUsername(user.getCode())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
