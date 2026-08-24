package com.campus.bazaar.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Post;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.PostMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IPostService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子服务 - ZSet 点赞排行
 * <p>
 * 点赞数据用 Redis ZSet 存储（post:liked:{postId}，member=userId，score=点赞时间戳）：
 * - 点赞：ZADD；取消：ZREM；是否点赞：ZSCORE；
 * - 点赞排行：ZREVRANGE 按点赞时间倒序取用户列表；
 * - 帖子点赞数字段（liked）与 DB 同步自增/自减，保证热点查询走内存、数据落库。
 */
@Slf4j
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public Result likePost(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        String key = RedisConstants.POST_LIKED_KEY + id;

        // 1. 判断是否已点赞（ZSet 里有 score 说明点过）
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        boolean liked = score != null;

        if (!liked) {
            // 2a. 点赞：ZADD score=当前时间戳，DB liked+1
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            update().setSql("liked = liked + 1").eq("id", id).update();
        } else {
            // 2b. 取消：ZREM，DB liked-1
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            update().setSql("liked = liked - 1").eq("id", id).update();
        }
        return Result.ok();
    }

    @Override
    public boolean isLiked(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return false;
        }
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.POST_LIKED_KEY + id, userId.toString());
        return score != null;
    }

    @Override
    public List<Post> queryPostLikes(Long id, Integer top) {
        // 1. ZREVRANGE 取点赞时间最近的 top 个用户
        Set<String> userIds = stringRedisTemplate.opsForZSet().reverseRange(RedisConstants.POST_LIKED_KEY + id, 0, (top == null ? 5 : top) - 1);
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 批量查用户信息，转成 Post 视图（只带用户字段，前端复用帖子卡片）
        List<Long> ids = userIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(ids);
        if (users == null) {
            return new ArrayList<>();
        }
        return users.stream().map(u -> {
            Post post = new Post();
            post.setId(id);
            post.setUserId(u.getId());
            post.setName(u.getNickName());
            post.setIcon(u.getIcon());
            return post;
        }).collect(Collectors.toList());
    }
}
