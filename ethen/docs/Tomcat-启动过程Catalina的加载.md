#  Tomcat - 启动过程:Catalina的加载

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-catalina.html

## Catalina的引入

> 通过前两篇文章，我们知道了Tomcat的类加载机制和整体的组件加载流程；我们也知道通过Bootstrap初始化的catalinaClassLoader加载了Catalina，那么进而引入了一个问题就是Catalina是如何加载的呢？加载了什么呢？

- 先回顾下整个流程，和我们分析的阶段

![img](/ethen/imgs/tomcat/tomcat-x-catalina-1.png)

- 看下Bootstrap中Load的过程

```java
/**
  * 加载守护进程
  */
private void load(String[] arguments) throws Exception {

    // Call the load() method
    String methodName = "load";
    Object param[];
    Class<?> paramTypes[];
    if (arguments==null || arguments.length==0) {
        paramTypes = null;
        param = null;
    } else {
        paramTypes = new Class[1];
        paramTypes[0] = arguments.getClass();
        param = new Object[1];
        param[0] = arguments;
    }
    Method method =
        catalinaDaemon.getClass().getMethod(methodName, paramTypes); 
    if (log.isDebugEnabled()) {
        log.debug("Calling startup class " + method);
    }
    method.invoke(catalinaDaemon, param);// 本质上就是调用catalina的load方法
}
    
```

## [¶](#catalina的加载) Catalina的加载

上一步，我们知道catalina load的触发，因为有参数所以是load(String[])方法。我们进而看下这个load方法做了什么？

- load(String[])本质上还是调用了load方法

```java
/*
  * Load using arguments
  */
public void load(String args[]) {

    try {
        if (arguments(args)) { // 处理命令行的参数
            load();
        }
    } catch (Exception e) {
        e.printStackTrace(System.out);
    }
}
  
```



- load加载过程本质上是初始化Server的实例

```java
/**
  * Start a new server instance.
  */
public void load() {

    // 如果已经加载则退出
    if (loaded) {
        return;
    }
    loaded = true;

    long t1 = System.nanoTime();

    // （已经弃用）
    initDirs();

    // Before digester - it may be needed
    initNaming();

    // 解析 server.xml
    parseServerXml(true);
    Server s = getServer();
    if (s == null) {
        return;
    }

    getServer().setCatalina(this);
    getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
    getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

    // Stream redirection
    initStreams();

    // 启动Server
    try {
        getServer().init();
    } catch (LifecycleException e) {
        if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE")) {
            throw new java.lang.Error(e);
        } else {
            log.error(sm.getString("catalina.initError"), e);
        }
    }

    if(log.isInfoEnabled()) {
        log.info(sm.getString("catalina.init", Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1))));
    }
}
    
```

总体流程如下：

![img](/ethen/imgs/tomcat/tomcat-x-catalina-2.png)

### [¶](#initdirs) initDirs

已经弃用了，Tomcat10会删除这个方法。

```java
/**
  * @deprecated unused. Will be removed in Tomcat 10 onwards.
  */
@Deprecated
protected void initDirs() {
}   
```

### [¶](#initnaming) initNaming

设置额外的系统变量

```java
protected void initNaming() {
  // Setting additional variables
  if (!useNaming) {
      log.info(sm.getString("catalina.noNaming"));
      System.setProperty("catalina.useNaming", "false");
  } else {
      System.setProperty("catalina.useNaming", "true");
      String value = "org.apache.naming";
      String oldValue =
          System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
      if (oldValue != null) {
          value = value + ":" + oldValue;
      }
      System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
      if( log.isDebugEnabled() ) {
          log.debug("Setting naming prefix=" + value);
      }
      value = System.getProperty
          (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
      if (value == null) {
          System.setProperty
              (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                "org.apache.naming.java.javaURLContextFactory");
      } else {
          log.debug("INITIAL_CONTEXT_FACTORY already set " + value );
      }
  }
}
    
```

### [¶](#serverxml的解析) Server.xml的解析

分三大块，下面的代码还是很清晰的:

