package com.qingtian.lcpes.modules.authpre.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.qingtian.lcpes.base.facade.BasicMapper;
import com.qingtian.lcpes.modules.authpre.entity.AcOrgnizationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 组织机构 Mapper
 * 自定义Mapper方法
 * </p>
 *
 * @author <a href="qingtian@shougang.com.cn">晴天</a>
 * @since 2022-05-17
 */
@Mapper
@DS("auth")
public interface AuthOrgnizationMapper extends BasicMapper<AcOrgnizationEntity> {
    /**
     * 自定义分页查询
     *
     * @param page
     * @param entity
     * @return
     */
    public List<AcOrgnizationEntity> listByPage(IPage<AcOrgnizationEntity> page, @Param(Constants.ENTITY) AcOrgnizationEntity entity);
}
