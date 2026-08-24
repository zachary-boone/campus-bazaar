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

    /**
     * 发布帖子并推送到所有粉丝的收件箱（Feed 推模式）
     */
    Result savePostWithFeed(Post post);

    /**
     * 关注流（滚动分页）：读取当前用户收件箱 ZSet
     * @param lastId 上次返回的最后一条的 score（首次传 0）
     * @param offset 与 lastId 同 score 的偏移（处理同一毫秒多条）
     */
    Result queryFollowFeed(Long lastId, Integer offset);
}
