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
 *
 * @author 虎哥
 * @since 2021-12-22
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
        // 保存探店博文
        postService.save(post);
        // 返回id
        return Result.ok(post.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        postService.update()
                .setSql("liked = liked + 1").eq("id", id).update();
        return Result.ok();
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
