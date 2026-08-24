package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.PostComments;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IPostCommentsService extends IService<PostComments> {

    /**
     * 发表评论
     * @param postComments 评论内容
     * @return 评论id
     */
    Result addComment(PostComments postComments);

    /**
     * 查询帖子的评论列表
     * @param postId 帖子id
     * @param current 页码
     * @return 评论列表
     */
    Result queryCommentsByPostId(Long postId, Integer current);

    /**
     * 删除评论
     * @param commentId 评论id
     * @return 操作结果
     */
    Result deleteComment(Long commentId);

    /**
     * 点赞评论
     * @param commentId 评论id
     * @return 操作结果
     */
    Result likeComment(Long commentId);
}
