package com.qingtian.lcpes.base.web;

import com.qingtian.lcpes.base.web.enums.CodeEnum;

/**
 * @author zail
 * @date 2022/1/21
 */
public class JsonResult extends CommonResult<Object> {

    public JsonResult() {
    }

    public JsonResult(Integer code, String message, Object data) {
        super(code, message, data);
    }

    /**
     * 快速生成JsonResult对象
     */
    public static JsonResult of(Integer code, String message, Object data) {
        return new JsonResult(code, message, data);
    }

    public static JsonResult of(CodeEnum ce) {
        return new JsonResult(ce.getCode(), ce.getMessage(), null);
    }

    public static JsonResult of(CodeEnum e, Object data) {
        return new JsonResult(e.getCode(), e.getMessage(), data);
    }

    public static JsonResult ok() {
        return JsonResult.of(ResponseStatusEnum.OK);
    }

    public static JsonResult ok(Object data) {
        JsonResult result = JsonResult.of(ResponseStatusEnum.OK);
        result.setData(data);
        return result;
    }

    public static JsonResult success() {
        return JsonResult.of(ResponseStatusEnum.OK);
    }

    public static JsonResult success(Object data) {
        JsonResult result = JsonResult.of(ResponseStatusEnum.OK);
        result.setData(data);
        return result;
    }

    public static JsonResult err() {
        return JsonResult.of(ResponseStatusEnum.ERR);
    }

    public static JsonResult err(String message) {
        return JsonResult.of(ResponseStatusEnum.ERR.getCode(), message, null);
    }

    public static JsonResult err(ResponseStatusEnum e) {
        return JsonResult.of(e);
    }

    public static JsonResult err(Integer code, String message) {
        return JsonResult.of(code, message, null);
    }

    public static JsonResult err(Integer code, String message, Object data) {
        JsonResult result = JsonResult.of(ResponseStatusEnum.ERR);
        result.setData(data);
        return result;
    }

    public static JsonResult fail() {
        return JsonResult.of(ResponseStatusEnum.ERR);
    }

    public static JsonResult fail(String message) {
        return JsonResult.of(ResponseStatusEnum.ERR.getCode(), message, null);
    }

    public static JsonResult fail(CodeEnum e) {
        return JsonResult.of(e);
    }

    public static JsonResult fail(Integer code, String message) {
        return JsonResult.of(ResponseStatusEnum.ERR);
    }

    public static JsonResult fail(Integer code, String message, Object data) {
        JsonResult result = JsonResult.of(ResponseStatusEnum.ERR);
        result.setData(data);
        return result;
    }

}