```java
protected void parseServerXml(boolean start) {
    // Set configuration source
    ConfigFileLoader.setSource(new CatalinaBaseConfigurationSource(Bootstrap.getCatalinaBaseFile(), getConfigFile()));
    File file = configFile();

    if (useGeneratedCode && !Digester.isGeneratedCodeLoaderSet()) {
        // Load loader
        String loaderClassName = generatedCodePackage + ".DigesterGeneratedCodeLoader";
        try {
            Digester.GeneratedCodeLoader loader =
                    (Digester.GeneratedCodeLoader) Catalina.class.getClassLoader().loadClass(loaderClassName).newInstance();
            Digester.setGeneratedCodeLoader(loader);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.info(sm.getString("catalina.noLoader", loaderClassName), e);
            } else {
                log.info(sm.getString("catalina.noLoader", loaderClassName));
            }
            // No loader so don't use generated code
            useGeneratedCode = false;
        }
    }

    // 初始化server.xml的位置
    File serverXmlLocation = null;
    String xmlClassName = null;
    if (generateCode || useGeneratedCode) {
        xmlClassName = start ? generatedCodePackage + ".ServerXml" : generatedCodePackage + ".ServerXmlStop";
    }
    if (generateCode) {
        if (generatedCodeLocationParameter != null) {
            generatedCodeLocation = new File(generatedCodeLocationParameter);
            if (!generatedCodeLocation.isAbsolute()) {
                generatedCodeLocation = new File(Bootstrap.getCatalinaHomeFile(), generatedCodeLocationParameter);
            }
        } else {
            generatedCodeLocation = new File(Bootstrap.getCatalinaHomeFile(), "work");
        }
        serverXmlLocation = new File(generatedCodeLocation, generatedCodePackage);
        if (!serverXmlLocation.isDirectory() && !serverXmlLocation.mkdirs()) {
            log.warn(sm.getString("catalina.generatedCodeLocationError", generatedCodeLocation.getAbsolutePath()));
            // Disable code generation
            generateCode = false;
        }
    }

    // 用 SAXParser 来解析 xml，解析完了之后，xml 里定义的各种标签就有对应的实现类对象了
    ServerXml serverXml = null;
    if (useGeneratedCode) {
        serverXml = (ServerXml) Digester.loadGeneratedClass(xmlClassName);
    }

    if (serverXml != null) {
        serverXml.load(this);
    } else {
        try (ConfigurationSource.Resource resource = ConfigFileLoader.getSource().getServerXml()) {
            // Create and execute our Digester
            Digester digester = start ? createStartDigester() : createStopDigester();
            InputStream inputStream = resource.getInputStream();
            InputSource inputSource = new InputSource(resource.getURI().toURL().toString());
            inputSource.setByteStream(inputStream);
            digester.push(this);
            if (generateCode) {
                digester.startGeneratingCode();
                generateClassHeader(digester, start);
            }
            digester.parse(inputSource);
            if (generateCode) {
                generateClassFooter(digester);
                try (FileWriter writer = new FileWriter(new File(serverXmlLocation,
                        start ? "ServerXml.java" : "ServerXmlStop.java"))) {
                    writer.write(digester.getGeneratedCode().toString());
                }
                digester.endGeneratingCode();
                Digester.addGeneratedClass(xmlClassName);
            }
        } catch (Exception e) {
            log.warn(sm.getString("catalina.configFail", file.getAbsolutePath()), e);
            if (file.exists() && !file.canRead()) {
                log.warn(sm.getString("catalina.incorrectPermissions"));
            }
        }
    }
}
  
        
    
```

### [¶](#initstreams) initStreams

替换掉System.out, System.err为自定义的PrintStream

```java
protected void initStreams() {
    // Replace System.out and System.err with a custom PrintStream
    System.setOut(new SystemLogHandler(System.out));
    System.setErr(new SystemLogHandler(System.err));
}   
```

## [¶](#catalina-的启动) Catalina 的启动

在 load 方法之后，Tomcat 就初始化了一系列的组件，接着就可以调用 start 方法进行启动了。

```java
/**
  * Start a new server instance.
  */
public void start() {

    if (getServer() == null) {
        load();
    }

    if (getServer() == null) {
        log.fatal(sm.getString("catalina.noServer"));
        return;
    }

    long t1 = System.nanoTime();

    // Start the new server
    try {
        getServer().start();
    } catch (LifecycleException e) {
        log.fatal(sm.getString("catalina.serverStartFail"), e);
        try {
            getServer().destroy();
        } catch (LifecycleException e1) {
            log.debug("destroy() failed for failed Server ", e1);
        }
        return;
    }

    long t2 = System.nanoTime();
    if(log.isInfoEnabled()) {
        log.info(sm.getString("catalina.startup", Long.valueOf((t2 - t1) / 1000000)));
    }

    // Register shutdown hook
    if (useShutdownHook) {
        if (shutdownHook == null) {
            shutdownHook = new CatalinaShutdownHook();
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // If JULI is being used, disable JULI's shutdown hook since
        // shutdown hooks run in parallel and log messages may be lost
        // if JULI's hook completes before the CatalinaShutdownHook()
        LogManager logManager = LogManager.getLogManager();
        if (logManager instanceof ClassLoaderLogManager) {
            ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                    false);
        }
    }

    if (await) {
        await();
        stop();
    }
}
      
```

