---
title: "NSQ 源码学习笔记"
date: 2023年4月03日10:16:13

tags: ["nsq"]
categories: ["nsq"]
---





# NSQ 源码学习笔记

> - 课程： https://www.bilibili.com/video/BV1qN4y157Vm
>
> - 课程文档：https://juejin.cn/post/7128643959043129374
>
> - NSQ文档：https://doc.yonyoucloud.com/doc/wiki/project/nsq-guide/index.html



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
// apps/nsqd/main.go:26

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
// internal/util/wait_group_wrapper.go

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

1. 验证协议
2. 创建client 
3. 让 client 处理业务`IOLoop`

```go
// /Users/yanghx/CODES/opensoruce/nsq/nsqd/tcp.go 

// Handle 处理 TCP 连接的方法
func (p *tcpServer) Handle(conn net.Conn) {
	p.nsqd.logf(LOG_INFO, "TCP: new client(%s)", conn.RemoteAddr())

	// 4字节的魔数.表示协议的版本号
	// The client should initialize itself by sending a 4 byte sequence indicating
	// the version of the protocol that it intends to communicate, this will allow us
	// to gracefully upgrade the protocol away from text/line oriented to whatever...
	buf := make([]byte, 4)
	// 会阻塞，直到读取够4字节  (  V2)
	_, err := io.ReadFull(conn, buf)
	if err != nil {
		p.nsqd.logf(LOG_ERROR, "failed to read protocol version - %s", err)
		conn.Close()
		return
	}
	protocolMagic := string(buf)

	p.nsqd.logf(LOG_INFO, "CLIENT(%s): desired protocol magic '%s'",
		conn.RemoteAddr(), protocolMagic)

	var prot protocol.Protocol
	switch protocolMagic {
	case "  V2":
		// 初始化
		prot = &protocolV2{nsqd: p.nsqd}
	default:
		// 协议错误，给个提示信息，结束连接
		protocol.SendFramedResponse(conn, frameTypeError, []byte("E_BAD_PROTOCOL"))
		conn.Close()
		p.nsqd.logf(LOG_ERROR, "client(%s) bad protocol magic '%s'",
			conn.RemoteAddr(), protocolMagic)
		return
	}
	// 对新建立的连接创建 client
	client := prot.NewClient(conn)
	// 存储 连接。 放到 map中
	p.conns.Store(conn.RemoteAddr(), client)

	// io处理 (业务)
	err = prot.IOLoop(client)
	if err != nil {
		p.nsqd.logf(LOG_ERROR, "client(%s) - %s", conn.RemoteAddr(), err)
	}

	p.conns.Delete(conn.RemoteAddr())
	client.Close()
}

```



### 发送消息到topic



主要看 nsq 的通信协议。刚好是 `prot.IOLoop(client)`下的代码。

`prot.IOLoop(client)`  同步chanal, 维护心跳。监听客户端发送的数据。

并解析协议头，转化为指令，交给 `nsqd/protocol_v2.Exec()`处理。

```go

func (p *protocolV2) Exec(client *clientV2, params [][]byte) ([]byte, error) {
	if bytes.Equal(params[0], []byte("IDENTIFY")) {
		return p.IDENTIFY(client, params)
	}
	err := enforceTLSPolicy(client, p, params[0])
	if err != nil {
		return nil, err
	}
	switch {
	case bytes.Equal(params[0], []byte("FIN")):
		return p.FIN(client, params)
	case bytes.Equal(params[0], []byte("RDY")):
		return p.RDY(client, params)
	case bytes.Equal(params[0], []byte("REQ")):
		return p.REQ(client, params)
	case bytes.Equal(params[0], []byte("PUB")):
		return p.PUB(client, params)
	case bytes.Equal(params[0], []byte("MPUB")):
		return p.MPUB(client, params)
	case bytes.Equal(params[0], []byte("DPUB")):
		return p.DPUB(client, params)
	case bytes.Equal(params[0], []byte("NOP")):
		return p.NOP(client, params)
	case bytes.Equal(params[0], []byte("TOUCH")):
		return p.TOUCH(client, params)
	case bytes.Equal(params[0], []byte("SUB")):
		return p.SUB(client, params)
	case bytes.Equal(params[0], []byte("CLS")):
		return p.CLS(client, params)
	case bytes.Equal(params[0], []byte("AUTH")):
		return p.AUTH(client, params)
	}
	return nil, protocol.NewFatalClientErr(nil, "E_INVALID", fmt.Sprintf("invalid command %s", params[0]))
}
```



发送消息是 `PUB`

