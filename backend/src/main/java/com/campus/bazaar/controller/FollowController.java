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
     * 关注/取关用户（幂等）
     * @param followUserId 被关注的用户id
     * @param isFollow true关注 false取关
     * @return 操作后该用户的 { followers, following } 计数
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

    /**
     * 关注数 / 粉丝数（个人中心、他人主页都用）
     * @param userId 目标用户id
     * @return { followers, following }
     */
    @GetMapping("/counts/{id}")
    public Result counts(@PathVariable("id") Long userId) {
        return followService.countResult(userId);
    }

    /**
     * TA 关注的人（按关注时间倒序）
     * @param userId 目标用户id
     * @return 用户卡片列表
     */
    @GetMapping("/following/{id}")
    public Result following(@PathVariable("id") Long userId) {
        return followService.followingList(userId);
    }

    /**
     * TA 的粉丝（按关注时间倒序）
     * @param userId 目标用户id
     * @return 用户卡片列表
     */
    @GetMapping("/followers/{id}")
    public Result followers(@PathVariable("id") Long userId) {
        return followService.followerList(userId);
    }
}
