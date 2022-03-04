#  Tomcat - 线程池的设计与实现：StandardThreadExecutor

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-executor.html

## 理解思路

> 我们如下几个方面开始引入线程池的，这里主要从上文Service引入，保持上下文之间的衔接，会很好的构筑你的知识体系。@pdai

- 上文中我们了解到，Executor是包含在Service中的，Service中关于Executor的配置和相关代码如下：

server.xml中service里包含Executor的配置

```xml
<Service name="Catalina">
<!-- 1. 属性说明
	name:Service的名称
-->

    <!--2. 一个或多个excecutors --> // 看这里
    <!--
    <Executor name="tomcatThreadPool" namePrefix="catalina-exec-"
        maxThreads="150" minSpareThreads="4"/>
    -->
</Service>    
    
```



Service中executors相关方法

```java
/**
  * Adds a named executor to the service
  * @param ex Executor
  */
@Override
public void addExecutor(Executor ex) {
    synchronized (executors) {
        if (!executors.contains(ex)) {
            executors.add(ex);
            if (getState().isAvailable()) {
                try {
                    ex.start(); // 启动
                } catch (LifecycleException x) {
                    log.error(sm.getString("standardService.executor.start"), x);
                }
            }
        }
    }
}

/**
  * Retrieves all executors
  * @return Executor[]
  */
@Override
public Executor[] findExecutors() {
    synchronized (executors) {
        Executor[] arr = new Executor[executors.size()];
        executors.toArray(arr);
        return arr;
    }
}


/**
  * Retrieves executor by name, null if not found
  * @param executorName String
  * @return Executor
  */
@Override
public Executor getExecutor(String executorName) {
    synchronized (executors) {
        for (Executor executor: executors) {
            if (executorName.equals(executor.getName()))
                return executor;
        }
    }
    return null;
}

/**
  * Removes an executor from the service
  * @param ex Executor
  */
@Override
public void removeExecutor(Executor ex) {
    synchronized (executors) {
        if ( executors.remove(ex) && getState().isAvailable() ) {
            try {
                ex.stop(); // 停止
            } catch (LifecycleException e) {
                log.error(sm.getString("standardService.executor.stop"), e);
            }
        }
    }
}
    
```



- 和Server、Service实现一样，StandardThreadExecutor也是继承LifecycleMBeanBase；然后实现Executor的接口。

![img](/ethen/imgs/tomcat/tomcat-x-executor-1.jpg)

- Tomcat关于Executor相关的配置文档

http://tomcat.apache.org/tomcat-9.0-doc/config/executor.html

## [¶](#准备知识) 准备知识

> 在理解Tomcat的线程池时，需要有一定的基础，这里推荐学习下JDK关于线程池的设计和实现。

> 并发基础

- [Java 并发 - 理论基础]()
  - 多线程的出现是要解决什么问题的?
  - 线程不安全是指什么? 举例说明
  - 并发出现线程不安全的本质什么? 可见性，原子性和有序性。
  - Java是怎么解决并发问题的? 3个关键字，JMM和8个Happens-Before
  - 线程安全是不是非真即假? 不是
  - 线程安全有哪些实现思路?
  - 如何理解并发和并行的区别?
- [Java 并发 - 线程基础]()
  - 线程有哪几种状态? 分别说明从一种状态到另一种状态转变有哪些方式?
  - 通常线程有哪几种使用方式?
  - 基础线程机制有哪些?
  - 线程的中断方式有哪些?
  - 线程的互斥同步方式有哪些? 如何比较和选择?
  - 线程之间有哪些协作方式?

> JUC BlockingQueue 和 ThreadPoolExecutor

- [JUC集合: BlockingQueue详解]()
  - 什么是BlockingDeque?
  - BlockingQueue大家族有哪些? ArrayBlockingQueue, DelayQueue, LinkedBlockingQueue, SynchronousQueue...
  - BlockingQueue适合用在什么样的场景?
  - BlockingQueue常用的方法?
  - BlockingQueue插入方法有哪些? 这些方法(`add(o)`,`offer(o)`,`put(o)`,`offer(o, timeout, timeunit)`)的区别是什么?
  - BlockingDeque 与BlockingQueue有何关系，请对比下它们的方法?
  - BlockingDeque适合用在什么样的场景?
  - BlockingDeque大家族有哪些?
  - BlockingDeque 与BlockingQueue实现例子?