```GO

func (p *protocolV2) PUB(client *clientV2, params [][]byte) ([]byte, error) {
	var err error

	if len(params) < 2 {
		return nil, protocol.NewFatalClientErr(nil, "E_INVALID", "PUB insufficient number of parameters")
	}

	// 验证 topicName 是否合法
	topicName := string(params[1])
	if !protocol.IsValidTopicName(topicName) {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_TOPIC",
			fmt.Sprintf("PUB topic name %q is not valid", topicName))
	}
	// 读取4字节， 获取 消息体长度
	bodyLen, err := readLen(client.Reader, client.lenSlice)
	if err != nil {
		return nil, protocol.NewFatalClientErr(err, "E_BAD_MESSAGE", "PUB failed to read message body size")
	}

	if bodyLen <= 0 {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_MESSAGE",
			fmt.Sprintf("PUB invalid message body size %d", bodyLen))
	}

	if int64(bodyLen) > p.nsqd.getOpts().MaxMsgSize {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_MESSAGE",
			fmt.Sprintf("PUB message too big %d > %d", bodyLen, p.nsqd.getOpts().MaxMsgSize))
	}
	// 消息体
	messageBody := make([]byte, bodyLen)
	_, err = io.ReadFull(client.Reader, messageBody)
	if err != nil {
		return nil, protocol.NewFatalClientErr(err, "E_BAD_MESSAGE", "PUB failed to read message body")
	}

	//
	if err := p.CheckAuth(client, "PUB", topicName, ""); err != nil {
		return nil, err
	}

	// 获取topic ,不存在的话 会创建
	topic := p.nsqd.GetTopic(topicName)
	msg := NewMessage(topic.GenerateID(), messageBody)
	err = topic.PutMessage(msg)
	if err != nil {
		return nil, protocol.NewFatalClientErr(err, "E_PUB_FAILED", "PUB failed "+err.Error())
	}

	client.PublishedMessage(topicName, 1)

	return okBytes, nil
}

```





### 看看 topic 是如何创建的

> 上面的 `p.nsqd.GetTopic(topicName)` 就是创建 topic 的入口

`p.nsqd.GetTopic(topicName)` 首先检查是否已经创建了 `（topicMap）`。不存在的话，进入创建流程。



`topic/NewTopic()`

1. 初始化`struct`

   ```go
   	t := &Topic{
   		name:              topicName,
   		channelMap:        make(map[string]*Channel), // 存储 topic 下的 Channel 信息
   		memoryMsgChan:     make(chan *Message, nsqd.getOpts().MemQueueSize),
   		startChan:         make(chan int, 1),
   		exitChan:          make(chan int),
   		channelUpdateChan: make(chan int),
   		nsqd:              nsqd,
   		paused:            0,
   		pauseChan:         make(chan int),
   		deleteCallback:    deleteCallback,
   		idFactory:         NewGUIDFactory(nsqd.getOpts().ID), // 消息 id 的工厂
   	}
   ```

   

2. 判断是否临时 `topic`,临时 topic 的数据不写如磁盘，是一个单独的 backend。 普通 topic 的 backend 是 `diskqueue` 磁盘队列

3. 启动一个携程，*将* *message* *分发到* *topic* *下所有的* *channel*  `t.waitGroup.Wrap(t.messagePump)`

4. 发送通知

### 发送给 topic 的数据存那了

顺着 topic 的创建，接着看 PUB 函数。下一步就是 创建 `Messaage` 结构体，和 存储 `Message`。

```go

func (p *protocolV2) PUB(client *clientV2, params [][]byte) ([]byte, error) {
	var err error

	if len(params) < 2 {
		return nil, protocol.NewFatalClientErr(nil, "E_INVALID", "PUB insufficient number of parameters")
	}

	// 验证 topicName 是否合法
	topicName := string(params[1])
	if !protocol.IsValidTopicName(topicName) {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_TOPIC",
			fmt.Sprintf("PUB topic name %q is not valid", topicName))
	}
	// 读取4字节， 获取 消息体长度
	bodyLen, err := readLen(client.Reader, client.lenSlice)
	if err != nil {
		return nil, protocol.NewFatalClientErr(err, "E_BAD_MESSAGE", "PUB failed to read message body size")
	}

	if bodyLen <= 0 {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_MESSAGE",
			fmt.Sprintf("PUB invalid message body size %d", bodyLen))
	}

	if int64(bodyLen) > p.nsqd.getOpts().MaxMsgSize {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_MESSAGE",
			fmt.Sprintf("PUB message too big %d > %d", bodyLen, p.nsqd.getOpts().MaxMsgSize))
	}
	// 消息体
	messageBody := make([]byte, bodyLen)
	_, err = io.ReadFull(client.Reader, messageBody)
	if err != nil {
		return nil, protocol.NewFatalClientErr(err, "E_BAD_MESSAGE", "PUB failed to read message body")
	}

	//
	if err := p.CheckAuth(client, "PUB", topicName, ""); err != nil {
		return nil, err
	}

	// 获取topic ,不存在的话 会创建
	topic := p.nsqd.GetTopic(topicName)
	// 创建  message
	msg := NewMessage(topic.GenerateID(), messageBody)
	// 将 message 发送给 topic 中的队列 channel
	err = topic.PutMessage(msg)
	if err != nil {
		return nil, protocol.NewFatalClientErr(err, "E_PUB_FAILED", "PUB failed "+err.Error())
	}
	// 统计 发送消息的数量
	client.PublishedMessage(topicName, 1)

	return okBytes, nil
}

```



写 `Message` 的流程

1. 拿锁
2. 检查  `memoryMsgChan 小于0, 临时topic,延时消息`，不写入队列，
3. 将数据写入 backend,也就是队列，一般是磁盘 backend。



