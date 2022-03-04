# Tomcat - 如何设计一个简单的web容器

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-design-web-container.html

## 写在前面

我们在学习一项技术时，需要学习是它的知识体系，而不是碎片化的知识点。在构建知识体系时，我们往往需要先全局的看完一个教程或者一本书，这是构建的基础。这里我推荐大家看两本书：

![img](/ethen/imgs/tomcat/tomcat-x-design-7.png)

特别是第一本：经典的《How Tomcat Works》的中文版，它从0基础逐步构建出Tomcat，适合新手；本节中很多内容源自这本书。

本系列在本之后，将转为直接分析Tomcat框架。

## [¶](#基础认知：如何实现服务器和客户端浏览器的交互) 基础认知：如何实现服务器和客户端（浏览器）的交互

> 客户端和服务器端之间的交互式通过Socket来实现的，它术语应用层的协议。

### [¶](#http协议) HTTP协议

http协议相关的内容可以参看这里：[网络协议 - HTTP 协议详解]()

### [¶](#socket) Socket

Socket是网络连接的一个端点。套接字使得一个应用可以从网络中读取和写入数据。放在两 个不同计算机上的两个应用可以通过连接发送和接受字节流。为了从你的应用发送一条信息到另 一个应用，你需要知道另一个应用的 IP 地址和套接字端口。在 Java 里边，套接字指的是`java.net.Socket`类。

要创建一个套接字，你可以使用 Socket 类众多构造方法中的一个。其中一个接收主机名称 和端口号:

```java
public Socket (java.lang.String host, int port)
  
        @pdai: 代码已经复制到剪贴板
    
```

1

在这里主机是指远程机器名称或者 IP 地址，端口是指远程应用的端口号。例如，要连接 yahoo.com 的 80 端口，你需要构造以下的 Socket 对象:

```java
new Socket ("yahoo.com", 80);
  
        @pdai: 代码已经复制到剪贴板
    
```

1

一旦你成功创建了一个 Socket 类的实例，你可以使用它来发送和接受字节流。要发送字节 流，你首先必须调用Socket类的getOutputStream方法来获取一个`java.io.OutputStream`对象。 要 发 送 文 本 到 一 个 远 程 应 用 ， 你 经 常 要 从 返 回 的 OutputStream 对 象 中 构 造 一 个 `java.io.PrintWriter` 对象。要从连接的另一端接受字节流，你可以调用 Socket 类的 getInputStream 方法用来返回一个 `java.io.InputStream` 对象。

![img](/ethen/imgs/tomcat/tomcat-x-design-6.png)

### [¶](#seversocket) SeverSocket

Socket 类代表一个**客户端套接字**，即任何时候你想连接到一个远程服务器应用的时候你构造的套接字，现在，假如你想实施一个服务器应用，例如一个 HTTP 服务器或者 FTP 服务器，你需要一种不同的做法。这是因为你的服务器必须随时待命，因为它不知道一个客户端应用什么时候会尝试去连接它。为了让你的应用能随时待命，你需要使用 `java.net.ServerSocket` 类。这是 **服务器套接字**的实现。

`ServerSocket` 和 `Socket` 不同，服务器套接字的角色是等待来自客户端的连接请求。**一旦服务器套接字获得一个连接请求，它创建一个 Socket 实例来与客户端进行通信**。

要创建一个服务器套接字，你需要使用 ServerSocket 类提供的四个构造方法中的一个。你 需要指定 IP 地址和服务器套接字将要进行监听的端口号。通常，IP 地址将会是 127.0.0.1，也 就是说，服务器套接字将会监听本地机器。服务器套接字正在监听的 IP 地址被称为是绑定地址。 服务器套接字的另一个重要的属性是 backlog，这是服务器套接字开始拒绝传入的请求之前，传 入的连接请求的最大队列长度。

其中一个 ServerSocket 类的构造方法如下所示:

```java
public ServerSocket(int port, int backLog, InetAddress bindingAddress);
  
        @pdai: 代码已经复制到剪贴板
    
```

1

## [¶](#一个简单web容器的设计和实现：对静态资源) 一个简单web容器的设计和实现：对静态资源

> 准备，这个例子来源于《How Tomcat Works》, 可以从这里下载源码

注意：当你跑如下程序时，可能会由于浏览器新版本不再支持的HTTP 0.9协议，而造成浏览器页面没有返回信息。

### [¶](#组件设计) 组件设计

根据上述的基础，我们可以看到，我们只需要提供三个最基本的类，分别是：

- **Request** - 表示请求，这里表示浏览器发起的HTTP请求
- **HttpServer** - 表示处理请求的服务器，同时这里使用我们上面铺垫的ServerSocket
- **Reponse** - 表示处理请求后的响应， 这里表示服务器对HTTP请求的响应结果

![img](/ethen/imgs/tomcat/tomcat-x-design-1.png)

### [¶](#组件实现) 组件实现

从上图中我们可以看到，组织这几个类的入口在Server的启动方法中，即main方法中, 所以我们透过main方法从Server类进行分析：

