# Tomcat - 事件的监听机制：观察者模式

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-listener.html

##  引入

> 前几篇文章中，我们经常会涉及到Listener相关的内容，比如如下内容中；我们通过引入这些内容，来具体探讨事件监听机制。

- **Lifecycle中出现的监听器**

（老的版本中是LifecycleSupport接口）

```java
public interface Lifecycle {
    /** 第1类：针对监听器 **/
    // 添加监听器
    public void addLifecycleListener(LifecycleListener listener);
    // 获取所以监听器
    public LifecycleListener[] findLifecycleListeners();
    // 移除某个监听器
    public void removeLifecycleListener(LifecycleListener listener);
    ...
}
    
```

- **多个组件中出现监听器**

对应到整体架构图中

![img](/ethen/imgs/tomcat/tomcat-x-listener-1.jpg)

对应到代码中

![img](/ethen/imgs/tomcat/tomcat-x-listener-2.jpg)

## [¶](#知识准备) 知识准备

> 理解上述监听器的需要你有些知识储备，一是设计模式中的观察者模式，另一个是事件监听机制。

### [¶](#观察者模式) 观察者模式

> 观察者模式(observer pattern): 在对象之间定义一对多的依赖, 这样一来, 当一个对象改变状态, 依赖它的对象都会收到通知, 并自动更新

主题(Subject)具有注册和移除观察者、并通知所有观察者的功能，主题是通过维护一张观察者列表来实现这些操作的。

观察者(Observer)的注册功能需要调用主题的 registerObserver() 方法。

