package com.ethen.mbean;

/**
 * @author ethenyang@126.com
 * @since 2022/03/07
 */
public class ServerMonitor implements ServerMonitorMBean {
    private final ServerImpl target;

    public ServerMonitor(ServerImpl target) {
        this.target = target;
    }

    @Override
    public long getUpTime() {
        return System.currentTimeMillis() - target.getStartTime();
    }
}
