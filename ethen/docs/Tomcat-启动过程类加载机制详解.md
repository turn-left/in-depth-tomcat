#  Tomcat - 启动过程:类加载机制详解

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-classloader.html

## Tomcat初始化了哪些classloader

在Bootstrap中我们可以看到有如下三个classloader

```java
ClassLoader commonLoader = null;
ClassLoader catalinaLoader = null;
ClassLoader sharedLoader = null;    
```

### [¶](#如何初始化的呢) 如何初始化的呢？

```java
private void initClassLoaders() {
    try {
        // commonLoader初始化
        commonLoader = createClassLoader("common", null);
        if (commonLoader == null) {
            // no config file, default to this loader - we might be in a 'single' env.
            commonLoader = this.getClass().getClassLoader();
        }
        // catalinaLoader初始化, 父classloader是commonLoader
        catalinaLoader = createClassLoader("server", commonLoader);
        // sharedLoader初始化
        sharedLoader = createClassLoader("shared", commonLoader);
    } catch (Throwable t) {
        handleThrowable(t);
        log.error("Class loader creation threw exception", t);
        System.exit(1);
    }
}    
```

> 可以看出，catalinaLoader 和 sharedLoader 的 parentClassLoader 是 commonLoader。

### [¶](#如何创建classloader的) 如何创建classLoader的？

不妨再看下如何创建的？

```java
private ClassLoader createClassLoader(String name, ClassLoader parent)
    throws Exception {

    String value = CatalinaProperties.getProperty(name + ".loader");
    if ((value == null) || (value.equals("")))
        return parent;

    value = replace(value);

    List<Repository> repositories = new ArrayList<>();

    String[] repositoryPaths = getPaths(value);

    for (String repository : repositoryPaths) {
        // Check for a JAR URL repository
        try {
            @SuppressWarnings("unused")
            URL url = new URL(repository);
            repositories.add(new Repository(repository, RepositoryType.URL));
            continue;
        } catch (MalformedURLException e) {
            // Ignore
        }

        // Local repository
        if (repository.endsWith("*.jar")) {
            repository = repository.substring
                (0, repository.length() - "*.jar".length());
            repositories.add(new Repository(repository, RepositoryType.GLOB));
        } else if (repository.endsWith(".jar")) {
            repositories.add(new Repository(repository, RepositoryType.JAR));
        } else {
            repositories.add(new Repository(repository, RepositoryType.DIR));
        }
    }

    return ClassLoaderFactory.createClassLoader(repositories, parent);
}
```

方法的逻辑也比较简单就是从 catalina.property文件里找 common.loader, shared.loader, server.loader 对应的值，然后构造成Repository 列表，再将Repository 列表传入ClassLoaderFactory.createClassLoader 方法，ClassLoaderFactory.createClassLoader 返回的是 URLClassLoader，而Repository 列表就是这个URLClassLoader 可以加在的类的路径。 在catalina.property文件里

```bash
common.loader="${catalina.base}/lib","${catalina.base}/lib/*.jar","${catalina.home}/lib","${catalina.home}/lib/*.jar"
server.loader=
shared.loader=    
```

其中 shared.loader, server.loader 是没有值的，createClassLoader 方法里如果没有值的话，就返回传入的 parent ClassLoader，也就是说，commonLoader,catalinaLoader,sharedLoader 其实是一个对象。在Tomcat之前的版本里，这三个是不同的URLClassLoader对象。

```java
Class<?> startupClass = catalinaLoader.loadClass("org.apache.catalina.startup.Catalina");
        Object startupInstance = startupClass.getConstructor().newInstance();   
```

初始化完三个ClassLoader对象后，init() 方法就使用 catalinaClassLoader 加载了org.apache.catalina.startup.Catalina 类，并创建了一个对象，然后通过反射调用这个对象的 setParentClassLoader 方法，传入的参数是 sharedClassLoader。最后吧这个 Catania 对象复制给 catalinaDaemon 属性。

## [¶](#深入理解) 深入理解

