## Tomcat8.5.X源码分析

#### 源码分析主线

![img.png](ethen/imgs/tomcat-source-roadmap.png)

#### 源码目录结构

- org.apache.catalina
  <br>包含所有的Servlet容器实现，以及涉及到安全、会话、集群、部署管理Servlet容器的各个方面，同时还包含的启动入口。
- org.apache.coyote
  <br>是Tomcat连接器框架的名称，Tomcat服务器提供的客户端访问外部接口，客户端通过Coyote于服务器建立连接、发送请求并接受响应。
- org.apache.el
  <br> 提供java表达式语言支持。
- org.apache.jasper
  <br>提供JSP引擎支持。
- naming
  <br>提供JNDI服务。
- tomcat
  <br>提供外部调用的API。

#### Tomcat工程启动VM参数

```properties
-Dcatalina.home=catalina-home
-Dcatalina.base=catalina-home
-Djava.endorsed.dirs=catalina-home/endorsed
-Djava.io.tmpdir=catalina-home/temp
-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
-Djava.util.logging.config.file=catalina-home/conf/logging.properties
```
