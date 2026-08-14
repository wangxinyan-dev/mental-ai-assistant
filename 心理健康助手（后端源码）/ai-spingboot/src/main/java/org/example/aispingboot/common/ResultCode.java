package org.example.aispingboot.common;

public enum ResultCode {
    // 选举类的第一项必须要是枚举项
    SUCCESS("200", "操作成功"),
    ERROR("-1", "操作失败"),
    UNAUTHORIZED("401", "暂未登录或token已经过期"),
    SYSTEM_ERROR("500", "系统错误"),

    // 参数相关错误
    PARAM_ERROR("400", "参数错误"),
    PARAM_MISSING("4001", "缺少必要参数"),
    PARAM_INVALID("4002", "参数格式不正确"),

    // 文件操作相关错误
    FILE_NOT_FOUND("5001", "文件不存在"),
    FILE_UPLOAD_FAILED("5002", "文件上传失败"),
    FILE_DELETE_FAILED("5003", "文件删除失败"),
    FILE_SIZE_EXCEEDED("5004", "文件大小超过限制"),
    FILE_TYPE_NOT_SUPPORTED("5005", "不支持的文件类型"),
    FILE_NAME_INVALID("5006", "文件名不合法"),
    FILE_CONTENT_INVALID("5007", "文件内容不合法"),
    FILE_SAVE_FAILED("5008", "文件保存失败"),

    // 业务相关错误
    BUSINESS_ERROR("6000", "业务处理失败"),
    ACCOUNT_SAME("6001", "用户名已存在"),
    USER_NOT_EXIST("6002", "用户不存在"),

    // token相关错误
    TOKEN_INVALID("A0230", "token无效"),
    TOKEN_EXPIRED("A0230", "token已过期"),
    TOKEN_BLOCKED("A0230", "token已加入黑名单"),
    TOKEN_ACCESS_FORBIDDEN("A0231", "token已被禁止访问"),
    AUTHORIZED_ERROR("A0300", "访问权限异常"),
    ACCESS_UNAUTHORIZED("A0301", "访问未授权");


    private String code;
    private String msg;

    ResultCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
