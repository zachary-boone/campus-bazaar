package com.campus.bazaar.service.impl;

import com.campus.bazaar.entity.Post;
import com.campus.bazaar.mapper.PostMapper;
import com.campus.bazaar.service.IPostService;
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
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

}
