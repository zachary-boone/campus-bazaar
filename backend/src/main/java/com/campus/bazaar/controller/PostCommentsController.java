package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.PostComments;
import com.campus.bazaar.service.IPostCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/post-comments")
public class PostCommentsController {

    @Resource
    private IPostCommentsService postCommentsService;

    /**
     * 发表评论
     * @param postComments 评论内容
     * @return 评论id
     */
    @PostMapping
    public Result addComment(@RequestBody PostComments postComments) {
        return postCommentsService.addComment(postComments);
    }

    /**
     * 查询帖子的评论列表
     * @param postId 帖子id
     * @param current 页码
     * @return 评论列表
     */
    @GetMapping("/{postId}")
    public Result queryCommentsByPostId(
            @PathVariable("postId") Long postId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return postCommentsService.queryCommentsByPostId(postId, current);
    }

    /**
     * 删除评论
     * @param commentId 评论id
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long commentId) {
        return postCommentsService.deleteComment(commentId);
    }

    /**
     * 点赞评论
     * @param commentId 评论id
     * @return 操作结果
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long commentId) {
        return postCommentsService.likeComment(commentId);
    }
}
