package com.campus.bazaar.service.impl;

import com.campus.bazaar.entity.PostComments;
import com.campus.bazaar.mapper.PostCommentsMapper;
import com.campus.bazaar.service.IPostCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class PostCommentsServiceImpl extends ServiceImpl<PostCommentsMapper, PostComments> implements IPostCommentsService {

}