- Server是如何启动的？

```java
public class HttpServer {

  // 存放静态资源的位置
  public static final String WEB_ROOT =
    System.getProperty("user.dir") + File.separator  + "webroot";

  // 关闭Server的请求
  private static final String SHUTDOWN_COMMAND = "/SHUTDOWN";

  // 是否关闭Server
  private boolean shutdown = false;

  // 主入口
  public static void main(String[] args) {
    HttpServer server = new HttpServer();
    server.await();
  }

  public void await() {
    // 启动ServerSocket
    ServerSocket serverSocket = null;
    int port = 8080;
    try {
      serverSocket =  new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    // 循环等待一个Request请求
    while (!shutdown) {
      Socket socket = null;
      InputStream input = null;
      OutputStream output = null;
      try {
        // 创建socket
        socket = serverSocket.accept();
        input = socket.getInputStream();
        output = socket.getOutputStream();

        // 封装input至request, 并处理请求
        Request request = new Request(input);
        request.parse();

        // 封装output至response
        Response response = new Response(output);
        response.setRequest(request);
        response.sendStaticResource();

        // 关闭socket
        socket.close();

        // 如果接受的是关闭请求，则设置关闭监听request的标志
        shutdown = request.getUri().equals(SHUTDOWN_COMMAND);
      }
      catch (Exception e) {
        e.printStackTrace();
        continue;
      }
    }
  }
}   
```

- Request请求是如何封装和处理的？

```java
public class Request {

  private InputStream input;
  private String uri;

  // 初始化Request
  public Request(InputStream input) {
    this.input = input;
  }

  // 处理request的方法
  public void parse() {
    // 从socket中读取字符
    StringBuffer request = new StringBuffer(2048);
    int i;
    byte[] buffer = new byte[2048];
    try {
      i = input.read(buffer);
    }
    catch (IOException e) {
      e.printStackTrace();
      i = -1;
    }
    for (int j=0; j<i; j++) {
      request.append((char) buffer[j]);
    }
    System.out.print(request.toString());

    // 获得两个空格之间的内容, 这里将是HttpServer.WEB_ROOT中静态文件的文件名称
    uri = parseUri(request.toString());
  }

  private String parseUri(String requestString) {
    int index1, index2;
    index1 = requestString.indexOf(' ');
    if (index1 != -1) {
      index2 = requestString.indexOf(' ', index1 + 1);
      if (index2 > index1)
        return requestString.substring(index1 + 1, index2);
    }
    return null;
  }

  public String getUri() {
    return uri;
  }

}    
```

- Response中响应了什么？

```java
public class Response {

  private static final int BUFFER_SIZE = 1024;
  Request request;
  OutputStream output;

  public Response(OutputStream output) {
    this.output = output;
  }

  // response中封装了request，以便获取request中的请求参数
  public void setRequest(Request request) {
    this.request = request;
  }

  public void sendStaticResource() throws IOException {
    byte[] bytes = new byte[BUFFER_SIZE];
    FileInputStream fis = null;
    try {
      // 读取文件内容
      File file = new File(HttpServer.WEB_ROOT, request.getUri());
      if (file.exists()) {
        fis = new FileInputStream(file);
        int ch = fis.read(bytes, 0, BUFFER_SIZE);
        while (ch!=-1) {
          output.write(bytes, 0, ch);
          ch = fis.read(bytes, 0, BUFFER_SIZE);
        }
      }
      else {
        // 文件不存在时，输出404信息
        String errorMessage = "HTTP/1.1 404 File Not Found\r\n" +
          "Content-Type: text/html\r\n" +
          "Content-Length: 23\r\n" +
          "\r\n" +
          "<h1>File Not Found</h1>";
        output.write(errorMessage.getBytes());
      }
    }
    catch (Exception e) {
      // thrown if cannot instantiate a File object
      System.out.println(e.toString() );
    }
    finally {
      if (fis!=null)
        fis.close();
    }
  }
}    
```

- 启动输出

当我们run上面HttpServer中的main方法之后，我们就可以打开浏览器http://localhost:8080, 后面添加参数看返回webroot目录中静态文件的内容了(比如这里我加了hello.txt文件到webroot下，并访问http://localhost:8080/hello.txt)。

![img](/ethen/imgs/tomcat/tomcat-x-design-5.png)

![img](/ethen/imgs/tomcat/tomcat-x-design-4.png)

## [¶](#一个简单web容器的设计和实现：对servelet) 一个简单web容器的设计和实现：对Servelet

上面这个例子是不是很简单？是否打破了对一个简单http服务器的认知，减少了对它的恐惧。

但是上述的例子中只处理了静态资源，我们如果要处理Servlet呢？

### [¶](#组件设计-2) 组件设计

不难发现，我们只需要在HttpServer只需要请求的处理委托给ServletProcessor, 让它接受请求，并处理Response即可。

![img](/ethen/imgs/tomcat/tomcat-x-design-2.png)

