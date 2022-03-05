# Tomcat - Request请求处理: Container设计

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-container.html

##  内容引入

> 这里一定把握住我们上下文之间的衔接，这是我们整个系列理解Tomcat的主线。

- 到目前我们研究到了哪里？

![img](/ethen/imgs/tomcat/tomcat-x-container-2.png)

## [¶](#理解思路) 理解思路

- **为什么我们说上面的是Container呢？我们看下几个Container之间的关系**：

![img](/ethen/imgs/tomcat/tomcat-x-container-1.jpg)

从上图上，我们也可以看出Container顶层也是基于Lifecycle的组件设计的。

- **在设计Container组件层次组件时，上述4个组件分别做什么的呢？为什么要四种组件呢？**

如下是Container接口类的相关注释

```html
 * <li><b>Engine</b> - Representation of the entire Catalina servlet engine,
 *     most likely containing one or more subcontainers that are either Host
 *     or Context implementations, or other custom groups.
 * <li><b>Host</b> - Representation of a virtual host containing a number
 *     of Contexts.
 * <li><b>Context</b> - Representation of a single ServletContext, which will
 *     typically contain one or more Wrappers for the supported servlets.
 * <li><b>Wrapper</b> - Representation of an individual servlet definition
 *     (which may support multiple servlet instances if the servlet itself
 *     implements SingleThreadModel).
 * </ul>    
```



**Engine** - 表示整个catalina的servlet引擎，多数情况下包含**一个或多个**子容器，这些子容器要么是Host，要么是Context实现，或者是其他自定义组。

**Host** - 表示包含多个Context的虚拟主机的。

**Context** — 表示一个ServletContext，表示一个webapp，它通常包含一个或多个wrapper。

**Wrapper** - 表示一个servlet定义的（如果servlet本身实现了SingleThreadModel，则可能支持多个servlet实例）。

- **结合整体的框架图中上述组件部分，我们看下包含了什么**？

![img](/ethen/imgs/tomcat/tomcat-x-container-3.png)

很明显，除了四个组件的嵌套关系，Container中还包含了Realm，Cluster，Listeners, Pipleline等支持组件。

这一点，还可以通过相关注释可以看出：

```html
**Loader** - Class loader to use for integrating new Java classes for this Container into the JVM in which Catalina is running.

**Logger** - Implementation of the log() method signatures of the ServletContext interface.

**Manager** - Manager for the pool of Sessions associated with this Container.

**Realm** - Read-only interface to a security domain, for authenticating user identities and their corresponding roles.

**Resources** - JNDI directory context enabling access to static resources, enabling custom linkages to existing server components when Catalina is embedded in a larger server.    
```



## [¶](#container的设计) Container的设计

> 这container应该包含哪些接口呢？如果你看源代码它包含二十多个接口，这里理解的时候一定要分组去理解。

### [¶](#container的层次结构方法) Container的层次结构方法

查找父容器的方法：

```java
/**
  * Get the parent container.
  *
  * @return Return the Container for which this Container is a child, if
  *         there is one. If there is no defined parent, return
  *         <code>null</code>.
  */
public Container getParent();


/**
  * Set the parent Container to which this Container is being added as a
  * child.  This Container may refuse to become attached to the specified
  * Container by throwing an exception.
  *
  * @param container Container to which this Container is being added
  *  as a child
  *
  * @exception IllegalArgumentException if this Container refuses to become
  *  attached to the specified Container
  */
public void setParent(Container container);
```



由于Engine显然上层是Service，所以里面加了一个getService的方法

```java
/**
  * Return the Service to which this container belongs.
  * @param container The container to start from
  * @return the Service, or null if not found
  */
public static Service getService(Container container) {
    while (container != null && !(container instanceof Engine)) {
        container = container.getParent();
    }
    if (container == null) {
        return null;
    }
    return ((Engine) container).getService();
}    
```



类比树接口，有Parent方法，那肯定也child方法：

```java
/**
  * Add a new child Container to those associated with this Container,
  * if supported.  Prior to adding this Container to the set of children,
  * the child's <code>setParent()</code> method must be called, with this
  * Container as an argument.  This method may thrown an
  * <code>IllegalArgumentException</code> if this Container chooses not
  * to be attached to the specified Container, in which case it is not added
  *
  * @param child New child Container to be added
  *
  * @exception IllegalArgumentException if this exception is thrown by
  *  the <code>setParent()</code> method of the child Container
  * @exception IllegalArgumentException if the new child does not have
  *  a name unique from that of existing children of this Container
  * @exception IllegalStateException if this Container does not support
  *  child Containers
  */
public void addChild(Container child);

/**
  * Obtain the child Containers associated with this Container.
  *
  * @return An array containing all children of this container. If this
  *         Container has no children, a zero-length array is returned.
  */
public Container[] findChildren();

/**
  * Remove an existing child Container from association with this parent
  * Container.
  *
  * @param child Existing child Container to be removed
  */
public void removeChild(Container child);

```