- [JUC线程池: ThreadPoolExecutor详解]()
  - 为什么要有线程池?
  - Java是实现和管理线程池有哪些方式?  请简单举例如何使用。
  - 为什么很多公司不允许使用Executors去创建线程池? 那么推荐怎么使用呢?
  - ThreadPoolExecutor有哪些核心的配置参数? 请简要说明
  - ThreadPoolExecutor可以创建哪是哪三种线程池呢?
  - 当队列满了并且worker的数量达到maxSize的时候，会怎么样?
  - 说说ThreadPoolExecutor有哪些RejectedExecutionHandler策略? 默认是什么策略?
  - 简要说下线程池的任务执行机制? execute –> addWorker –>runworker (getTask)
  - 线程池中任务是如何提交的?
  - 线程池中任务是如何关闭的?
  - 在配置线程池的时候需要考虑哪些配置因素?
  - 如何监控线程池的状态?

## [¶](#executor接口设计) Executor接口设计

> Executor的设计很简单，在理解的时候需要理解两点：

- 1.Tomcat希望将Executor也纳入Lifecycle**生命周期管理**，所以让它实现了Lifecycle接口
- 2.**引入超时机制**：也就是说当work queue满时，会等待指定的时间，如果超时将抛出RejectedExecutionException，所以这里增加了一个`void execute(Runnable command, long timeout, TimeUnit unit)`方法; 其实本质上，它构造了JUC中ThreadPoolExecutor，通过它调用ThreadPoolExecutor的`void execute(Runnable command, long timeout, TimeUnit unit)`方法。

```java
public interface Executor extends java.util.concurrent.Executor, Lifecycle {

    public String getName();

    /**
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the <code>Executor</code> implementation.
     * If no threads are available, it will be added to the work queue.
     * If the work queue is full, the system will wait for the specified
     * time until it throws a RejectedExecutionException
     *
     * @param command the runnable task
     * @param timeout the length of time to wait for the task to complete
     * @param unit    the units in which timeout is expressed
     *
     * @throws java.util.concurrent.RejectedExecutionException if this task
     * cannot be accepted for execution - the queue is full
     * @throws NullPointerException if command or unit is null
     */
    void execute(Runnable command, long timeout, TimeUnit unit);
}
    
```



找到Executor的实现类

![img](/ethen/imgs/tomcat/tomcat-x-executor-2.jpg)

## [¶](#standardthreadexecutor的实现) StandardThreadExecutor的实现

> 接下来我们看下具体的实现类StandardThreadExecutor。

### [¶](#理解相关配置参数) 理解相关配置参数

