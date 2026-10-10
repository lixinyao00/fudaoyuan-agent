package com.study.ragagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.study.ragagent.domain.entity.SysUser;
import com.study.ragagent.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务
 */
@Service
public class UserService {

    @Autowired
    private SysUserMapper userMapper;

    public SysUser getById(Long id) {
        return userMapper.selectById(id);
    }

    public SysUser getByUsername(String username) {
        return userMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", username));
    }

    public boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", username)) > 0;
    }

    public void save(SysUser user) {
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
    }

    public List<SysUser> listAll() {
        return userMapper.selectList(null);
    }
}