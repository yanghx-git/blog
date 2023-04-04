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

通过 `go-svc` 实现生命周期管理`(优雅关机)` 

// TODO 单独写一篇文件 介绍 go-svc



