package com.github.auties00.cobalt.yunsuo.sender;

/**
 * 消息发送结果，通过进程退出码回传给调用方。
 */
public enum SendResult {
    /** 消息发送成功 */
    SUCCESS(0),
    /** 消息发送失败（业务异常） */
    SEND_FAILED(1),
    /** 账号被封禁（登录后收到 failure reason=401/403） */
    ACCOUNT_BANNED(2),
    /** 超时 */
    TIMEOUT(3);

    private final int code;

    SendResult(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
