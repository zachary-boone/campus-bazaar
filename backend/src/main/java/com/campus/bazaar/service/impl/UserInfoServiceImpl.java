package com.campus.bazaar.service.impl;

import com.campus.bazaar.entity.UserInfo;
import com.campus.bazaar.mapper.UserInfoMapper;
import com.campus.bazaar.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
