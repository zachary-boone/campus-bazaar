package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Post;
import com.campus.bazaar.entity.PostComments;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.PostCommentsMapper;
import com.campus.bazaar.mapper.PostMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IPostCommentsService;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PostCommentsServiceImpl extends ServiceImpl<PostCommentsMapper, PostComments> implements IPostCommentsService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional
    public Result addComment(PostComments postComments) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        postComments.setUserId(userId);

        // 2. 设置默认值
        if (postComments.getParentId() == null) {
            postComments.setParentId(0L);
        }
        if (postComments.getAnswerId() == null) {
            postComments.setAnswerId(0L);
        }
        if (postComments.getLiked() == null) {
            postComments.setLiked(0);
        }
        if (postComments.getStatus() == null) {
            postComments.setStatus(false);
        }
        postComments.setCreateTime(LocalDateTime.now());
        postComments.setUpdateTime(LocalDateTime.now());

        // 3. 保存评论
        save(postComments);

        // 4. 更新帖子评论数
        postMapper.update(null, new UpdateWrapper<Post>()
                .eq("id", postComments.getPostId())
                .setSql("comments = comments + 1"));

        return Result.ok(postComments.getId());
    }

    @Override
    public Result queryCommentsByPostId(Long postId, Integer current) {
        // 1. 分页查询评论
        Page<PostComments> page = query()
                .eq("post_id", postId)
                .eq("status", false)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<PostComments> records = page.getRecords();

        // 2. 填充用户信息
        for (PostComments comment : records) {
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                comment.setAnswerId(comment.getAnswerId()); // 保持原值
            }
        }

        return Result.ok(records, page.getTotal());
    }

    @Override
    @Transactional
    public Result deleteComment(Long commentId) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 查询评论
        PostComments comment = getById(commentId);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        // 3. 判断是否是评论作者
        if (!comment.getUserId().equals(userId)) {
            return Result.fail("只能删除自己的评论");
        }

        // 4. 删除评论（逻辑删除，设置状态为禁止查看）
        comment.setStatus(true);
        comment.setUpdateTime(LocalDateTime.now());
        updateById(comment);

        // 5. 更新帖子评论数
        postMapper.update(null, new UpdateWrapper<Post>()
                .eq("id", comment.getPostId())
                .setSql("comments = comments - 1"));

        return Result.ok();
    }

    @Override
    public Result likeComment(Long commentId) {
        // 1. 更新评论点赞数
        update(null, new UpdateWrapper<PostComments>()
                .eq("id", commentId)
                .setSql("liked = liked + 1"));
        return Result.ok();
    }
}
