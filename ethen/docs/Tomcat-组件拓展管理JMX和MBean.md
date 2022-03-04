# Tomcat - 组件拓展管理:JMX和MBean

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-jmx.html

## 引入

> 我们在前文中讲Lifecycle以及组件，怎么会突然讲JMX和MBean呢？本文通过承接上文Lifecycle讲Tomcat基于JMX的实现。

### [¶](#为什么要了解jmx) 为什么要了解JMX

我们在上文中讲Lifecycle和相关组件时，你会发现其实还设计一块就是左侧的JMX和MBean的实现，即LifecycleMBeanBase.

![img](/ethen/imgs/tomcat/tomcat-x-jmx-1.jpg)

### [¶](#什么是jmx和mbean) 什么是JMX和MBean

> JMX是java1.5中引入的新特性。JMX全称为“Java Management  Extension”，即Java管理扩展。

JMX(Java Management Extensions)是一个为应用程序植入管理功能的框架。JMX是一套标准的代理和服务，实际上，用户可以在任何Java应用程序中使用这些代理和服务实现管理。它使用了最简单的一类javaBean，使用有名的MBean，其内部包含了数据信息，这些信息可能是程序配置信息、模块信息、系统信息、统计信息等。MBean可以操作可读可写的属性、直接操作某些函数。

**应用场景**：中间件软件WebLogic的管理页面就是基于JMX开发的，而JBoss则整个系统都基于JMX构架，我们今天讲的Tomcat也是基于JMX开发而来的。

我们看下**JMX的结构**

![img](/ethen/imgs/tomcat/tomcat-x-jmx-2.png)

- **Probe Level** 负责资源的检测（获取信息），包含MBeans，通常也叫做Instrumentation Level。MX管理构件（MBean）分为四种形式，分别是标准管理构件（Standard MBean）、动态管理构件（Dynamic MBean）、开放管理构件(Open Mbean)和模型管理构件(Model MBean)。
- **The Agent Level** 或者叫做MBean Server（代理服务器），是JMX的核心，连接Mbeans和远程监控程序。
- **Remote Management Level** 通过connectors和adaptors来远程操作MBean Server。

## [¶](#jmx使用案例) JMX使用案例

> 上节只是引入和相关概念，这是不够的，你依然需要一个案例来帮助你理解JMX是如何工作的。

### [¶](#基于jmx的监控例子) 基于JMX的监控例子

- ServerImpl - 我们模拟的某个服务器ServerImpl状态

```java
public class ServerImpl {
    public final long startTime;
    public ServerImpl() {
        startTime = System.currentTimeMillis();
    }
} 
```



- 由于MXBean规定，标准MBean也要实现一个接口，其所有向外界公开的方法都要在该接口中声明，否则管理系统就不能从中获取信息。此外，该接口的命名有一定的规范：在标准MBean类名后加上MBean后缀。这里的标准MBean类就是ServerMonitor，所以其对应的接口就应该是ServerMonitorMBean。因此ServerMonitorMBean的实现如下

```java
public interface ServerMonitorMBean {
	public long getUpTime();
}
```



- 使用ServerMonitor类来监测ServerImpl的状态，实现如下

```java
public class ServerMonitor implements ServerMonitorMBean {
    private final ServerImpl target;
    public ServerMonitor(ServerImpl target) {
        this.target = target;
    }

    @Override
    public long getUpTime() {
        return System.currentTimeMillis() - target.startTime;
    }
}
    
```



- 对于管理系统来讲，这些MBean中公开的方法，最终会被JMX转换为属性（Attribute）、监听（Listener）和调用（Invoke）的概念。下面代码中Main类的manage方法就模拟了管理程序是如何获取监测到的属性，并表现监测结果。

```java
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

public class Main {
    private static ObjectName objectName;
    private static MBeanServer mBeanServer;

    public static void main(String[] args) throws Exception {
        init();
        manage();
    }

    private static void init() throws Exception {
        ServerImpl serverImpl = new ServerImpl();
        ServerMonitor serverMonitor = new ServerMonitor(serverImpl);
        mBeanServer = MBeanServerFactory.createMBeanServer();
        objectName = new ObjectName("objectName:id=ServerMonitor1");

        // 注册到MBeanServer
        mBeanServer.registerMBean(serverMonitor, objectName);
    }

    private static void manage() throws Exception {
        // 获取属性值
        long upTime = (Long)mBeanServer.getAttribute(objectName, "UpTime");
        System.out.println(upTime);
    }
}
```



- 整体流程

![img](/ethen/imgs/tomcat/tomcat-x-jmx-3.jpg)

> 如上步骤就能让你理解常见的Jconsole是如何通过JMX获取属性，对象等监控信息的了。

### [¶](#基于jmx的htmladapter案例) 基于JMX的HTMLAdapter案例

> 上面例子，还没有体现adapter展示，比如上述信息在HTML页面中展示出来，再看一个例子

- 我们的管理目标