### [¶](#container事件监听相关方法) Container事件监听相关方法

前文我们也分析过Tomcat的事件监听机制，Container也是一样， 比如如下的ContainerListener

```java
/**
  * Add a container event listener to this component.
  *
  * @param listener The listener to add
  */
public void addContainerListener(ContainerListener listener);

/**
  * Obtain the container listeners associated with this Container.
  *
  * @return An array containing the container listeners associated with this
  *         Container. If this Container has no registered container
  *         listeners, a zero-length array is returned.
  */
public ContainerListener[] findContainerListeners();

/**
  * Remove a container event listener from this component.
  *
  * @param listener The listener to remove
  */
public void removeContainerListener(ContainerListener listener);    
```



除了Container级别的，和前文我们理解的一样，还有属性相关的Listener, 显然就增删属性的监听方法

```java
/**
  * Remove a property change listener from this component.
  *
  * @param listener The listener to remove
  */
public void removePropertyChangeListener(PropertyChangeListener listener);

/**
  * Add a property change listener to this component.
  *
  * @param listener The listener to add
  */
public void addPropertyChangeListener(PropertyChangeListener listener);    
```



最后显然还有事件的触发方法

```java
/**
  * Notify all container event listeners that a particular event has
  * occurred for this Container.  The default implementation performs
  * this notification synchronously using the calling thread.
  *
  * @param type Event type
  * @param data Event data
  */
public void fireContainerEvent(String type, Object data);    
```



### [¶](#container功能支撑方法) Container功能支撑方法

前面我们知道，Loader, Logger, Manager, Realm, Resources等支撑功能。这里简单看下接口定义，相关基本实现看下节ContainerBase的实现。

- Loader

```java
/**
  * Get the parent class loader.
  *
  * @return the parent class loader for this component. If not set, return
  *         {@link #getParent()}.{@link #getParentClassLoader()}. If no
  *         parent has been set, return the system class loader.
  */
public ClassLoader getParentClassLoader();


/**
  * Set the parent class loader for this component. For {@link Context}s
  * this call is meaningful only <strong>before</strong> a Loader has
  * been configured, and the specified value (if non-null) should be
  * passed as an argument to the class loader constructor.
  *
  * @param parent The new parent class loader
  */
public void setParentClassLoader(ClassLoader parent);
    
```

- Logger

```java
/**
  * Obtain the log to which events for this container should be logged.
  *
  * @return The Logger with which this Container is associated.  If there is
  *         no associated Logger, return the Logger associated with the
  *         parent Container (if any); otherwise return <code>null</code>.
  */
public Log getLogger();


/**
  * Return the logger name that the container will use.
  * @return the abbreviated name of this container for logging messages
  */
public String getLogName();
```



- Manager

体现在我们之前分析的JMX管理

```java
/**
  * Obtain the JMX name for this container.
  *
  * @return the JMX name associated with this container.
  */
public ObjectName getObjectName();


/**
  * Obtain the JMX domain under which this container will be / has been
  * registered.
  *
  * @return The JMX domain name
  */
public String getDomain();


/**
  * Calculate the key properties string to be added to an object's
  * {@link ObjectName} to indicate that it is associated with this container.
  *
  * @return          A string suitable for appending to the ObjectName
  *
  */
public String getMBeanKeyProperties();

/**
  * Obtain the number of threads available for starting and stopping any
  * children associated with this container. This allows start/stop calls to
  * children to be processed in parallel.
  *
  * @return The currently configured number of threads used to start/stop
  *         children associated with this container
  */
public int getStartStopThreads();   
```



- Realm

```java
/**
  * Obtain the Realm with which this Container is associated.
  *
  * @return The associated Realm; if there is no associated Realm, the
  *         Realm associated with the parent Container (if any); otherwise
  *         return <code>null</code>.
  */
public Realm getRealm();


/**
  * Set the Realm with which this Container is associated.
  *
  * @param realm The newly associated Realm
  */
public void setRealm(Realm realm);    
```



