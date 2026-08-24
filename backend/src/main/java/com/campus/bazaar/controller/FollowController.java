package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注/取关用户
     * @param followUserId 被关注的用户id
     * @param isFollow true关注 false取关
     * @return 操作结果
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 判断是否关注了某用户
     * @param followUserId 被关注的用户id
     * @return true/false
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        Boolean isFollow = followService.isFollow(followUserId);
        return Result.ok(isFollow);
    }

    /**
     * 查询共同关注
     * @param userId 目标用户id
     * @return 共同关注的用户id列表
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long userId) {
        return followService.followCommons(userId);
    }
}
