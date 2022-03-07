package com.ethen.mbean;

/**
 * 模拟服务器监控MBean接口
 *
 * @author ethenyang@126.com
 * @since 2022/03/07
 */
public interface ServerMonitorMBean {
    /**
     * 获取服务器的运行时间
     */
    long getUpTime();
}