上面这段代码，逻辑非常简单，首先确定 getServer() 方法不为 null ，也就是确定 server 属性不为null，而 server 属性是在 load 方法就初始化了。

整段代码的核心就是 try-catch 里的 getServer().start() 方法了，也就是调用 Server 对象的 start() 方法来启动 Tomcat。本篇文章就先不对 Server 的 start() 方法进行解析了，下篇文章会单独讲。

## [¶](#catalina-的关闭) Catalina 的关闭

调用完 Server#start 方法之后，注册了一个ShutDownHook，也就是 CatalinaShutdownHook 对象，

```java
/**
  * Shutdown hook which will perform a clean shutdown of Catalina if needed.
  */
protected class CatalinaShutdownHook extends Thread {

  @Override
  public void run() {
      try {
          if (getServer() != null) {
              Catalina.this.stop();
          }
      } catch (Throwable ex) {
          ExceptionUtils.handleThrowable(ex);
          log.error(sm.getString("catalina.shutdownHookFail"), ex);
      } finally {
          // If JULI is used, shut JULI down *after* the server shuts down
          // so log messages aren't lost
          LogManager logManager = LogManager.getLogManager();
          if (logManager instanceof ClassLoaderLogManager) {
              ((ClassLoaderLogManager) logManager).shutdown();
          }
      }
  }
}
      
```

CatalinaShutdownHook 的逻辑也简单，就是调用 Catalina 对象的 stop 方法来停止 tomcat。

最后就进入 if 语句了，await 是在 Bootstrap 里调用的时候设置为 true 的，也就是本文开头的时候提到的三个方法中的一个。await 方法的作用是停住主线程，等待用户输入shutdown 命令之后，停止等待，之后 main 线程就调用 stop 方法来停止Tomcat。

```java
/**
  * Stop an existing server instance.
  */
public void stop() {

    try {
        // Remove the ShutdownHook first so that server.stop()
        // doesn't get invoked twice
        if (useShutdownHook) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

            // If JULI is being used, re-enable JULI's shutdown to ensure
            // log messages are not lost
            LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof ClassLoaderLogManager) {
                ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                        true);
            }
        }
    } catch (Throwable t) {
        ExceptionUtils.handleThrowable(t);
        // This will fail on JDK 1.2. Ignoring, as Tomcat can run
        // fine without the shutdown hook.
    }

    // Shut down the server
    try {
        Server s = getServer();
        LifecycleState state = s.getState();
        if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0
                && LifecycleState.DESTROYED.compareTo(state) >= 0) {
            // Nothing to do. stop() was already called
        } else {
            s.stop();
            s.destroy();
        }
    } catch (LifecycleException e) {
        log.error(sm.getString("catalina.stopError"), e);
    }

}    
```

Catalina 的 stop 方法主要逻辑是调用 Server 对象的 stop 方法。

## [¶](#聊聊关闭钩子) 聊聊关闭钩子

上面我们看到CatalinaShutdownHook, 这里有必要谈谈JVM的关闭钩子。

```java
if (shutdownHook == null) {
    shutdownHook = new CatalinaShutdownHook();
}
Runtime.getRuntime().addShutdownHook(shutdownHook);   
```

关闭钩子是指通过**Runtime.addShutdownHook注册的但尚未开始的线程**。这些钩子可以用于**实现服务或者应用程序的清理工作**，例如删除临时文件，或者清除无法由操作系统自动清除的资源。

JVM既可以正常关闭，也可以强行关闭。正常关闭的触发方式有多种，包括：当最后一个“正常（非守护）”线程结束时，或者当调用了System.exit时，或者通过其他特定于平台的方法关闭时（例如发送了SIGINT信号或者键入Ctrl-C）。

在**正常关闭中，JVM首先调用所有已注册的关闭钩子**。JVM并不能保证关闭钩子的调用顺序。在关闭应用程序线程时，如果有（守护或者非守护）线程仍然在执行，那么这些线程接下来将与关闭进程并发执行。当所有的关闭钩子都执行结束时，如果runFinalizersOnExit为true【通过Runtime.runFinalizersOnExit(true)设置】，那么JVM将运行这些Finalizer（对象重写的finalize方法），然后再停止。JVM不会停止或中断任何在关闭时仍然运行的应用程序线程。当JVM最终结束时，这些线程将被强行结束。如果关闭钩子或者Finalizer没有执行完成，那么正常关闭进程“挂起”并且JVM必须被强行关闭。当**JVM被强行关闭时，只是关闭JVM，并不会运行关闭钩子**（举个例子，类似于电源都直接拔了，还怎么做其它动作呢？）。