- Cluster

```java
/**
  * Get the Cluster for this container.
  *
  * @return The Cluster with which this Container is associated. If there is
  *         no associated Cluster, return the Cluster associated with our
  *         parent Container (if any); otherwise return <code>null</code>.
  */
public Cluster getCluster();


/**
  * Set the Cluster with which this Container is associated.
  *
  * @param cluster the Cluster with which this Container is associated.
  */
public void setCluster(Cluster cluster);
    
```



- 其它

```java
/**
  * Return a name string (suitable for use by humans) that describes this
  * Container.  Within the set of child containers belonging to a particular
  * parent, Container names must be unique.
  *
  * @return The human readable name of this container.
  */
public String getName();


/**
  * Set a name string (suitable for use by humans) that describes this
  * Container.  Within the set of child containers belonging to a particular
  * parent, Container names must be unique.
  *
  * @param name New name of this container
  *
  * @exception IllegalStateException if this Container has already been
  *  added to the children of a parent Container (after which the name
  *  may not be changed)
  */
public void setName(String name);

/**
  * Sets the number of threads available for starting and stopping any
  * children associated with this container. This allows start/stop calls to
  * children to be processed in parallel.
  * @param   startStopThreads    The new number of threads to be used
  */
public void setStartStopThreads(int startStopThreads);


/**
  * Obtain the location of CATALINA_BASE.
  *
  * @return  The location of CATALINA_BASE.
  */
public File getCatalinaBase();


/**
  * Obtain the location of CATALINA_HOME.
  *
  * @return The location of CATALINA_HOME.
  */
public File getCatalinaHome();
    
```



## [¶](#container基本实现：containerbase) Container基本实现：ContainerBase

> 就讲讲几个比较核心的

### [¶](#logger) Logger

日志记录器，比较简单，直接看代码

```java
/**
  * Return the Logger for this Container.
  */
@Override
public Log getLogger() {
    if (logger != null)
        return logger;
    logger = LogFactory.getLog(getLogName());
    return logger;
}


/**
  * @return the abbreviated name of this container for logging messages
  */
@Override
public String getLogName() {

    if (logName != null) {
        return logName;
    }
    String loggerName = null;
    Container current = this;
    while (current != null) {
        String name = current.getName();
        if ((name == null) || (name.equals(""))) {
            name = "/";
        } else if (name.startsWith("##")) {
            name = "/" + name;
        }
        loggerName = "[" + name + "]"
            + ((loggerName != null) ? ("." + loggerName) : "");
        current = current.getParent();
    }
    logName = ContainerBase.class.getName() + "." + loggerName;
    return logName;

}
    
```

### [¶](#cluster) Cluster

- `getCluster`：读锁，获取子类的cluster，如果没有则返回父类的cluster；
- `getClusterInternal`: 读锁，获取子类的cluster
- `setCluster`: 写锁，设置container的cluster；由于cluster具备生命周期，所以需要对停止旧的cluster，启动新的cluster；设置成功后，再触发cluster变更事件。

```java
/**
  * The cluster with which this Container is associated.
  */
protected Cluster cluster = null;
private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();

/**
  * The parent Container to which this Container is a child.
  */
protected Container parent = null;

/**
  * Return the Cluster with which this Container is associated.  If there is
  * no associated Cluster, return the Cluster associated with our parent
  * Container (if any); otherwise return <code>null</code>.
  */
@Override
public Cluster getCluster() {
    Lock readLock = clusterLock.readLock();
    readLock.lock();
    try {
        if (cluster != null)
            return cluster;

        if (parent != null)
            return parent.getCluster();

        return null;
    } finally {
        readLock.unlock();
    }
}


/*
  * Provide access to just the cluster component attached to this container.
  */
protected Cluster getClusterInternal() {
    Lock readLock = clusterLock.readLock();
    readLock.lock();
    try {
        return cluster;
    } finally {
        readLock.unlock();
    }
}


/**
  * Set the Cluster with which this Container is associated.
  *
  * @param cluster The newly associated Cluster
  */
@Override
public void setCluster(Cluster cluster) {

    Cluster oldCluster = null;
    Lock writeLock = clusterLock.writeLock();
    writeLock.lock();
    try {
        // Change components if necessary
        oldCluster = this.cluster;
        if (oldCluster == cluster)
            return;
        this.cluster = cluster;

        // Stop the old component if necessary
        if (getState().isAvailable() && (oldCluster != null) &&
            (oldCluster instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldCluster).stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.cluster.stop"), e);
            }
        }

        // Start the new component if necessary
        if (cluster != null)
            cluster.setContainer(this);

        if (getState().isAvailable() && (cluster != null) &&
            (cluster instanceof Lifecycle)) {
            try {
                ((Lifecycle) cluster).start();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.cluster.start"), e);
            }
        }
    } finally {
        writeLock.unlock();
    }

    // Report this property change to interested listeners
    support.firePropertyChange("cluster", oldCluster, cluster);
}
```



