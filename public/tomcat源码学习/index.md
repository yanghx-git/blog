# 

# tomcat源码学习



> 参考 https://www.cnblogs.com/java-chen-hao/category/1516344.html

##  整体架构和组件



### 整体架构图



![img](tomcat源码学习.assets/webp.webp)

- `Connector(连接器)`: 用于处理连接相关的事情，并提供 Socket 与 Request 和 Response 相关的转化。  
- `Container(容器)`:  用于封装和管理 Servlet ，以及具体处理 Request 请求。



一个 tomcat 中只有一个 Server , 一个 Server 可以包含多个 Service , 一个 Service 只有一个 Container, 但是可以有多个 Connectors , 这是因为一个服务可以有多个连接，如同时提供 Http 和 Https 链接，也可以提供向相同协议不同端口的连接。

![img](./tomcat源码学习.assets/1168971-20190807172958351-1132740453.png)

多个 Connector 和一个 Container 就形成了一个 Serevice , 有了 Service 就可以对外提供服务了，但是 Service 还要一个生存的环境，必须要有人能够给她生命、掌控其生死大权，那就非 Service 莫属了！所以整个Tomcat 的生命周期由 Service 控制。

另外，上述的包含关系或说是父子关系，都可以在 tomcat 的 conf 目录下的 `server.xml`配置文件中看出。

![img](./tomcat源码学习.assets/1168971-20190807173324906-2029196119.png)

上边的配置文件，还可以通过下边的一张结构图更清楚的理解：

![img](./tomcat源码学习.assets/1168971-20190807173511076-1097002698.png)

***组件***

1. `Server` : 表示服务器,提供了一种优雅的方式来启动和停止整个系统，不必单独启停连接器和容器。

2. `Service`: 表示服务，Server 可以运行多个服务。比如一个 tomcat 里面可以运行订单服务、支付服务、用户服务等。

3. 每个 Service 可包含多个 Connector 和 一个 Container 。 因为每个服务允许同时支持多种协议，但是每种协议最终执行的 Servlet 却是相同的。

4. `Connector`: 表示连接器，比如一个服务可以同时支持 AJP 协议、Http 协议和 Https 协议，每种协议可使用一种连接器来支持。

5. `Container`: 表示容器，可以看做 Servlet 容器

   1. `Engine` : 引擎
   2. `HOST`:  主机
   3. `Context`: 上下文
   4. `Wrapper` : 包装器

6. Service 服务之下还有各种 `支撑组件` 

   1. `Manager` : 管理器，用于管理回话 Session
   2. `Logger`: 日志器，用于管理日志
   3. `Loader`: 加载器，和类加载有关，只会开放给 Context 所使用
   4. `Pipeline`: 管道组件，配合 Valve 实现过滤器功能
   5. `Valve`: 阀门组件，配合 Pipeline 实现过滤器功能
   6. `Realm`: 认证授权组件

   

   

   除了连接器和容器，管道组件和阀门组件也很关键，我们通过一张图来看看这两个组件

![img](./tomcat源码学习.assets/1168971-20190807174018910-1559786217.png)

### `Connector和Container 的微妙关系`

由上述内容我们大致可以知道一个请求发送到 Tomcat 之后，首先经过 Service 然后交给我们 Connector, Connector 用于接收请求并将接收到的请求封装为 Request 和 Response 来具体处理，Request 和 Response 封装完之后再交给 Cotainer 进行处理， Container 处理完请求之后再返回给 Connector, 最后在由 Connector 通过 Socket 将处理的结果返回给客户端，这样整个请求就处理完了。

Connector 最底层使用的是 Socket 进行连接的， Request 和 Response 是按照 Http 协议来封装的，所以Connector 同时需要实现 TCP/IP 协议和 Http 协议。



### `Connector`架构分析

Connector 用于接收请求并将请求封装成 Request 和 Response , 然后交给 Container 进行处理，Container 处理完之后再交给 Connector 返回给客户端。

因此，我们可以将 Connector 分为四个方面进行理解：

1. Connector 如何接收请求的？
2. 如何将请求封装成 Request 和 Response 的。
3. 封装完之后的 Request 和 Response 如何交给 Container 进行处理的？



***connector 的结构图***

![img](./tomcat源码学习.assets/1168971-20190807174351299-521376669.png)

Connector 就是使用 ProtocolHandler 来处理请求的，不同的 ProtocolHandler 代表不同的连接类型，比如 Http11Protocol 使用的是Socket来连接的， Http11NioProtocol 使用的是 NioSocket来连接的。

其中ProtocolHandler 包含了三个组件： Endpoint、Processor、Adapter。