可以复习下类加载机制的基础：[JVM基础 - Java 类加载机制]()

### [¶](#什么是类加载机制) 什么是类加载机制

Java是一门面向对象的语言，而对象又必然依托于类。类要运行，必须首先被加载到内存。我们可以简单地把类分为几类：

- Java自带的核心类
- Java支持的可扩展类
- 我们自己编写的类
- **为什么要设计多个类加载器**？

> 如果所有的类都使用一个类加载器来加载，会出现什么问题呢？

假如我们自己编写一个类`java.util.Object`，它的实现可能有一定的危险性或者隐藏的bug。而我们知道Java自带的核心类里面也有`java.util.Object`，如果JVM启动的时候先行加载的是我们自己编写的`java.util.Object`，那么就有可能出现安全问题！

所以，Sun（后被Oracle收购）采用了另外一种方式来保证最基本的、也是最核心的功能不会被破坏。你猜的没错，那就是双亲委派模式！

- **什么是双亲委派模型**？

> 双亲委派模型解决了类错乱加载的问题，也设计得非常精妙。

双亲委派模式对类加载器定义了层级，每个类加载器都有一个父类加载器。在一个类需要加载的时候，首先委派给父类加载器来加载，而父类加载器又委派给祖父类加载器来加载，以此类推。如果父类及上面的类加载器都加载不了，那么由当前类加载器来加载，并将被加载的类缓存起来。

![img](/ethen/imgs/tomcat/java_jvm_classload_3.png)

所以上述类是这么加载的

- Java自带的核心类 -- 由启动类加载器加载
- Java支持的可扩展类 -- 由扩展类加载器加载
- 我们自己编写的类 -- 默认由应用程序类加载器或其子类加载

> 但它也不是万能的，在有些场景也会遇到它解决不了的问题，比如如下场景。

### [¶](#双亲委派模型问题是如何解决的) 双亲委派模型问题是如何解决的？

> 在Java核心类里面有SPI（Service Provider Interface），它由Sun编写规范，第三方来负责实现。SPI需要用到第三方实现类。如果使用双亲委派模型，那么第三方实现类也需要放在Java核心类里面才可以，不然的话第三方实现类将不能被加载使用。但是这显然是不合理的！怎么办呢？

**ContextClassLoader**（上下文类加载器）就来解围了。

在java.lang.Thread里面有两个方法，get/set上下文类加载器

```java
public void setContextClassLoader(ClassLoader cl)
public ClassLoader getContextClassLoader()
    
```

我们可以通过在SPI类里面调用getContextClassLoader来获取第三方实现类的类加载器。由第三方实现类通过调用setContextClassLoader来传入自己实现的类加载器, 这样就变相地解决了双亲委派模式遇到的问题。

### [¶](#为什么tomcat的类加载器也不是双亲委派模型) 为什么Tomcat的类加载器也不是双亲委派模型

> 我们知道，Java默认的类加载机制是通过双亲委派模型来实现的，而Tomcat实现的方式又和双亲委派模型有所区别。

**原因在于一个Tomcat容器允许同时运行多个Web程序，每个Web程序依赖的类又必须是相互隔离的**。因此，如果Tomcat使用双亲委派模式来加载类的话，将导致Web程序依赖的类变为共享的。

举个例子，假如我们有两个Web程序，一个依赖A库的1.0版本，另一个依赖A库的2.0版本，他们都使用了类xxx.xx.Clazz，其实现的逻辑因类库版本的不同而结构完全不同。那么这两个Web程序的其中一个必然因为加载的Clazz不是所使用的Clazz而出现问题！而这对于开发来说是非常致命的！

### [¶](#tomcat类加载机制是怎么样的呢) Tomcat类加载机制是怎么样的呢

> 既然Tomcat的类加载机器不同于双亲委派模式，那么它又是一种怎样的模式呢？

