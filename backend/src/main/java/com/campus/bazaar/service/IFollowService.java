package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关用户（幂等：重复关注不报错）
     * @param followUserId 被关注的用户id
     * @param isFollow true关注 false取关
     * @return 操作后该用户的 { followers, following } 计数，前端可直接刷新
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

    /**
     * 粉丝数（谁关注了我）
     * @param userId 目标用户id
     * @return 粉丝数
     */
    long countFollowers(Long userId);

    /**
     * 关注数（我关注了谁）
     * @param userId 目标用户id
     * @return 关注数
     */
    long countFollowing(Long userId);

    /**
     * 批量取粉丝数（一次 GROUP BY，避免逐个 count 的 N+1）
     * @param userIds 目标用户id集合
     * @return userId -> 粉丝数（无粉丝的用户不会出现在结果里）
     */
    java.util.Map<Long, Long> countFollowersBatch(java.util.List<Long> userIds);

    /**
     * 关注/粉丝计数（给前端同时展示两个数字）
     * @param userId 目标用户id
     * @return { followers, following }
     */
    Result countResult(Long userId);

    /**
     * 某用户关注的人（按关注时间倒序）
     * @param userId 目标用户id
     * @return 用户卡片列表
     */
    Result followingList(Long userId);

    /**
     * 某用户的粉丝（按关注时间倒序）
     * @param userId 目标用户id
     * @return 用户卡片列表
     */
    Result followerList(Long userId);
}