### [¶](#realm) Realm

Realm和上面的Cluster方法基本一致。

```java
/**
 * Return the Realm with which this Container is associated.  If there is
 * no associated Realm, return the Realm associated with our parent
 * Container (if any); otherwise return <code>null</code>.
 */
@Override
public Realm getRealm() {

    Lock l = realmLock.readLock();
    l.lock();
    try {
        if (realm != null)
            return realm;
        if (parent != null)
            return parent.getRealm();
        return null;
    } finally {
        l.unlock();
    }
}


protected Realm getRealmInternal() {
    Lock l = realmLock.readLock();
    l.lock();
    try {
        return realm;
    } finally {
        l.unlock();
    }
}

/**
 * Set the Realm with which this Container is associated.
 *
 * @param realm The newly associated Realm
 */
@Override
public void setRealm(Realm realm) {

    Lock l = realmLock.writeLock();
    l.lock();
    try {
        // Change components if necessary
        Realm oldRealm = this.realm;
        if (oldRealm == realm)
            return;
        this.realm = realm;

        // Stop the old component if necessary
        if (getState().isAvailable() && (oldRealm != null) &&
            (oldRealm instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldRealm).stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.realm.stop"), e);
            }
        }

        // Start the new component if necessary
        if (realm != null)
            realm.setContainer(this);
        if (getState().isAvailable() && (realm != null) &&
            (realm instanceof Lifecycle)) {
            try {
                ((Lifecycle) realm).start();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.realm.start"), e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("realm", oldRealm, this.realm);
    } finally {
        l.unlock();
    }
}    
```



### [¶](#name等属性) name等属性

此类属性改变时触发属性变更事件，比如name是容器的名字，name变更会触发name变更事件。

```java
/**
  * The human-readable name of this Container.
  */
protected String name = null;


/**
  * Return a name string (suitable for use by humans) that describes this
  * Container.  Within the set of child containers belonging to a particular
  * parent, Container names must be unique.
  */
@Override
public String getName() {
    return name;
}


/**
  * Set a name string (suitable for use by humans) that describes this
  * Container.  Within the set of child containers belonging to a particular
  * parent, Container names must be unique.
  *
  * @param name New name of this container
  *
  * @exception IllegalStateException if this Container has already been
  *  added to the children of a parent Container (after which the name
  *  may not be changed)
  */
@Override
public void setName(String name) {
    if (name == null) {
        throw new IllegalArgumentException(sm.getString("containerBase.nullName"));
    }
    String oldName = this.name;
    this.name = name;
    support.firePropertyChange("name", oldName, this.name);
}
```



### [¶](#child相关) child相关

添加子容器

```java
/**
  * Add a new child Container to those associated with this Container,
  * if supported.  Prior to adding this Container to the set of children,
  * the child's <code>setParent()</code> method must be called, with this
  * Container as an argument.  This method may thrown an
  * <code>IllegalArgumentException</code> if this Container chooses not
  * to be attached to the specified Container, in which case it is not added
  *
  * @param child New child Container to be added
  *
  * @exception IllegalArgumentException if this exception is thrown by
  *  the <code>setParent()</code> method of the child Container
  * @exception IllegalArgumentException if the new child does not have
  *  a name unique from that of existing children of this Container
  * @exception IllegalStateException if this Container does not support
  *  child Containers
  */
@Override
public void addChild(Container child) {
    if (Globals.IS_SECURITY_ENABLED) {
        PrivilegedAction<Void> dp =
            new PrivilegedAddChild(child);
        AccessController.doPrivileged(dp);
    } else {
        addChildInternal(child);
    }
}

private void addChildInternal(Container child) {

    if (log.isDebugEnabled()) {
        log.debug("Add child " + child + " " + this);
    }

    synchronized(children) {
        if (children.get(child.getName()) != null)
            throw new IllegalArgumentException(
                    sm.getString("containerBase.child.notUnique", child.getName()));
        child.setParent(this);  // May throw IAE 设置父容器
        children.put(child.getName(), child); // 使用map,方便通过name查找子容器
    }

    fireContainerEvent(ADD_CHILD_EVENT, child); // 触发添加子容器的事件

    // Start child // 注意下这里，没有将start方法放到synchronized的原因
    // Don't do this inside sync block - start can be a slow process and
    // locking the children object can cause problems elsewhere
    try {
        if ((getState().isAvailable() ||
                LifecycleState.STARTING_PREP.equals(getState())) &&
                startChildren) {
            child.start();
        }
    } catch (LifecycleException e) {
        throw new IllegalStateException(sm.getString("containerBase.child.start"), e);
    }
}    
```



