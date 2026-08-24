package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Post;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 帖子服务类
 * </p>
 */
public interface IPostService extends IService<Post> {

    /**
     * 点赞/取消点赞（Redis ZSet + DB liked 同步）
     * @param id 帖子 id
     */
    Result likePost(Long id);

    /**
     * 当前用户是否已点赞
     */
    boolean isLiked(Long id);

    /**
     * 帖子点赞排行 TopN（ZSet 按时间倒序）
     * @param id   帖子 id
     * @param top 取前 N 个用户
     */
    List<Post> queryPostLikes(Long id, Integer top);
}