下面是一个简单的示例：

```java
public class T {
	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws Exception {
		//启用退出JVM时执行Finalizer
		Runtime.runFinalizersOnExit(true);
		MyHook hook1 = new MyHook("Hook1");
		MyHook hook2 = new MyHook("Hook2");
		MyHook hook3 = new MyHook("Hook3");
		
		//注册关闭钩子
		Runtime.getRuntime().addShutdownHook(hook1);
		Runtime.getRuntime().addShutdownHook(hook2);
		Runtime.getRuntime().addShutdownHook(hook3);
		
		//移除关闭钩子
		Runtime.getRuntime().removeShutdownHook(hook3);
		
		//Main线程将在执行这句之后退出
		System.out.println("Main Thread Ends.");
	}
}

class MyHook extends Thread {
	private String name;
	public MyHook (String name) {
		this.name = name;
		setName(name);
	}
	public void run() {
		System.out.println(name + " Ends.");
	}
	//重写Finalizer，将在关闭钩子后调用
	protected void finalize() throws Throwable {
		System.out.println(name + " Finalize.");
	}
}
    
```

和（可能的）执行结果（因为JVM不保证关闭钩子的调用顺序，因此结果中的第二、三行可能出现相反的顺序）：

```bash
Main Thread Ends.
Hook2 Ends.
Hook1 Ends.
Hook3 Finalize.
Hook2 Finalize.
Hook1 Finalize.   
```

可以看到，main函数执行完成，首先输出的是Main Thread Ends，接下来执行关闭钩子，输出Hook2 Ends和Hook1 Ends。这两行也可以证实：JVM确实不是以注册的顺序来调用关闭钩子的。而由于hook3在调用了addShutdownHook后，接着对其调用了removeShutdownHook将其移除，于是hook3在JVM退出时没有执行，因此没有输出Hook3 Ends。

另外，由于MyHook类实现了finalize方法，而main函数中第一行又通过Runtime.runFinalizersOnExit(true)打开了退出JVM时执行Finalizer的开关，于是3个hook对象的finalize方法被调用，输出了3行Finalize。

注意，多次调用addShutdownHook来注册同一个关闭钩子将会抛出IllegalArgumentException:

```bash
Exception in thread "main" java.lang.IllegalArgumentException: Hook previously registered
	at java.lang.ApplicationShutdownHooks.add(ApplicationShutdownHooks.java:72)
	at java.lang.Runtime.addShutdownHook(Runtime.java:211)
	at T.main(T.java:12)
    
```

另外，从JavaDoc中得知：**一旦JVM关闭流程开始，就只能通过调用halt方法来停止该流程，也不可能再注册或移除关闭钩子了，这些操作将导致抛出IllegalStateException**。

如果在关闭钩子中关闭应用程序的公共的组件，如日志服务，或者数据库连接等，像下面这样：

```java
Runtime.getRuntime().addShutdownHook(new Thread() {
	public void run() {
		try { 
			LogService.this.stop();
		} catch (InterruptedException ignored){
			//ignored
		}
	}
});
    
```



由于**关闭钩子将并发执行，因此在关闭日志时可能导致其他需要日志服务的关闭钩子产生问题**。**为了避免这种情况，可以使关闭钩子不依赖那些可能被应用程序或其他关闭钩子关闭的服务**。实现这种功能的一种方式是对所有服务使用同一个关闭钩子（而不是每个服务使用一个不同的关闭钩子），并且在该关闭钩子中执行一系列的关闭操作。这确保了关闭操作在单个线程中串行执行，从而避免了在关闭操作之前出现竞态条件或死锁等问题。

### [¶](#使用场景) 使用场景

通过Hook实现临时文件清理

```java
public class test {

  public static void main(String[] args) {
      try {
          Thread.sleep(20000);
      } catch (InterruptedException e) {
          e.printStackTrace();
      }

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
          public void run() {
              System.out.println("auto clean temporary file");
          }
      }));
  }
}
    
```

## [¶](#小结) 小结

Catalina 类承接了 Bootstrap 类的 load 和 start 方法，然后根据配置初始化了 Tomcat 的组件，并调用了 Server 类的 init 和 start 方法来启动 Tomcat。

## [¶](#参考文章) 参考文章

- https://segmentfault.com/a/1190000022012525
- https://my.oschina.net/itblog/blog/811053