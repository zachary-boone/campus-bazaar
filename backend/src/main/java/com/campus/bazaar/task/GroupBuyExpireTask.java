package com.campus.bazaar.task;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.bazaar.entity.GroupBuy;
import com.campus.bazaar.mapper.GroupBuyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 拼单过期任务
 * <p>
 * 到点未成团的拼单批量置为"已过期"，与查询路径的懒惰过期互为双保险：
 * 没有任务时（如应用刚启动、任务停摆）读路径也会先过期再查，不会出现
 * "已过期的团还能加入"。每 30 秒扫描一次。
 */
@Slf4j
@Component
public class GroupBuyExpireTask {

    @Resource
    private GroupBuyMapper groupBuyMapper;

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void expireOverdueGroups() {
        try {
            int expired = groupBuyMapper.update(null, new UpdateWrapper<GroupBuy>()
                    .eq("status", GroupBuy.STATUS_ONGOING)
                    .lt("expire_time", LocalDateTime.now())
                    .set("status", GroupBuy.STATUS_EXPIRED));
            if (expired > 0) {
                log.warn("[GroupBuyTask] 拼单过期完成，本次过期 {} 个未成团拼单", expired);
            }
        } catch (Exception e) {
            log.error("[GroupBuyTask] 拼单过期扫描异常: {}", e.getMessage(), e);
        }
    }
}
