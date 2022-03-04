#  Tomcat - 启动过程：初始化和启动流程

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-start.html

## 总体流程

> 很多人在看框架代码的时候会很难抓住重点的，而一开始了解整体流程会很大程度提升理解的效率。@pdai

我们看下整体的初始化和启动的流程，在**理解的时候可以直接和Tomcat架构设计中组件关联上**：

![img](/ethen/imgs/tomcat/tomcat-x-start-1.png)

## [¶](#代码浅析) 代码浅析

看了下网上关于Tomcat的文章，很多直接关注在纯代码的分析，这种是很难的；我建议你一定要把代码加载进来自己看一下，然后这里我把它转化为核心的几个问题来帮助你理解。@pdai

### [¶](#bootstrap主入口) Bootstrap主入口？

Tomcat源码就从它的main方法开始。Tomcat的main方法在org.apache.catalina.startup.Bootstrap 里。 如下代码我们就是创建一个 Bootstrap 对象，调用它的 init 方法初始化，然后根据启动参数，分别调用 Bootstrap 对象的不同方法。

```java
public final class Bootstrap {
    ……
    
    /**
     * Daemon object used by main.
     */
    private static final Object daemonLock = new Object();
    
    ……
    
    
   /**
     * Main method and entry point when starting Tomcat via the provided
     * scripts.
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String args[]) {
        // 创建一个 Bootstrap 对象，调用它的 init 方法初始化
        synchronized (daemonLock) {
            if (daemon == null) {
                // Don't set daemon until init() has completed
                Bootstrap bootstrap = new Bootstrap();
                try {
                    bootstrap.init();
                } catch (Throwable t) {
                    handleThrowable(t);
                    t.printStackTrace();
                    return;
                }
                daemon = bootstrap;
            } else {
                // When running as a service the call to stop will be on a new
                // thread so make sure the correct class loader is used to
                // prevent a range of class not found exceptions.
                Thread.currentThread().setContextClassLoader(daemon.catalinaLoader);
            }
        }

        // 根据启动参数，分别调用 Bootstrap 对象的不同方法
        try {
            String command = "start"; // 默认是start
            if (args.length > 0) {
                command = args[args.length - 1];
            }

            if (command.equals("startd")) {
                args[args.length - 1] = "start";
                daemon.load(args);
                daemon.start();
            } else if (command.equals("stopd")) {
                args[args.length - 1] = "stop";
                daemon.stop();
            } else if (command.equals("start")) {
                daemon.setAwait(true);
                daemon.load(args);
                daemon.start();
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
            } else if (command.equals("stop")) {
                daemon.stopServer(args);
            } else if (command.equals("configtest")) {
                daemon.load(args);
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
                System.exit(0);
            } else {
                log.warn("Bootstrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            // Unwrap the Exception for clearer error reporting
            if (t instanceof InvocationTargetException &&
                    t.getCause() != null) {
                t = t.getCause();
            }
            handleThrowable(t);
            t.printStackTrace();
            System.exit(1);
        }

    }
    
    ……
}
  
```



### [¶](#bootstrap如何初始化catalina的) Bootstrap如何初始化Catalina的？

我们用`Sequence Diagram`插件来看main方法的时序图，但是可以发现它并没有帮我们画出Bootstrap初始化Catalina的过程，这和上面的组件初始化不符合？

![img](/ethen/imgs/tomcat/tomcat-x-start-2.png)

让我们带着这个为看下Catalina的初始化的

```java
/**
  * 初始化守护进程
  * 
  * @throws Exception Fatal initialization error
  */
public void init() throws Exception {

    // 初始化classloader（包括catalinaLoader），下文将具体分析
    initClassLoaders();

    // 设置当前的线程的contextClassLoader为catalinaLoader
    Thread.currentThread().setContextClassLoader(catalinaLoader);

    SecurityClassLoad.securityClassLoad(catalinaLoader);

    // 通过catalinaLoader加载Catalina，并初始化startupInstance 对象
    if (log.isDebugEnabled())
        log.debug("Loading startup class");
    Class<?> startupClass = catalinaLoader.loadClass("org.apache.catalina.startup.Catalina");
    Object startupInstance = startupClass.getConstructor().newInstance();

    // 通过反射调用了setParentClassLoader 方法
    if (log.isDebugEnabled())
        log.debug("Setting startup class properties");
    String methodName = "setParentClassLoader";
    Class<?> paramTypes[] = new Class[1];
    paramTypes[0] = Class.forName("java.lang.ClassLoader");
    Object paramValues[] = new Object[1];
    paramValues[0] = sharedLoader;
    Method method =
        startupInstance.getClass().getMethod(methodName, paramTypes);
    method.invoke(startupInstance, paramValues);

    catalinaDaemon = startupInstance;

}
    
```

通过上面几行关键代码的注释，我们就可以看出Catalina是如何初始化的。这里还留下一个问题，tomcat为什么要初始化不同的classloader呢？我们将在下文进行详解。