### [¶](#组件实现-2) 组件实现

- 在HttpServer中

```java
public void await() {
    //....

        // create Response object
        Response response = new Response(output);
        response.setRequest(request);

        // 不再有response自己处理
        //response.sendStaticResource();

        // 而是如果以/servlet/开头，则委托ServletProcessor处理
        if (request.getUri().startsWith("/servlet/")) {
          ServletProcessor1 processor = new ServletProcessor1();
          processor.process(request, response);
        } else {
          // 原有的静态资源处理
          StaticResourceProcessor processor = new StaticResourceProcessor();
          processor.process(request, response);
        }

    // ....
  }   
```

- ServletProcessor 如何处理的？

```java
public class ServletProcessor1 {

  public void process(Request request, Response response) {

    // 获取servlet名字
    String uri = request.getUri();
    String servletName = uri.substring(uri.lastIndexOf("/") + 1);
    
    // 初始化URLClassLoader
    URLClassLoader loader = null;
    try {
      // create a URLClassLoader
      URL[] urls = new URL[1];
      URLStreamHandler streamHandler = null;
      File classPath = new File(Constants.WEB_ROOT);
      // the forming of repository is taken from the createClassLoader method in
      // org.apache.catalina.startup.ClassLoaderFactory
      String repository = (new URL("file", null, classPath.getCanonicalPath() + File.separator)).toString() ;
      // the code for forming the URL is taken from the addRepository method in
      // org.apache.catalina.loader.StandardClassLoader class.
      urls[0] = new URL(null, repository, streamHandler);
      loader = new URLClassLoader(urls);
    } catch (IOException e) {
      System.out.println(e.toString() );
    }

    // 用classLoader加载上面的servlet
    Class myClass = null;
    try {
      myClass = loader.loadClass(servletName);
    }
    catch (ClassNotFoundException e) {
      System.out.println(e.toString());
    }

    // 将加载到的class转成Servlet，并调用service方法处理
    Servlet servlet = null;
    try {
      servlet = (Servlet) myClass.newInstance();
      servlet.service((ServletRequest) request, (ServletResponse) response);
    } catch (Exception e) {
      System.out.println(e.toString());
    } catch (Throwable e) {
      System.out.println(e.toString());
    }

  }
}   
```

- Repsonse

```java
public class PrimitiveServlet implements Servlet {

  public void init(ServletConfig config) throws ServletException {
    System.out.println("init");
  }

  public void service(ServletRequest request, ServletResponse response)
    throws ServletException, IOException {
    System.out.println("from service");
    PrintWriter out = response.getWriter();
    out.println("Hello. Roses are red.");
    out.print("Violets are blue.");
  }

  public void destroy() {
    System.out.println("destroy");
  }

  public String getServletInfo() {
    return null;
  }
  public ServletConfig getServletConfig() {
    return null;
  }

}    
```

- 访问 URL

![img](/ethen/imgs/tomcat/tomcat-x-design-8.png)

### [¶](#利用外观模式改造) 利用外观模式改造

上述代码存在一个问题，

```java
// 将加载到的class转成Servlet，并调用service方法处理
    Servlet servlet = null;
    try {
      servlet = (Servlet) myClass.newInstance();
      servlet.service((ServletRequest) request, (ServletResponse) response);
    } catch (Exception e) {
      System.out.println(e.toString());
    } catch (Throwable e) {
      System.out.println(e.toString());
    }   
```

这里直接处理将request和response传给servlet处理是不安全的，因为request可以向下转型为Request类，从而ServeletRequest便具备了访问Request中方法的能力。

```java
public class Request implements ServletRequest {
  // 一些public方法
}
public class Response implements ServletResponse {

}    
```

解决的方法便是通过外观模式进行改造：

![img](/ethen/imgs/tomcat/tomcat-x-design-3.png)

- RequestFacade为例

```java
public class RequestFacade implements ServletRequest {

  private ServletRequest request = null;

  public RequestFacade(Request request) {
    this.request = request;
  }

  /* implementation of the ServletRequest*/
  public Object getAttribute(String attribute) {
    return request.getAttribute(attribute);
  }

  public Enumeration getAttributeNames() {
    return request.getAttributeNames();
  }

  public String getRealPath(String path) {
    return request.getRealPath(path);
  }

...  
```

- Process中由传入外观类

```java
Servlet servlet = null;
RequestFacade requestFacade = new RequestFacade(request); // 转换成外观类
ResponseFacade responseFacade = new ResponseFacade(response);// 转换成外观类
try {
  servlet = (Servlet) myClass.newInstance();
  servlet.service((ServletRequest) requestFacade, (ServletResponse) responseFacade);
}
catch (Exception e) {
  System.out.println(e.toString());
}
catch (Throwable e) {
  System.out.println(e.toString());
}   
```

## [¶](#总结) 总结

当我们看到这么一个简单的web容器实现之后，我们便不再觉得Tomcat高高在上；这将为我们继续分析Tomcat中核心源码提供基础。