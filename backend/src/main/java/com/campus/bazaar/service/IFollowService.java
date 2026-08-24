package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关用户
     * @param followUserId 被关注的用户id
     * @param isFollow true关注 false取关
     * @return 操作结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断是否关注了某用户
     * @param followUserId 被关注的用户id
     * @return true/false
     */
    Boolean isFollow(Long followUserId);

    /**
     * 查询共同关注
     * @param userId 目标用户id
     * @return 共同关注的用户id列表
     */
    Result followCommons(Long userId);
}
