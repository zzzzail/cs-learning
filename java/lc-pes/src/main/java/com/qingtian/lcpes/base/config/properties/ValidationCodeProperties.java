package com.qingtian.lcpes.base.config.properties;

import lombok.Data;

/**
 * @author zail
 * @date 2022/1/24
 */
@Data
public class ValidationCodeProperties {
    // 验证码类型
    private ValidationCodeType type = ValidationCodeType.CODE;

    private int width = 80;
    private int height = 80;
    // 验证码长度
    private int length = 4;
    // 过期时间, 默认30分钟
    private int expireIn = 1800;

    static enum ValidationCodeType {
        CODE,
        IMAGE,
    }
}