```java
public class ControlTarget {
	private long width;
	private long length;
	
	public ControlTarget( long width, long length) {
		this.width = width;
		this.length = length;
	}
	
	public long getWidth() {
		return width;
	}
	
	public long getLength() {
		return length;
	}
}    
```



- 根据标准MBean类抽象出符合规范的MBean类的接口，并修改标准MBean类实现该接口。

```java
public interface ControlImplMBean {
	public long getLength();
	public long getWidth();
	public long getArea();
	public double getLengthWidthRatio();
}
```



- 根据需求，创建管理（目标程序）的类，其中包含操纵和获取（目标程序）特性的方法。这个类就是标准MBean类。

```java
public class ControlImpl implements ControlImplMBean {
	private ControlTarget target;
	
	public ControlImpl(ControlTarget target) {
		this.target = target;
	}
	
	@Override
	public long getLength() {
		return target.getLength();
	}
	
	@Override
	public long getWidth() {
		return target.getWidth();
	}
	
	@Override
	public long getArea() {
		return target.getLength() * target.getWidth();
	}
	
	@Override
	public double getLengthWidthRatio() {
		return  target.getLength() * 1.0f / target.getWidth();
	}
}
    
```



- 创建MBean的代理类，代理中包含创建MBeanServer、生成ObjectName、注册MBean、表现MBean

```java
import com.sun.jdmk.comm.HtmlAdaptorServer;

import javax.management.*;

public class ControlImplAgent {

    public static void main(String[] args) throws MalformedObjectNameException, NullPointerException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {

        // 创建MBeanServer
        MBeanServer server = MBeanServerFactory.createMBeanServer();

        // 为MBean创建ObjectName
        ObjectName controlImplName = new ObjectName("controlImpl:name=firstOne");

        // 注册MBean到Server中
        server.registerMBean(new ControlImpl(new ControlTarget(50, 200)), controlImplName);

        // 表现MBean(一种方式)
        ObjectName adapterName = new ObjectName("ControlImpl:name=htmladapter,port=8082");
        HtmlAdaptorServer adapter = new HtmlAdaptorServer();
        server.registerMBean(adapter, adapterName);

        adapter.start();
        //adapter.stop();
    }

}
```



- 打开相关页面

PS：相关Adapter可以通过这里下载https://download.csdn.net/download/com_ma/10379741

![img](/ethen/imgs/tomcat/tomcat-x-jmx-4.jpg)

点击最后一个链接

![img](/ethen/imgs/tomcat/tomcat-x-jmx-5.jpg)

## [¶](#tomcat如何通过jmx实现组件管理) Tomcat如何通过JMX实现组件管理

> 在简单理解了JMX概念和案例之后，我们便可以开始学习Tomcat基于JMX的实现了。

![img](/ethen/imgs/tomcat/tomcat-x-jmx-1.jpg)

上述图中，我们看下相关的类的用途

- `MBeanRegistration`：Java JMX框架提供的注册MBean的接口，引入此接口是为了便于使用JMX提供的管理功能；
- `JmxEnabled`: 此接口由组件实现，这些组件在创建时将注册到MBean服务器，在销毁时将注销这些组件。它主要是由实现生命周期的组件来实现的，但并不是专门为它们实现的。
- `LifecycleMBeanBase`：Tomcat提供的对MBeanRegistration的抽象实现类，运用抽象模板模式将所有容器统一注册到JMX；

此外，ContainerBase、StandardServer、StandardService、WebappLoader、Connector、StandardContext、StandardEngine、StandardHost、StandardWrapper等容器都继承了LifecycleMBeanBase，因此这些容器都具有了同样的生命周期并可以通过JMX进行管理。

### [¶](#mbeanregistration) MBeanRegistration

理解MBeanRegistration主要在于:

- 两块内容：registered 和 unregistered
- 两类方法：before和after

```java
public interface MBeanRegistration   {

    // 在注册之前执行的方法，如果发生异常，MBean不会注册到MBean Server中
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws java.lang.Exception;
    // 在注册之后执行的方法，比如注册失败提供报错信息
    public void postRegister(Boolean registrationDone);


    // 在卸载前执行的方法
    public void preDeregister() throws java.lang.Exception ;
    // 在执行卸载之后的方法
    public void postDeregister();

 }
    
```

### [¶](#jmxenabled) JmxEnabled

理解JmxEnabled：在设计上它引一个域（Domain）对注册的MBeans进行隔离，这个域类似于MBean上层的命名空间一样。

```java
public interface JmxEnabled extends MBeanRegistration {

    // 获取MBean所属于的Domain
    String getDomain();

    // 设置Domain
    void setDomain(String domain);

    // 获取MBean的名字
    ObjectName getObjectName();
}
    
```

### [¶](#lifecyclembeanbase) LifecycleMBeanBase

这样理解LifecycleMBeanBase时，你便知道它包含两块，一个是Lifecycle的接口实现，一个是Jmx接口封装实现。

从它实现的类继承和实现关系便能看出：

```java
public abstract class LifecycleMBeanBase extends LifecycleBase
        implements JmxEnabled {

}

```



#### [¶](#jmxenabled的接口实现) JmxEnabled的接口实现