```go
// 重点的写入流程
func (t *Topic) put(m *Message) error {
	// If mem-queue-size == 0, avoid memory chan, for more consistent ordering,
	// but try to use memory chan for deferred messages (they lose deferred timer
	// in backend queue) or if topic is ephemeral (there is no backend queue).

	// 如果 mem-queue-size == 0，避免 memory chan，以获得更一致的排序，
	// 但尝试对延迟消息使用 memory chan（它们会丢失延迟计时器 在后端队列中）或者如果主题是临时的（没有后端队列）。
	if cap(t.memoryMsgChan) > 0 || t.ephemeral || m.deferred != 0 {
		select {
		case t.memoryMsgChan <- m:
			return nil
		default:
			break // write to backend
		}
	}
	// 会将数据写入 backend
	err := writeMessageToBackend(m, t.backend)
	t.nsqd.SetHealth(err)
	if err != nil {
		t.nsqd.logf(LOG_ERROR,
			"TOPIC(%s) ERROR: failed to write message to backend - %s",
			t.name, err)
		return err
	}
	return nil
}
```



### 消费者如何订阅消息

看 `protocol_v2/SUB()`

1. 检查 client 状态， 只能是 `stateInit`
2. 心跳检查，禁用心跳的，不让订阅
3. 参数检查
4. 认证
5. 获取 topic , 没有就新增
6. 获取 channel , 没有就新增
7. 将 topic, channel ,client 关联起来。`一个 topic 有多个 channel ,一个 channel 有多个 client ,一个 client 只对应一个 Channel`
8. 里面用了一个for循环，是重试机制，因为 channel 和 topic 可能是临时的，或者正在退出，这种情况下要重试一次。
9.  订阅成功，更新 client 的订阅状态，并发送通知。



```go

// SUB <topic_name> <channel_name>\n
// <topic_name> - 字符串 (建议包含 #ephemeral 后缀)
// <channel_name> - 字符串 (建议包含 #ephemeral 后缀)
func (p *protocolV2) SUB(client *clientV2, params [][]byte) ([]byte, error) {
	// 判断 client 的状态
	if atomic.LoadInt32(&client.State) != stateInit {
		return nil, protocol.NewFatalClientErr(nil, "E_INVALID", "cannot SUB in current state")
	}
	// 心跳检查，不能在禁用心跳的情况下进行 SUB
	if client.HeartbeatInterval <= 0 {
		return nil, protocol.NewFatalClientErr(nil, "E_INVALID", "cannot SUB with heartbeats disabled")
	}
	// 参数检查 是否  sub topicName channelName
	if len(params) < 3 {
		return nil, protocol.NewFatalClientErr(nil, "E_INVALID", "SUB insufficient number of parameters")
	}

	topicName := string(params[1])
	if !protocol.IsValidTopicName(topicName) {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_TOPIC",
			fmt.Sprintf("SUB topic name %q is not valid", topicName))
	}

	channelName := string(params[2])
	if !protocol.IsValidChannelName(channelName) {
		return nil, protocol.NewFatalClientErr(nil, "E_BAD_CHANNEL",
			fmt.Sprintf("SUB channel name %q is not valid", channelName))
	}
	// 认证
	if err := p.CheckAuth(client, "SUB", topicName, channelName); err != nil {
		return nil, err
	}

	// This retry-loop is a work-around for a race condition, where the
	// last client can leave the channel between GetChannel() and AddClient().
	// Avoid adding a client to an ephemeral channel / topic which has started exiting.
	// 这个重试循环是一个竞争条件的变通方法，其中
	// 最后一个客户端可以离开 GetChannel() 和 AddClient() 之间的通道。
	// 避免将客户端添加到已开始退出的临时通道/主题。

	var channel *Channel
	for i := 1; ; i++ {
		// 取 topic ,没有的话，就创建
		topic := p.nsqd.GetTopic(topicName)
		// 去 channel ,没有的话，就创建
		channel = topic.GetChannel(channelName)
		// 把 client 放入 Channel 中, 一个 Channel 对应多个 client
		if err := channel.AddClient(client.ID, client); err != nil {
			return nil, protocol.NewFatalClientErr(err, "E_SUB_FAILED", "SUB failed "+err.Error())
		}
		// 临时，或者 topic,channel 正在退出
		if (channel.ephemeral && channel.Exiting()) || (topic.ephemeral && topic.Exiting()) {
			// 移除这个client
			channel.RemoveClient(client.ID)
			if i < 2 {
				time.Sleep(100 * time.Millisecond)
				continue
			}
			return nil, protocol.NewFatalClientErr(nil, "E_SUB_FAILED", "SUB failed to deleted topic/channel")
		}
		break
	}
	// 更新 client 的 State 为 stateSubscribed 表示已订阅，无法再次订阅
	atomic.StoreInt32(&client.State, stateSubscribed)
	// 一个 client 只关联一个 Channel ， 一个 	Channel 关联多个 Client
	client.Channel = channel
	// update message pump
	// 发送订阅事件到 channel
	client.SubEventChan <- channel

	return okBytes, nil
}

```