查找子容器

```java
/**
  * Return the child Container, associated with this Container, with
  * the specified name (if any); otherwise, return <code>null</code>
  *
  * @param name Name of the child Container to be retrieved
  */
@Override
public Container findChild(String name) {
    if (name == null) {
        return null;
    }
    synchronized (children) {
        return children.get(name);
    }
}
/**
  * Return the set of children Containers associated with this Container.
  * If this Container has no children, a zero-length array is returned.
  */
@Override
public Container[] findChildren() {
    synchronized (children) {
        Container results[] = new Container[children.size()];
        return children.values().toArray(results);
    }
}    
```



- 删除子容器

子容器有生命周期，所以应该是先停止，然后销毁（distroy), 再触发删除事件，最后将children中子容器删除。

```java
/**
  * Remove an existing child Container from association with this parent
  * Container.
  *
  * @param child Existing child Container to be removed
  */
@Override
public void removeChild(Container child) {

    if (child == null) {
        return;
    }

    try {
        if (child.getState().isAvailable()) {
            child.stop();
        }
    } catch (LifecycleException e) {
        log.error(sm.getString("containerBase.child.stop"), e);
    }

    boolean destroy = false;
    try {
        // child.destroy() may have already been called which would have
        // triggered this call. If that is the case, no need to destroy the
        // child again.
        if (!LifecycleState.DESTROYING.equals(child.getState())) {
            child.destroy();
            destroy = true;
        }
    } catch (LifecycleException e) {
        log.error(sm.getString("containerBase.child.destroy"), e);
    }

    if (!destroy) {
        fireContainerEvent(REMOVE_CHILD_EVENT, child);
    }

    synchronized(children) {
        if (children.get(child.getName()) == null)
            return;
        children.remove(child.getName());
    }

}
    
```

### [¶](#lifecycle的模板方法) Lifecycle的模板方法

- **initInternal**

startStopThreads 默认为 1 ，所以 reconfigureStartStopExecutor 方法会走 if 语句，而 startStopExecutor 最开始是没有赋值的，startStopExecutor instanceof InlineExecutorService 会返回 false，因此最终会执行 startStopExecutor = new InlineExecutorService()，InlineExecutorService 只是简单地实现了 java.util.concurrent.AbstractExecutorService 类。 最终 reconfigureStartStopExecutor 给 startStopExecutor 这个成员变量设置了，startStopExecutor。

```java
/**
  * The number of threads available to process start and stop events for any
  * children associated with this container.
  */
private int startStopThreads = 1;
protected ExecutorService startStopExecutor;


@Override
protected void initInternal() throws LifecycleException {
    reconfigureStartStopExecutor(getStartStopThreads()); // 设置一个线程池来处理子容器启动和关闭事件
    super.initInternal(); // 调用LifecycleMBeanBase的方法
}


private void reconfigureStartStopExecutor(int threads) {
    if (threads == 1) {
        // Use a fake executor
        if (!(startStopExecutor instanceof InlineExecutorService)) {
            startStopExecutor = new InlineExecutorService(); // 执行这里
        }
    } else {
        // Delegate utility execution to the Service
        Server server = Container.getService(this).getServer();
        server.setUtilityThreads(threads);
        startStopExecutor = server.getUtilityExecutor();
    }
}    
```



- **startInternal**

试想，container中有很多组件，而且属于Lifecycle生命周期管理；那么启动容器的时候，必然是逐个将这些子组件（包括子容器）启动起来。