- Domain和mBeanName相关，代码很简单，不做详解

```java
/* Cache components of the MBean registration. */
private String domain = null;
private ObjectName oname = null;
@Deprecated
protected MBeanServer mserver = null;

/**
  * Specify the domain under which this component should be registered. Used
  * with components that cannot (easily) navigate the component hierarchy to
  * determine the correct domain to use.
  */
@Override
public final void setDomain(String domain) {
    this.domain = domain;
}


/**
  * Obtain the domain under which this component will be / has been
  * registered.
  */
@Override
public final String getDomain() {
    if (domain == null) {
        domain = getDomainInternal();
    }

    if (domain == null) {
        domain = Globals.DEFAULT_MBEAN_DOMAIN;
    }

    return domain;
}


/**
  * Method implemented by sub-classes to identify the domain in which MBeans
  * should be registered.
  *
  * @return  The name of the domain to use to register MBeans.
  */
protected abstract String getDomainInternal();


/**
  * Obtain the name under which this component has been registered with JMX.
  */
@Override
public final ObjectName getObjectName() {
    return oname;
}


/**
  * Allow sub-classes to specify the key properties component of the
  * {@link ObjectName} that will be used to register this component.
  *
  * @return  The string representation of the key properties component of the
  *          desired {@link ObjectName}
  */
protected abstract String getObjectNameKeyProperties();
  
```



- 注册和卸载的相关方法

```java
/**
  * Utility method to enable sub-classes to easily register additional
  * components that don't implement {@link JmxEnabled} with an MBean server.
  * <br>
  * Note: This method should only be used once {@link #initInternal()} has
  * been called and before {@link #destroyInternal()} has been called.
  *
  * @param obj                       The object the register
  * @param objectNameKeyProperties   The key properties component of the
  *                                  object name to use to register the
  *                                  object
  *
  * @return  The name used to register the object
  */
protected final ObjectName register(Object obj,
        String objectNameKeyProperties) {

    // Construct an object name with the right domain
    StringBuilder name = new StringBuilder(getDomain());
    name.append(':');
    name.append(objectNameKeyProperties);

    ObjectName on = null;

    try {
        on = new ObjectName(name.toString());
        Registry.getRegistry(null, null).registerComponent(obj, on, null);
    } catch (MalformedObjectNameException e) {
        log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                e);
    } catch (Exception e) {
        log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                e);
    }

    return on;
}


/**
  * Utility method to enable sub-classes to easily unregister additional
  * components that don't implement {@link JmxEnabled} with an MBean server.
  * <br>
  * Note: This method should only be used once {@link #initInternal()} has
  * been called and before {@link #destroyInternal()} has been called.
  *
  * @param objectNameKeyProperties   The key properties component of the
  *                                  object name to use to unregister the
  *                                  object
  */
protected final void unregister(String objectNameKeyProperties) {
    // Construct an object name with the right domain
    StringBuilder name = new StringBuilder(getDomain());
    name.append(':');
    name.append(objectNameKeyProperties);
    Registry.getRegistry(null, null).unregisterComponent(name.toString());
}


/**
  * Utility method to enable sub-classes to easily unregister additional
  * components that don't implement {@link JmxEnabled} with an MBean server.
  * <br>
  * Note: This method should only be used once {@link #initInternal()} has
  * been called and before {@link #destroyInternal()} has been called.
  *
  * @param on    The name of the component to unregister
  */
protected final void unregister(ObjectName on) {
    Registry.getRegistry(null, null).unregisterComponent(on);
}


/**
  * Not used - NOOP.
  */
@Override
public final void postDeregister() {
    // NOOP
}


/**
  * Not used - NOOP.
  */
@Override
public final void postRegister(Boolean registrationDone) {
    // NOOP
}


/**
  * Not used - NOOP.
  */
@Override
public final void preDeregister() throws Exception {
    // NOOP
}


/**
  * Allows the object to be registered with an alternative
  * {@link MBeanServer} and/or {@link ObjectName}.
  */
@Override
public final ObjectName preRegister(MBeanServer server, ObjectName name)
        throws Exception {

    this.mserver = server;
    this.oname = name;
    this.domain = name.getDomain().intern();

    return oname;
}
    
```



#### [¶](#lifecyclebase相关接口) LifecycleBase相关接口

这样你就知道这里抽象出的LifecycleBase如下两个方法的用意，就是为了注册和卸载MBean

```java
/**
注册MBean
  */
@Override
protected void initInternal() throws LifecycleException {
    // If oname is not null then registration has already happened via
    // preRegister().
    if (oname == null) {
        mserver = Registry.getRegistry(null, null).getMBeanServer();

        oname = register(this, getObjectNameKeyProperties());
    }
}


/**
  卸载MBean
  */
@Override
protected void destroyInternal() throws LifecycleException {
    unregister(oname);
}
    
```



## [¶](#参考文档) 参考文档

JMX例子整理自：

- https://blog.csdn.net/xiaoxiaoyusheng2012/article/details/52101083
- https://www.cnblogs.com/dongguacai/p/5900507.html