1. `Endpoint` 用于处理底层 Socket 的网络连接，Processor 用于将 Endpoint 接收到的 Socket 封装成 Request, Adapter 用于将 Request 交给 Container 进行具体的处理。
2. Endpoint 由于是处理底层 Socket 网络连接，因此Endpoint 是用来实现 TCP/IP 协议的，而Processor 用来实现 Http 协议的，Adapter 将请求适配到 Servlet 容器进行具体的处理。
3. Endpoint 的抽象实现 AbstractEndpoint 里面定义的 Acceptor 和 AsyncTimout 两个内部类和一个 Handler 接口。Aceeptor 用于监听请求， AsyncTimout 用于检查异步 Request 的超时，Handler 用于处理接收到 Socket, 在内部调用 Processor 进行处理。

 

### `Container`如何处理请求的

Container 处理请求是使用 Pipeline-Valve管道来处理的 （Valve 是阀门的意思）

Pipeline-Valve 使用的是责任链模式，责任链模式是指在一个请求处理过程中有很多处理者依次对请求进行处理，每个处理者负责做自己相应的处理，处理完之后将处理后的请求返回，再让下一个处理者继续处理。

但是！Pipeline-Valve使用的责任链模式和普通的责任链模式有些不同，

1. 每个 Pipeline-Valve 都有特定的Valve, 而且是在管道的最后一个执行，这个Valve 叫做 BaseValve, BaseValve 是不可删除的；
2. 在上层容器的管道的BaseValve 中会调用下层容器的管道。

我们知道 Container 包含四个子容器，而这个四个子容器对应的 BaseValve 分别在: `StandardEngineValve、StandardHostValve、StandardContextValve、StandardWrapperValve`。

Pipeline 的处理流程图如下：

![img](./tomcat源码学习.assets/1168971-20190807174820042-2008919568.png)

1. Connector 在接收到请求后会首先调用最顶层容器的 Pipeline 来处理，这里的最顶层容器的 Pipeline 就是 EnginePipeline (Engine 的管道)；

2. 在 Engine 的管道中依次会执行 EngineValve1、EngineValve2等等，最后会执行 StandardEngineValve,在 StandardEngineValve 中会调用 Host 管道，然后再依次执行Host 的 HostValve1, HostValve2 等，最后在执行StandardHostValve,然后再依次调用 Context 的管道和 Wrapper 的管道，最后执行到 StandardWrapperValve。

3. 当执行到 StandardWrapperValve的时候，会在 StandardWrapperValve 中创建 FilterChain, 并调用其 doFilter 方法来处理请求，这个FilterChain 包含着我们配置的与请求相匹配的 Filter 和 Servlet, 其 doFilter 方法会依次调用所有的 Filter 方法 和 Servlet 的 service 方法，这样请求就得到处理了。

4. 当所有的 Pipeline-Valve 都执行完毕之后，并且处理完了具体的请求，这个时候就可以将返回的结果交给 Connector 了，Connector 在通过 Socket 的方式将结果返回给客户端。

   

## tomcat 的生命周期机制

### 什么是 Lifecycle

> Lifecycle ,其实就是一个状态机，对组件的由生到死状态的管理。

- 当组件在 `STARTING_PREP、STARTING、STARTED` 时，调用 `start()`方法没有任何效果。
- 当组件在 `NEW`状态时调用 `start()`方法会导致`init()` 方法被立即执行，随后`start()`方法被执行。
- 当组件在 `STOPING_PREP、STOPING、STOPED`时，调用`stop()`方法没有任何效果。
- 当一个组件在 `NEW`状态时，调用 `stop()`方法会将组件状态更新为 `STOPED`,比较典型的场景就是组件启动失败，其子组件还没有启动。当一个组件停止的时候，它将尝试停止它下面的所有子组件，即使子组件还没有启动。

### Lifecycle方法

```java
public interface Lifecycle {
    // 添加监听器
    public void addLifecycleListener(LifecycleListener listener);
    // 获取所以监听器
    public LifecycleListener[] findLifecycleListeners();
    // 移除某个监听器
    public void removeLifecycleListener(LifecycleListener listener);
    // 初始化方法
    public void init() throws LifecycleException;
    // 启动方法
    public void start() throws LifecycleException;
    // 停止方法，和start对应
    public void stop() throws LifecycleException;
    // 销毁方法，和init对应
    public void destroy() throws LifecycleException;
    // 获取生命周期状态
    public LifecycleState getState();
    // 获取字符串类型的生命周期状态
    public String getStateName();
}
```



### LifecycleBase

`LifecycleBase` 是 `Lifecycle` 的基本实现。



### 模板方法

LifecycleBase 是使用了状态机+模板模式来实现的。

```java
// 初始化方法
protected abstract void initInternal() throws LifecycleException;
// 启动方法
protected abstract void startInternal() throws LifecycleException;
// 停止方法
protected abstract void stopInternal() throws LifecycleException;
// 销毁方法
protected abstract void destroyInternal() throws LifecycleException;
```





## 源码-tomcat的启动流程

`org.apache.catalina.startup.Bootstrap`