```java
/**
  * Start this component and implement the requirements
  * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
  *
  * @exception LifecycleException if this component detects a fatal error
  *  that prevents this component from being used
  */
@Override
protected synchronized void startInternal() throws LifecycleException {

    // Start our subordinate components, if any
    logger = null;
    getLogger();
    Cluster cluster = getClusterInternal();
    if (cluster instanceof Lifecycle) {
        ((Lifecycle) cluster).start();
    }
    Realm realm = getRealmInternal();
    if (realm instanceof Lifecycle) {
        ((Lifecycle) realm).start();
    }

    // Start our child containers, if any
    Container children[] = findChildren();
    List<Future<Void>> results = new ArrayList<>();
    for (Container child : children) {
        results.add(startStopExecutor.submit(new StartChild(child)));
    }

    MultiThrowable multiThrowable = null; // 引入一个MultiThrowable，来收集多个异常

    for (Future<Void> result : results) {
        try {
            result.get();
        } catch (Throwable e) {
            log.error(sm.getString("containerBase.threadedStartFailed"), e);
            if (multiThrowable == null) {
                multiThrowable = new MultiThrowable();
            }
            multiThrowable.add(e);
        }

    }
    if (multiThrowable != null) {
        throw new LifecycleException(sm.getString("containerBase.threadedStartFailed"),
                multiThrowable.getThrowable());
    }

    // Start the Valves in our pipeline (including the basic), if any
    if (pipeline instanceof Lifecycle) {
        ((Lifecycle) pipeline).start();
    }

    setState(LifecycleState.STARTING);

    // 看这个，本质是调用最上层server的utilityExecutorWrapper 线程池去执行 ContainerBackgroundProcessorMonitor 任务
    if (backgroundProcessorDelay > 0) {
        monitorFuture = Container.getService(ContainerBase.this).getServer()
                .getUtilityExecutor().scheduleWithFixedDelay(
                        new ContainerBackgroundProcessorMonitor(), 0, 60, TimeUnit.SECONDS);
    }
}   
```



- **stopInternal**

和initInternal初始化子组件方式倒过来，逐一停止子组件，并触发相关事件。

```java
/**
  * Stop this component and implement the requirements
  * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
  *
  * @exception LifecycleException if this component detects a fatal error
  *  that prevents this component from being used
  */
@Override
protected synchronized void stopInternal() throws LifecycleException {

    // Stop our thread
    if (monitorFuture != null) {
        monitorFuture.cancel(true);
        monitorFuture = null;
    }
    threadStop();

    setState(LifecycleState.STOPPING);

    // Stop the Valves in our pipeline (including the basic), if any
    if (pipeline instanceof Lifecycle &&
            ((Lifecycle) pipeline).getState().isAvailable()) {
        ((Lifecycle) pipeline).stop();
    }

    // Stop our child containers, if any
    Container children[] = findChildren();
    List<Future<Void>> results = new ArrayList<>();
    for (Container child : children) {
        results.add(startStopExecutor.submit(new StopChild(child)));
    }

    boolean fail = false;
    for (Future<Void> result : results) {
        try {
            result.get();
        } catch (Exception e) {
            log.error(sm.getString("containerBase.threadedStopFailed"), e);
            fail = true;
        }
    }
    if (fail) {
        throw new LifecycleException(
                sm.getString("containerBase.threadedStopFailed"));
    }

    // Stop our subordinate components, if any
    Realm realm = getRealmInternal();
    if (realm instanceof Lifecycle) {
        ((Lifecycle) realm).stop();
    }
    Cluster cluster = getClusterInternal();
    if (cluster instanceof Lifecycle) {
        ((Lifecycle) cluster).stop();
    }
}    
```



- **destroyInternal**

对比下initInternal，它初始化了什么就destory什么

```java
@Override
protected void destroyInternal() throws LifecycleException {

    Realm realm = getRealmInternal();
    if (realm instanceof Lifecycle) {
        ((Lifecycle) realm).destroy();
    }
    Cluster cluster = getClusterInternal();
    if (cluster instanceof Lifecycle) {
        ((Lifecycle) cluster).destroy();
    }

    // Stop the Valves in our pipeline (including the basic), if any
    if (pipeline instanceof Lifecycle) {
        ((Lifecycle) pipeline).destroy();
    }

    // Remove children now this container is being destroyed
    for (Container child : findChildren()) {
        removeChild(child);
    }

    // Required if the child is destroyed directly.
    if (parent != null) {
        parent.removeChild(this);
    }

    // If init fails, this may be null
    if (startStopExecutor != null) {
        startStopExecutor.shutdownNow();
    }

    super.destroyInternal(); // 调用LifecycleMBeanBase的方法
}
```