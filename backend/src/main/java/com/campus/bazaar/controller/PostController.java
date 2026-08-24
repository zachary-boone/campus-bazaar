package com.campus.bazaar.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.entity.Post;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.service.IPostService;
import com.campus.bazaar.service.IUserService;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    private IPostService postService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result savePost(@RequestBody Post post) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        post.setUserId(user.getId());
        if (post.getGoodsId() == null) {
            post.setGoodsId(0L); // 不关联商品的普通帖子
        }
        if (post.getImages() == null) {
            post.setImages(""); // 无图帖子
        }
        // 保存并推送关注流（Feed 推模式）
        return postService.savePostWithFeed(post);
    }

    /**
     * 关注流（滚动分页）
     * @param lastId 上次返回的 minTime（首次不传）
     * @param offset 与 lastId 同分的偏移（首次不传）
     */
    @GetMapping("/of/follow")
    public Result queryFollowFeed(@RequestParam(value = "lastId", required = false) Long lastId,
                                  @RequestParam(value = "offset", required = false) Integer offset) {
        return postService.queryFollowFeed(lastId, offset);
    }

    @PutMapping("/like/{id}")
    @com.campus.bazaar.utils.RateLimit(key = "post-like", rate = 20, capacity = 50)
    public Result likePost(@PathVariable("id") Long id) {
        // ZSet 点赞/取消
        return postService.likePost(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryPostLikes(@PathVariable("id") Long id,
                                 @RequestParam(value = "top", defaultValue = "5") Integer top) {
        // ZSet 点赞排行 TopN
        return Result.ok(postService.queryPostLikes(id, top));
    }

    @GetMapping("/like/status/{id}")
    public Result isLiked(@PathVariable("id") Long id) {
        return Result.ok(postService.isLiked(id));
    }

    @GetMapping("/of/me")
    public Result queryMyPost(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Post> page = postService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Post> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotPost(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户查询
        Page<Post> page = postService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Post> records = page.getRecords();
        // 查询用户
        records.forEach(post ->{
            Long userId = post.getUserId();
            User user = userService.getById(userId);
            post.setName(user.getNickName());
            post.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }
}