[Executor官方配置说明文档  (opens new window)](http://tomcat.apache.org/tomcat-9.0-doc/config/executor.html)

- 公共属性

Executor的所有实现都 支持以下属性：

| 属性      | 描述                                                         |
| --------- | ------------------------------------------------------------ |
| className | 实现的类。实现必须实现 org.apache.catalina.Executor接口。此接口确保可以通过其name属性引用对象并实现Lifecycle，以便可以使用容器启动和停止对象。className的默认值是org.apache.catalina.core.StandardThreadExecutor |
| name      | 用于在server.xml中的其他位置引用此池的名称。该名称是必需的，必须是唯一的。 |

- **StandardThreadExecutor属性**

默认实现支持以下属性：

| 属性                    | 描述                                                         |
| ----------------------- | ------------------------------------------------------------ |
| threadPriority          | （int）执行程序中线程的线程优先级，默认为 5（Thread.NORM_PRIORITY常量的值） |
| daemon                  | （boolean）线程是否应该是守护程序线程，默认为 true           |
| namePrefix              | （字符串）执行程序创建的每个线程的名称前缀。单个线程的线程名称将是namePrefix+threadNumber |
| maxThreads              | （int）此池中活动线程的最大数量，默认为 200                  |
| minSpareThreads         | （int）最小线程数（空闲和活动）始终保持活动状态，默认为 25   |
| maxIdleTime             | （int）空闲线程关闭之前的毫秒数，除非活动线程数小于或等于minSpareThreads。默认值为60000（1分钟） |
| maxQueueSize            | （int）在我们拒绝之前可以排队等待执行的可运行任务的最大数量。默认值是Integer.MAX_VALUE |
| prestartminSpareThreads | （boolean）是否应该在启动Executor时启动minSpareThreads，默认值为 false |
| threadRenewalDelay      | （long）如果配置了ThreadLocalLeakPreventionListener，它将通知此执行程序有关已停止的上下文。上下文停止后，池中的线程将被更新。为避免同时更新所有线程，此选项在任意2个线程的续订之间设置延迟。该值以ms为单位，默认值为1000ms。如果值为负，则不会续订线程。 |

### [¶](#lifecycle模板方法) Lifecycle模板方法

先看核心变量：

```java
// 任务队列
private TaskQueue taskqueue = null;

// 包装了一个ThreadPoolExecutor
protected ThreadPoolExecutor executor = null;
    
```



- **initInternal**和**destroyInternal**默认父类实现

```java
@Override
protected void initInternal() throws LifecycleException {
    super.initInternal();
}
@Override
protected void destroyInternal() throws LifecycleException {
    super.destroyInternal();
}
    
```



- **startInternal方法**

这个方法中，我们不难看出，就是初始化taskqueue，同时构造ThreadPoolExecutor的实例，后面Tomcat的StandardThreadExecutor的实现本质上通过ThreadPoolExecutor实现的。

```java
/**
  * Start the component and implement the requirements
  * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
  *
  * @exception LifecycleException if this component detects a fatal error
  *  that prevents this component from being used
  */
@Override
protected void startInternal() throws LifecycleException {

    taskqueue = new TaskQueue(maxQueueSize);
    TaskThreadFactory tf = new TaskThreadFactory(namePrefix,daemon,getThreadPriority());
    executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), maxIdleTime, TimeUnit.MILLISECONDS,taskqueue, tf);
    executor.setThreadRenewalDelay(threadRenewalDelay);
    if (prestartminSpareThreads) {
        executor.prestartAllCoreThreads();
    }
    taskqueue.setParent(executor);

    setState(LifecycleState.STARTING);
}
    
```

- **stopInternal方法**

代码很简单，关闭线程池后置null, 方便GC回收。

```java
/**
  * Stop the component and implement the requirements
  * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
  *
  * @exception LifecycleException if this component detects a fatal error
  *  that needs to be reported
  */
@Override
protected void stopInternal() throws LifecycleException {

    setState(LifecycleState.STOPPING);
    if (executor != null) {
        executor.shutdownNow();
    }
    executor = null;
    taskqueue = null;
}
    
```

### [¶](#核心executor方法) 核心executor方法

本质上就是调用ThreadPoolExecutor的实例的相关方法。

```java
@Override
public void execute(Runnable command, long timeout, TimeUnit unit) {
    if (executor != null) {
        executor.execute(command,timeout,unit);
    } else {
        throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
    }
}


@Override
public void execute(Runnable command) {
    if (executor != null) {
        try {
            executor.execute(command);
        } catch (RejectedExecutionException rx) {
            //there could have been contention around the queue
            if (!((TaskQueue) executor.getQueue()).force(command)) {
                throw new RejectedExecutionException(sm.getString("standardThreadExecutor.queueFull"));
            }
        }
    } else {
        throw new IllegalStateException(sm.getString("standardThreadExecutor.notStarted"));
    }
}
    
```



### [¶](#动态调整线程池) 动态调整线程池

我们还注意到StandardThreadExecutor还实现了ResizeableExecutor，从名称上我们就可知道它是希望实现对线程池的动态调整，所以呢，它封装了一个ResizeableExecutor的接口，看下接口。

```java
public interface ResizableExecutor extends Executor {

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize();

    public int getMaxThreads();

    /**
     * Returns the approximate number of threads that are actively executing
     * tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount();

    public boolean resizePool(int corePoolSize, int maximumPoolSize);

    public boolean resizeQueue(int capacity);

}
    
```

前三个方法比较简单，我们看下后两个方法是如何实现的, 其实也很简单。

```java
@Override
public boolean resizePool(int corePoolSize, int maximumPoolSize) {
    if (executor == null)
        return false;

    executor.setCorePoolSize(corePoolSize);
    executor.setMaximumPoolSize(maximumPoolSize);
    return true;
}

// 默认没有实现
@Override
public boolean resizeQueue(int capacity) {
    return false;
}
    
```



### [¶](#补充taskqueue) 补充TaskQueue

我们知道工作队列是有TaskQueue保障的，它集成自LinkedBlockingQueue（一个阻塞的链表队列），来看下源代码吧。

```java
/**
 * As task queue specifically designed to run with a thread pool executor. The
 * task queue is optimised to properly utilize threads within a thread pool
 * executor. If you use a normal queue, the executor will spawn threads when
 * there are idle threads and you wont be able to force items onto the queue
 * itself.
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 1L;
    protected static final StringManager sm = StringManager
            .getManager("org.apache.tomcat.util.threads.res");
    private static final int DEFAULT_FORCED_REMAINING_CAPACITY = -1;

    private transient volatile ThreadPoolExecutor parent = null;

    // No need to be volatile. This is written and read in a single thread
    // (when stopping a context and firing the listeners)
    private int forcedRemainingCapacity = -1;

    public TaskQueue() {
        super();
    }

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }

    public boolean force(Runnable o) {
        if (parent == null || parent.isShutdown()) throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        return super.offer(o); //forces the item onto the queue, to be used if the task is rejected
    }

    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (parent == null || parent.isShutdown()) throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        return super.offer(o,timeout,unit); //forces the item onto the queue, to be used if the task is rejected
    }

    @Override
    public boolean offer(Runnable o) {
      //we can't do any checks
        if (parent==null) return super.offer(o);
        //we are maxed out on threads, simply queue the object
        if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);
        //we have idle threads, just add it to the queue
        if (parent.getSubmittedCount()<=(parent.getPoolSize())) return super.offer(o);
        //if we have less threads than maximum force creation of a new thread
        if (parent.getPoolSize()<parent.getMaximumPoolSize()) return false;
        //if we reached here, we need to add it to the queue
        return super.offer(o);
    }


    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // the poll timed out, it gives an opportunity to stop the current
            // thread if needed to avoid memory leaks.
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, this may return null (in case of timeout) which normally
            // does not occur with take()
            // but the ThreadPoolExecutor implementation allows this
        }
        return super.take();
    }

    @Override
    public int remainingCapacity() {
        if (forcedRemainingCapacity > DEFAULT_FORCED_REMAINING_CAPACITY) {
            // ThreadPoolExecutor.setCorePoolSize checks that
            // remainingCapacity==0 to allow to interrupt idle threads
            // I don't see why, but this hack allows to conform to this
            // "requirement"
            return forcedRemainingCapacity;
        }
        return super.remainingCapacity();
    }

    public void setForcedRemainingCapacity(int forcedRemainingCapacity) {
        this.forcedRemainingCapacity = forcedRemainingCapacity;
    }

    void resetForcedRemainingCapacity() {
        this.forcedRemainingCapacity = DEFAULT_FORCED_REMAINING_CAPACITY;
    }

}
    
```



TaskQueue这个任务队列是专门为线程池而设计的。优化任务队列以适当地利用线程池执行器内的线程。

如果你使用一个普通的队列，当有空闲线程executor将产生线程并且你不能强制将任务添加到队列。

### [¶](#为什么不是直接使用threadpoolexecutor) 为什么不是直接使用ThreadPoolExecutor

这里你是否考虑过一个问题，为什么Tomcat会自己构造一个StandardThreadExecutor而不是直接使用ThreadPoolExecutor？

从上面的代码，你会发现这里只是使用executor只是使用了execute的两个主要方法，它希望让调用层屏蔽掉ThreadPoolExecutor的其它方法：

- 它体现的原则：**最少知识原则**: 只和你的密友谈话。也就是说客户对象所需要交互的对象应当尽可能少
- 它体现的设计模式：[结构型 - 外观(Facade)]()
  - 外观模式(Facade pattern)，它提供了一个统一的接口，用来访问子系统中的一群接口，从而让子系统更容易使用