![img](https://pdai-1257820000.cos.ap-beijing.myqcloud.com/pdai.tech/public/_images/pics/0df5d84c-e7ca-4e3a-a688-bb8e68894467.png)

详情请参考 设计模式：[行为型 - 观察者(Observer) ]()

### [¶](#事件监听机制) 事件监听机制

> JDK 1.0及更早版本的事件模型基于职责链模式，但是这种模型不适用于复杂的系统，因此在JDK 1.1及以后的各个版本中，事件处理模型采用基于观察者模式的委派事件模型(DelegationEvent Model, DEM)，即一个Java组件所引发的事件并不由引发事件的对象自己来负责处理，而是委派给独立的事件处理对象负责。这并不是说事件模型是基于Observer和Observable的，事件模型与Observer和Observable没有任何关系，Observer和Observable只是观察者模式的一种实现而已。

java中的事件机制的参与者有**3种角色**

- `Event Eource`：事件源，发起事件的主体。
- `Event Object`：事件状态对象，传递的信息载体，就好比Watcher的update方法的参数，可以是事件源本身，一般作为参数存在于listerner 的方法之中。
- `Event Listener`：事件监听器，当它监听到event object产生的时候，它就调用相应的方法，进行处理。

其实还有个东西比较重要：事件环境，在这个环境中，可以添加事件监听器，可以产生事件，可以触发事件监听器。

![img](/ethen/imgs/tomcat/tomcat-x-listener-3.png)

这个和观察者模式大同小异，但要比观察者模式复杂一些。一些逻辑需要手动实现，比如注册监听器，删除监听器，获取监听器数量等等，这里的eventObject也是你自己实现的。

> 下面我们看下Java中事件机制的实现，理解下面的类结构将帮助你Tomcat中监听机制的实现。

- 监听器

```java
public interface EventListener extends java.util.EventListener {
    void handleEvent(EventObject event);
}
```



- 监听事件

```java
public class EventObject extends java.util.EventObject{
    private static final long serialVersionUID = 1L;
    public EventObject(Object source){
        super(source);
    }
    public void doEvent(){
        System.out.println("通知一个事件源 source :"+ this.getSource());
    }
}
```



- 事件源：

```java
public class EventSource {
    //监听器列表，监听器的注册则加入此列表
    private Vector<EventListener> ListenerList = new Vector<>();
 
    //注册监听器
    public void addListener(EventListener eventListener) {
        ListenerList.add(eventListener);
    }
 
    //撤销注册
    public void removeListener(EventListener eventListener) {
        ListenerList.remove(eventListener);
    }
 
    //接受外部事件
    public void notifyListenerEvents(EventObject event) {
        for (EventListener eventListener : ListenerList) {
            eventListener.handleEvent(event);
        }
    }

}
```



- 测试

```java
public static void main(String[] args) {
    EventSource eventSource = new EventSource();
    eventSource.addListener(new EventListener() {
        @Override
        public void handleEvent(EventObject event) {
            event.doEvent();
            if (event.getSource().equals("closeWindows")) {
                System.out.println("doClose");
            }
        }
    });
    eventSource.addListener(new EventListener() {
        @Override
        public void handleEvent(EventObject event) {
            System.out.println("gogogo");
        }
    });
    /*
      * 传入openWindows事件，通知listener，事件监听器，
      对open事件感兴趣的listener将会执行
      **/
    eventSource.notifyListenerEvents(new EventObject("openWindows"));
}

```

## [¶](#tomcat中监听机制server部分) Tomcat中监听机制（Server部分）

> 基于上面的事件监听的代码结构，你就能知道Tomcat中事件监听的类结构了。

- 首先要定义一个监听器，它有一个监听方法，用来接受一个监听事件

```java
public interface LifecycleListener {
    /**
     * Acknowledge the occurrence of the specified event.
     *
     * @param event LifecycleEvent that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event);
}
```



- 监听事件, 由于它是lifecycle的监听器，所以它握有一个lifecycle实例

```java
/**
 * General event for notifying listeners of significant changes on a component
 * that implements the Lifecycle interface.
 *
 * @author Craig R. McClanahan
 */
public final class LifecycleEvent extends EventObject {

    private static final long serialVersionUID = 1L;


    /**
     * Construct a new LifecycleEvent with the specified parameters.
     *
     * @param lifecycle Component on which this event occurred
     * @param type Event type (required)
     * @param data Event data (if any)
     */
    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {
        super(lifecycle);
        this.type = type;
        this.data = data;
    }


    /**
     * The event data associated with this event.
     */
    private final Object data;


    /**
     * The event type this instance represents.
     */
    private final String type;


    /**
     * @return the event data of this event.
     */
    public Object getData() {
        return data;
    }


    /**
     * @return the Lifecycle on which this event occurred.
     */
    public Lifecycle getLifecycle() {
        return (Lifecycle) getSource();
    }


    /**
     * @return the event type of this event.
     */
    public String getType() {
        return this.type;
    }
}
```



- 事件源的接口和实现

事件源的接口：在Lifecycle中

```java
public interface Lifecycle {
    /** 第1类：针对监听器 **/
    // 添加监听器
    public void addLifecycleListener(LifecycleListener listener);
    // 获取所以监听器
    public LifecycleListener[] findLifecycleListeners();
    // 移除某个监听器
    public void removeLifecycleListener(LifecycleListener listener);
    ...
}    
```



事件源的实现： 在 LifecycleBase 中

```java
 /**
  * The list of registered LifecycleListeners for event notifications.
  */
private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

/**
  * {@inheritDoc}
  */
@Override
public void addLifecycleListener(LifecycleListener listener) {
    lifecycleListeners.add(listener);
}


/**
  * {@inheritDoc}
  */
@Override
public LifecycleListener[] findLifecycleListeners() {
    return lifecycleListeners.toArray(new LifecycleListener[0]);
}


/**
  * {@inheritDoc}
  */
@Override
public void removeLifecycleListener(LifecycleListener listener) {
    lifecycleListeners.remove(listener);
}


/**
  * Allow sub classes to fire {@link Lifecycle} events.
  *
  * @param type  Event type
  * @param data  Data associated with event.
  */
protected void fireLifecycleEvent(String type, Object data) {
    LifecycleEvent event = new LifecycleEvent(this, type, data);
    for (LifecycleListener listener : lifecycleListeners) {
        listener.lifecycleEvent(event);
    }
}    
```



- 接下来是调用了

比如在LifecycleBase, 停止方法是基于LifecycleState状态改变来触发上面的fireLifecycleEvent方法：

```java
@Override
public final synchronized void stop() throws LifecycleException {

    if (LifecycleState.STOPPING_PREP.equals(state) || LifecycleState.STOPPING.equals(state) ||
            LifecycleState.STOPPED.equals(state)) {

        if (log.isDebugEnabled()) {
            Exception e = new LifecycleException();
            log.debug(sm.getString("lifecycleBase.alreadyStopped", toString()), e);
        } else if (log.isInfoEnabled()) {
            log.info(sm.getString("lifecycleBase.alreadyStopped", toString()));
        }

        return;
    }

    if (state.equals(LifecycleState.NEW)) {
        state = LifecycleState.STOPPED;
        return;
    }

    if (!state.equals(LifecycleState.STARTED) && !state.equals(LifecycleState.FAILED)) {
        invalidTransition(Lifecycle.BEFORE_STOP_EVENT);
    }

    try {
        if (state.equals(LifecycleState.FAILED)) {
            // @pdai：看这里
            fireLifecycleEvent(BEFORE_STOP_EVENT, null);
        } else {
            setStateInternal(LifecycleState.STOPPING_PREP, null, false);
        }

        stopInternal();

        // Shouldn't be necessary but acts as a check that sub-classes are
        // doing what they are supposed to.
        if (!state.equals(LifecycleState.STOPPING) && !state.equals(LifecycleState.FAILED)) {
            invalidTransition(Lifecycle.AFTER_STOP_EVENT);
        }

        setStateInternal(LifecycleState.STOPPED, null, false);
    } catch (Throwable t) {
        handleSubClassException(t, "lifecycleBase.stopFail", toString());
    } finally {
        if (this instanceof Lifecycle.SingleUse) {
            // Complete stop process first
            setStateInternal(LifecycleState.STOPPED, null, false);
            destroy();
        }
    }
}
  
```