---
title: "NSQ 源码学习笔记"
date: 2023年4月03日10:16:13

tags: ["nsq"]
categories: ["nsq"]
---





# NSQ 源码学习笔记

> 课程： https://www.bilibili.com/video/BV1qN4y157Vm/?spm_id_from=333.788&vd_source=cfe317b4e512f948eb08fc25356a8589
>
> 文档：https://juejin.cn/post/7128643959043129374

## 核心组件



- nsqd

  - 负责接收、排队、转发消息到客户端的守护进程，可以单独运行
  - 监听 4150（TCP）、4151（HTTP）、4152（HTTPS，可选）端口

- nsqlookupd

  - 管理拓扑信息
  - 不需要和其他 `nsqlookupd` 协调提供查询
  - `nsqd` 向 `nsqlookupd` 广播 topic 和 channel 信息
  - 客户端查询 `nsqlookupd` 发现指定 topic 的 `nsqd` 生产者
  - 监听 4160（TCP） 和 4161（HTTP）端口；

- nsqadmin

  - 提供一个 web ui, 用于实时查看集群信息，进行各种任务管理。

- utilities

  nsq 也提供了一些工具供我们使用

  - nsq_stat 拉取指定 topic/channel 的所有消费者，展示统计数据
  - nsq_tail 消费指定 topic/channel 的数据，并输出到控制台
  - nsq_to_file 消费指定 topic/channel 的数据，并写到文件中，有选择的滚动和/或压缩文件
  - nsq_to_http 消费指定 topic/channel 的数据，发送到指定的 HTTP 端点
  - nsq_to_nsq 消费者指定 topic/channel 的数据，通过 TCP 重发布消息到目的 nsqd
  - to_nsq 通过标准输入流将数据发送到目的 nsqd



每一个组件对应一个目录，很方便找

![image-20230404223929022](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230404223929022.png)



##  部署

TODO 后面搞一个 docker-comonse 部署的例子。主要是体验一下集群。后面可以通过源码启动单节点，去debug 一些功能。

##  源码环境搭建

1. fock 源码
2. 创建自己的分支
3. 编译 `go mod tidy`
4. 启动项目 



## NSQD 源码分析

### 启动流程  `apps/nsqd/main.go`

通过 `go-svc` 实现生命周期管理`(优雅启停)` 

```
首先实现 go-svc 提供的 Service 接口
Init 
Start 
Stop

svc.Run() 方法 会依次执行 Init Start 方法 。 并注册系统监听
开一个for循环，阻塞进程，在接收到 系统停止的指令时，执行 Stop方法

```

#### 初始化 `Init`

1. 构造 `Options` : 处理配置文件
2. 创建 `NSQD`实例:  `nsqd.New()`
   1. 检查 `diskqueue` 目录`(磁盘队列 文件的 路径)`。并验证这个目录没有被占用 `(目录锁 dl.Lock())`
   2. 创建带取消的 `Context`，用于内部停止服务。
   3. 创建 `httpcli`
   4. 创建集群信息
   5. 做各类检查
   6. 创建 `tcpServer `,  `httpServer`



#### 启动 `Start`

1. 加载，持久化元数据

2. 开一个携程执行`nsqd.Main()` 。这个携程不会退出的。`nsed.Main()`会一直阻塞

   

​	

`nsqd.Main()`的执行流程

1. 创建 `exitCh`, 用于阻塞方法
2.  定义 `exitFunc`,封装方法，如果传入 err 不为 nil. 会打印日志，并停止 当前携程
3. 启动 `tcpServer`
4. 启动 `httpServer`
5. 启动 `httpsListener`
6. 维护 `channel` 中延时队列和等待消息确认队列
7. 连接到 `nsqlookupd`



```go
func (n *NSQD) Main() error {

	exitCh := make(chan error)
	var once sync.Once
	// 退出函数
	// 如果传入 err 不为 nil. 会打印日志，并停止 当前携程
	exitFunc := func(err error) {
		// once.Do() 只会执行一次
		once.Do(func() {
			if err != nil {
				n.logf(LOG_FATAL, "%s", err)
			}
			exitCh <- err
		})
	}
	// 自己封装的 waitGroup。 Wrap， 会开一个携程 等待匿名函数执行
	n.waitGroup.Wrap(func() {
		// 创建 tcp server
		// 里面是个无限 for 循环。 会一直阻塞
		err := protocol.TCPServer(n.tcpListener, n.tcpServer, n.logf)
		exitFunc(err)
	})
	// 创建 http server
	if n.httpListener != nil {
		httpServer := newHTTPServer(n, false, n.getOpts().TLSRequired == TLSRequired)
		n.waitGroup.Wrap(func() {
			exitFunc(http_api.Serve(n.httpListener, httpServer, "HTTP", n.logf))
		})
	}
	// 创建 https server
	if n.httpsListener != nil {
		httpsServer := newHTTPServer(n, true, true)
		n.waitGroup.Wrap(func() {
			exitFunc(http_api.Serve(n.httpsListener, httpsServer, "HTTPS", n.logf))
		})
	}
	// 维护 channel 中延时队列和等待消息确认队列
	n.waitGroup.Wrap(n.queueScanLoop)
	// 连接到 nsqlookupd
	n.waitGroup.Wrap(n.lookupLoop)
	// 一个统计的服务
	if n.getOpts().StatsdAddress != "" {
		n.waitGroup.Wrap(n.statsdLoop)
	}
	// 会一直阻塞
	err := <-exitCh
	return err
}

```



```go
type WaitGroupWrapper struct {
	sync.WaitGroup
}

// Wrap 包装。 会阻塞一下 ，在 cb 函数执行完之后，才会退出
func (w *WaitGroupWrapper) Wrap(cb func()) {
	w.Add(1)
	go func() {
		cb()
		w.Done()
	}()
}

```



重点看 `exitFunc`和 `waitGroup` 的配合。以 `tcpServer` 的创建为例。

`waitGroup` 使用携程执行 匿名函数，并会等待这个函数结束。`tcpServe`是会一直阻塞的，直到出现异常，才会退出。 这时 `exitFunc`会捕获到err, 并触发`exitChan`,结束整个 `Main()`



### TCPHandler 是如何处理连接的

