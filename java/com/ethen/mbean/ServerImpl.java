package com.ethen.mbean;

/**
 * 模拟一个服务器的实现
 *
 * @author ethenyang@126.com
 * @since 2022/03/07
 */
public class ServerImpl {
    private final long startTime;

    public ServerImpl() {
        this.startTime = System.currentTimeMillis();
    }

    public long getStartTime() {
        return startTime;
    }
}
