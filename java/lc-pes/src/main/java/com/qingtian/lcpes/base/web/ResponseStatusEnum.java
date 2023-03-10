package com.qingtian.lcpes.base.web;

import com.qingtian.lcpes.base.web.enums.CodeEnum;
import lombok.Getter;

/**
 * 返回值状态枚举
 *
 * @author zail
 * @date 2022/1/21
 */
@Getter
public enum ResponseStatusEnum implements CodeEnum {

    OK(0, "成功！"),
    ERR(1, "错误！"),
    UNAUTHORIZED(2, "暂未登录或token已经过期！"),
    VALIDATE_FAILED(3, "参数检验失败！"),
    FORBIDDEN(4, "没有相关权限！"),

    ;

    private final Integer code;
    private final String message;

    ResponseStatusEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
