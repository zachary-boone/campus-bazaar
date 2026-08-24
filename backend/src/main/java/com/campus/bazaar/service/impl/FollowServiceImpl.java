package com.campus.bazaar.service.impl;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.entity.Follow;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.FollowMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IFollowService;
import com.campus.bazaar.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        String key = "follows:" + userId;

        if (isFollow) {
            // 2. 关注 - 写入数据库
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
            // 3. 写入Redis Set
            stringRedisTemplate.opsForSet().add(key, String.valueOf(followUserId));
        } else {
            // 2. 取关 - 删除数据库记录
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            // 3. 从Redis Set移除
            stringRedisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
        }
        return Result.ok();
    }

    @Override
    public Boolean isFollow(Long followUserId) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        // 2. 查询是否关注
        Integer count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return count > 0;
    }

    @Override
    public Result followCommons(Long userId) {
        // 1. 获取当前登录用户
        Long currentUserId = UserHolder.getUserId();
        String key1 = "follows:" + currentUserId;
        String key2 = "follows:" + userId;

        // 2. 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 3. 解析用户id
        List<Long> userIds = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 4. 查询用户信息
        List<User> users = userMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 5. 转换为UserDTO返回
        List<UserDTO> userDTOs = users.stream()
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setNickName(user.getNickName());
                    dto.setIcon(user.getIcon());
                    return dto;
                })
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }
}