我们在这里一定要看下官网提供的[类加载的文档  (opens new window)](https://tomcat.apache.org/tomcat-9.0-doc/class-loader-howto.html)

![img](/ethen/imgs/tomcat/tomcat-x-classloader-1.png)

结合经典的类加载机制，我们完整的看下Tomcat类加载图

![img](/ethen/imgs/tomcat/tomcat-x-classloader-2.png)

我们在这张图中看到很多类加载器，除了Jdk自带的类加载器，我们尤其关心Tomcat自身持有的类加载器。仔细一点我们很容易发现：Catalina类加载器和Shared类加载器，他们并不是父子关系，而是兄弟关系。为啥这样设计，我们得分析一下每个类加载器的用途，才能知晓。

- Common类加载器

  ，负责加载Tomcat和Web应用都复用的类

  - **Catalina类加载器**，负责加载Tomcat专用的类，而这些被加载的类在Web应用中将不可见

  - Shared类加载器

    ，负责加载Tomcat下所有的Web应用程序都复用的类，而这些被加载的类在Tomcat中将不可见

    - **WebApp类加载器**，负责加载具体的某个Web应用程序所使用到的类，而这些被加载的类在Tomcat和其他的Web应用程序都将不可见
    - **Jsp类加载器**，每个jsp页面一个类加载器，不同的jsp页面有不同的类加载器，方便实现jsp页面的热插拔

同样的，我们可以看到通过**ContextClassLoader**（上下文类加载器）的**setContextClassLoader**来传入自己实现的类加载器

```java
public void init() throws Exception {

  initClassLoaders();

  // 看这里
  Thread.currentThread().setContextClassLoader(catalinaLoader);

  SecurityClassLoad.securityClassLoad(catalinaLoader);
...    
```

### [¶](#webapp类加载器) WebApp类加载器

> 到这儿，我们隐隐感觉到少分析了点什么！没错，就是WebApp类加载器。整个启动过程分析下来，我们仍然没有看到这个类加载器。它又是在哪儿出现的呢？

我们知道WebApp类加载器是Web应用私有的，而每个Web应用其实算是一个Context，那么我们通过Context的实现类应该可以发现。在Tomcat中，Context的默认实现为StandardContext，我们看看这个类的startInternal()方法，在这儿我们发现了我们感兴趣的WebApp类加载器。

```java
protected synchronized void startInternal() throws LifecycleException {
    if (getLoader() == null) {
        WebappLoader webappLoader = new WebappLoader(getParentClassLoader());
        webappLoader.setDelegate(getDelegate());
        setLoader(webappLoader);
    }
}
    
```



入口代码非常简单，就是webappLoader不存在的时候创建一个，并调用setLoader方法。我们接着分析setLoader

```java
public void setLoader(Loader loader) {

    Lock writeLock = loaderLock.writeLock();
    writeLock.lock();
    Loader oldLoader = null;
    try {
        // Change components if necessary
        oldLoader = this.loader;
        if (oldLoader == loader)
            return;
        this.loader = loader;

        // Stop the old component if necessary
        if (getState().isAvailable() && (oldLoader != null) &&
            (oldLoader instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldLoader).stop();
            } catch (LifecycleException e) {
                log.error("StandardContext.setLoader: stop: ", e);
            }
        }

        // Start the new component if necessary
        if (loader != null)
            loader.setContext(this);
        if (getState().isAvailable() && (loader != null) &&
            (loader instanceof Lifecycle)) {
            try {
                ((Lifecycle) loader).start();
            } catch (LifecycleException e) {
                log.error("StandardContext.setLoader: start: ", e);
            }
        }
    } finally {
        writeLock.unlock();
    }

    // Report this property change to interested listeners
    support.firePropertyChange("loader", oldLoader, loader);
}
    
```



这儿，我们感兴趣的就两行代码：

```java
((Lifecycle) oldLoader).stop(); // 旧的加载器停止
((Lifecycle) loader).start(); // 新的加载器启动
     
```

### [¶](#参考文章) 参考文章

- https://tomcat.apache.org/tomcat-9.0-doc/class-loader-howto.html
- juconcurrent https://www.jianshu.com/p/51b2c50c58eb