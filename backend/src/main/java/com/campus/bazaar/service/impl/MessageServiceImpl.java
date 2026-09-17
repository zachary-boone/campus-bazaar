package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Message;
import com.campus.bazaar.mapper.MessageMapper;
import com.campus.bazaar.service.IMessageService;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站内消息服务实现
 * <p>
 * 所有权约定：所有读/写都以 {@code user_id = 当前登录用户} 为过滤条件，
 * 消息 id 只用于定位行，越权访问等价于"查不到"，不做额外区分。
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements IMessageService {

    @Override
    public Result myMessages(Integer current, Integer type) {
        Long userId = UserHolder.getUser().getId();
        // type 只认 1/2/3，其余（含 0/null）一律当"全部"，白名单式收敛查询条件
        QueryWrapper<Message> wrapper = new QueryWrapper<Message>()
                .eq("user_id", userId)
                .eq(type != null && (type == 1 || type == 2 || type == 3), "type", type)
                .orderByDesc("create_time")
                .orderByDesc("id");
        Page<Message> page = page(new Page<>(current == null || current < 1 ? 1 : current,
                SystemConstants.MAX_PAGE_SIZE), wrapper);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result unreadCount() {
        Long userId = UserHolder.getUser().getId();
        long count = query().eq("user_id", userId).eq("is_read", 0).count();
        return Result.ok(count);
    }

    @Override
    public Result readOne(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 幂等：已读过的消息再点一次（0 行受影响）也不报错，统一返回成功
        update(new UpdateWrapper<Message>()
                .eq("id", id)
                .eq("user_id", userId)      // 只能标自己的消息，越权=0 行受影响
                .eq("is_read", 0)
                .set("is_read", 1));
        return Result.ok();
    }

    @Override
    public Result readAll() {
        Long userId = UserHolder.getUser().getId();
        update(new UpdateWrapper<Message>()
                .eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1));
        return Result.ok();
    }
}
