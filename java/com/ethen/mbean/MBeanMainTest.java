package com.ethen.mbean;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

/**
 * MBean测试
 * <br>
 * 对于管理系统来讲，这些MBean中公开的方法，最终会被JMX转换为
 * <li>属性（Attribute）</li>
 * <li>监听（Listener）</li>
 * <li>调用（Invoke）</li>
 *
 * @author ethenyang@126.com
 * @since 2022/03/07
 */
public class MBeanMainTest {
    private static ObjectName objectName;
    private static MBeanServer mBeanServer;

    public static void main(String[] args) throws Exception {
        init();
        manage();
        await();
    }

    private static void init() throws Exception {
        ServerImpl server = new ServerImpl();
        ServerMonitor serverMonitor = new ServerMonitor(server);
        mBeanServer = MBeanServerFactory.createMBeanServer();
        objectName = new ObjectName("objectName:id=ServerMonitor01");
        // 注册MBean
        mBeanServer.registerMBean(serverMonitor, objectName);

    }

    private static void manage() throws Exception {
        Object upTime = mBeanServer.getAttribute(objectName, "UpTime");
        System.out.println("upTime----->" + upTime);
    }

    private static void await() {
        while (true) ;
    }
}
