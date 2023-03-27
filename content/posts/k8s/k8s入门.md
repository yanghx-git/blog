---
title: "k8s入门笔记"
date: 2023-03-20T02:42:30+08:00

tags: ["k8s"]
categories: ["k8s"]
---



# `k8s` 介绍

> 阳明的ks8进阶手册 `https://www.qikqiak.com/k8s-book`
>
> k8s学习笔记 `https://www.huweihuang.com/kubernetes-notes`

## 安装 

### 脚本部署

用的脚本部署方式，具体看`https://github.com/lework/kainstall`

```shell
wget https://ghproxy.com/https://raw.githubusercontent.com/lework/kainstall/master/kainstall-centos.sh

# 本地安装 1.20.6 
bash kainstall-centos.sh init \
  --master 192.168.0.110 \
  --worker 192.168.0.112 \
  --user root \
  --password 123456 \
  --port 22 \
  --version 1.20.6 \
  --10years \
  --offline-file 1.20.6_centos7.tgz



# 本地安装 1.22.10
bash kainstall-centos.sh init \
  --master 192.168.128.125 \
  --worker 192.168.128.126 \
  --user root \
  --password 123456 \
  --port 22 \
  --version 1.22.10 \
  --10years \
  --offline-file 1.22.10_centos7.tgz
 

```

**问题**

- kubeadm init  失败 -> `https://www.cnblogs.com/chalon/p/14840216.html`



**完全卸载k8s**

```shell
kubeadm reset -f
modprobe -r ipip
lsmod
rm -rf ~/.kube/
rm -rf /etc/kubernetes/
rm -rf /etc/systemd/system/kubelet.service.d
rm -rf /etc/systemd/system/kubelet.service
rm -rf /usr/bin/kube*
rm -rf /etc/cni
rm -rf /opt/cni
rm -rf /var/lib/etcd
rm -rf /var/etcd
yum clean all
yum remove kube* -y
```



## 常用命令

- `kubeadm`: 用来安装k8s的脚本
- `kubectl`: k8s的管理命令

###  `kubeadm`的常用命令





### `kubectl`的常用命令

> 详细 https://www.huweihuang.com/kubernetes-notes/operation/kubectl/kubectl-commands.html

```bash
kubectl [command] [TYPE] [NAME] [flags]
```

- `command`:  指定要在一个或多个资源进行操作，例如`create`，`get`，`describe`，`delete`。

- `TYPE`:  指定[资源类型](https://kubernetes.io/cn/docs/user-guide/kubectl-overview/#资源类型)。资源类型区分大小写，您可以指定单数，复数或缩写形式.

  ```shell
  # 以下命令产生相同的输出：
  kubectl get pod pod1  
  kubectl get pods pod1 
  kubectl get po pod1
  
  # get的常用方式
  kubectl get pods  
  kubectl get deployments
  kubectl get rc  # resource controller
  kubectl get rs  # resource set 
  kubectl get service
  kubectl get ingress
  ```

  

- `NAME`: 指定资源的名称。名称区分大小写。如果省略名称，则会显示所有资源的详细信息 

  ```shell
  kubectl get pods  # 省略命令，查看全部pod 
  kubectl get pods  ingress-demo-app-6c88bddcf8-725nk # 查看指定pod 
  kubectl get pods  ingress-demo-app-6c88bddcf8-725nk, xxxx , xxx # 查看多个pod
  
  * 要分组资源，如果它们都是相同的类型：`TYPE1 name1 name2 name<#>`.<br/>
  例: `$ kubectl get pod example-pod1 example-pod2`
  
  * 要分别指定多种资源类型:  `TYPE1/name1 TYPE1/name2 TYPE2/name3 TYPE<#>/name<#>`.<br/>
  例: `$ kubectl get pod/example-pod1 replicationcontroller/example-rc1`
  ```

  

- `flags`: 指定可选标志。例如，您可以使用`-s`或`--serverflags`来指定Kubernetes API服务器的地址和端口。

  



## k8s架构

## k8s对象

### 一` Master`

集群的控制节点，负责整个集群的管理和控制，kubernetes 的所有的命令基本都是发给 master 的，由它来负责具体执行流程。

#### `master`的组件

- `kube-apiserver`: 资源增删改查的入口
- `kube-controller-manager`: 资源对象的大总管
- `kube-scheduler`: 负责资源调度（Pod 调度）
- `etcd Server`: kubernetes 的所有的资源对象的数据保存在etcd中

### 二 `NODE`

Node 是集群的工作负载节点，默认情况 kubelet 会向 Master 注册自己，一但 Node 被纳入集群管理范围。

kubelet 会定时向 Master 汇报自身的情报，包括操作系统、Docker版本、机器资源情况等。

如果 Node 超过指定时间不上报信息，会被 Master 判断为 失联，标记为 Not Ready , 随后 Master 会触发 Pod 转移。

#### 1 NODE 的组件

- `kubelet`: Pod 的管家，与 Master 通信
- `kube-proxy`: 实现 kubernetes Service 的通信与负载均衡机制的重要组件
- `docker`: 容器的创建和管理

#### 2 NODE 的相关命令

- `kubectl get nodes `
- `kubeclt describe node {node_name}`



#### 3 describe 命令的 Node 信息

- Node基本信息：名称、标签、创建时间等
- Node当前的状态，Node启动后会进行自检工作，磁盘是否满，内存是否不足，若都正常则切换为Ready状态。
- Node的主机地址与主机名
- Node上的资源总量：CPU,内存，最大可调度Pod数量等
- Node可分配资源量：当前Node可用于分配的资源量
- 主机系统信息：主机唯一标识符UUID，Linux kernel版本号，操作系统，kubernetes版本，kubelet与kube-proxy版本
- 当前正在运行的Pod列表及概要信息
- 已分配的资源使用概要，例如资源申请的最低、最大允许使用量占系统总量的百分比
- Node相关的Event信息。

```shell
[root@k8s-master-node1 ~]# kubectl  describe nodes k8s-master-node1
Name:               k8s-master-node1 # 名称
Roles:              control-plane,master
Labels:             beta.kubernetes.io/arch=amd64 # 标签
                    beta.kubernetes.io/os=linux
                    kubernetes.io/arch=amd64
                    kubernetes.io/hostname=k8s-master-node1
                    kubernetes.io/os=linux
                    node-role.kubernetes.io/control-plane=
                    node-role.kubernetes.io/master=
Annotations:        flannel.alpha.coreos.com/backend-data: {"VtepMAC":"0a:f2:1a:ec:31:c5"}
                    flannel.alpha.coreos.com/backend-type: vxlan
                    flannel.alpha.coreos.com/kube-subnet-manager: true
                    flannel.alpha.coreos.com/public-ip: 192.168.128.124
                    kubeadm.alpha.kubernetes.io/cri-socket: unix:///run/containerd/containerd.sock
                    node.alpha.kubernetes.io/ttl: 0
                    volumes.kubernetes.io/controller-managed-attach-detach: true
CreationTimestamp:  Wed, 11 Jan 2023 09:34:42 +0800 # 创建时间
Taints:             <none>
Unschedulable:      false
Lease:
  HolderIdentity:  k8s-master-node1
  AcquireTime:     <unset>
  RenewTime:       Wed, 11 Jan 2023 15:09:41 +0800
Conditions:
  Type                 Status  LastHeartbeatTime                 LastTransitionTime                Reason                       Message
  ----                 ------  -----------------                 ------------------                ------                       -------
  NetworkUnavailable   False   Wed, 11 Jan 2023 14:08:54 +0800   Wed, 11 Jan 2023 14:08:54 +0800   FlannelIsUp                  Flannel is running on this node
  MemoryPressure       False   Wed, 11 Jan 2023 15:09:47 +0800   Wed, 11 Jan 2023 09:34:42 +0800   KubeletHasSufficientMemory   kubelet has sufficient memory available
  DiskPressure         False   Wed, 11 Jan 2023 15:09:47 +0800   Wed, 11 Jan 2023 09:34:42 +0800   KubeletHasNoDiskPressure     kubelet has no disk pressure
  PIDPressure          False   Wed, 11 Jan 2023 15:09:47 +0800   Wed, 11 Jan 2023 09:34:42 +0800   KubeletHasSufficientPID      kubelet has sufficient PID available
  Ready                True    Wed, 11 Jan 2023 15:09:47 +0800   Wed, 11 Jan 2023 09:35:27 +0800   KubeletReady                 kubelet is posting ready status
Addresses: # 主机地址 主机名
  InternalIP:  192.168.128.124 
  Hostname:    k8s-master-node1
Capacity: # 资源信息  
  cpu:                2
  ephemeral-storage:  17394Mi
  hugepages-1Gi:      0
  hugepages-2Mi:      0
  memory:             3861264Ki
  pods:               200
Allocatable:
  cpu:                1500m
  ephemeral-storage:  14267554175
  hugepages-1Gi:      0
  hugepages-2Mi:      0
  memory:             2812688Ki
  pods:               200
System Info:
  Machine ID:                 d6d800db39f144a4887558a9f83ceea9
  System UUID:                A6714D56-D99C-3872-4DE2-0D72C866ADC4
  Boot ID:                    aa579834-1596-40df-96ea-5e41dbd5ee89
  Kernel Version:             3.10.0-1160.71.1.el7.x86_64
  OS Image:                   CentOS Linux 7 (Core)
  Operating System:           linux
  Architecture:               amd64
  Container Runtime Version:  containerd://1.6.15
  Kubelet Version:            v1.20.6
  Kube-Proxy Version:         v1.20.6
PodCIDR:                      10.244.0.0/24
PodCIDRs:                     10.244.0.0/24
Non-terminated Pods:          (7 in total)
  Namespace                   Name                                        CPU Requests  CPU Limits  Memory Requests  Memory Limits  AGE
  ---------                   ----                                        ------------  ----------  ---------------  -------------  ---
  kube-system                 coredns-8496bbfb78-4w6vw                    100m (6%)     0 (0%)      70Mi (2%)        170Mi (6%)     5h34m
  kube-system                 etcd-k8s-master-node1                       100m (6%)     0 (0%)      100Mi (3%)       0 (0%)         5h34m
  kube-system                 kube-apiserver-k8s-master-node1             250m (16%)    0 (0%)      0 (0%)           0 (0%)         5h34m
  kube-system                 kube-controller-manager-k8s-master-node1    200m (13%)    0 (0%)      0 (0%)           0 (0%)         5h34m
  kube-system                 kube-flannel-ds-d85pr                       100m (6%)     100m (6%)   50Mi (1%)        50Mi (1%)      5h34m
  kube-system                 kube-proxy-8tslx                            0 (0%)        0 (0%)      0 (0%)           0 (0%)         5h34m
  kube-system                 kube-scheduler-k8s-master-node1             100m (6%)     0 (0%)      0 (0%)           0 (0%)         5h34m
Allocated resources:
  (Total limits may be over 100 percent, i.e., overcommitted.)
  Resource           Requests    Limits
  --------           --------    ------
  cpu                850m (56%)  100m (6%)
  memory             220Mi (8%)  220Mi (8%)
  ephemeral-storage  100Mi (0%)  0 (0%)
  hugepages-1Gi      0 (0%)      0 (0%)
  hugepages-2Mi      0 (0%)      0 (0%)
Events:
  Type     Reason                   Age                    From        Message
  ----     ------                   ----                   ----        -------
  Normal   NodeHasSufficientMemory  5h36m (x5 over 5h36m)  kubelet     Node k8s-master-node1 status is now: NodeHasSufficientMemory
  Normal   NodeHasNoDiskPressure    5h36m (x5 over 5h36m)  kubelet     Node k8s-master-node1 status is now: NodeHasNoDiskPressure
  Normal   NodeHasSufficientPID     5h36m (x5 over 5h36m)  kubelet     Node k8s-master-node1 status is now: NodeHasSufficientPID
  Normal   Starting                 5h34m                  kubelet     Starting kubelet.
  Warning  InvalidDiskCapacity      5h34m                  kubelet     invalid capacity 0 on image filesystem
  Normal   NodeAllocatableEnforced  5h34m                  kubelet     Updated Node Allocatable limit across pods
  Normal   NodeHasSufficientMemory  5h34m                  kubelet     Node k8s-master-node1 status is now: NodeHasSufficientMemory
  Normal   NodeHasNoDiskPressure    5h34m                  kubelet     Node k8s-master-node1 status is now: NodeHasNoDiskPressure
  Normal   NodeHasSufficientPID     5h34m                  kubelet     Node k8s-master-node1 status is now: NodeHasSufficientPID
  Normal   Starting                 5h34m                  kube-proxy  Starting kube-proxy.
  Normal   NodeReady                5h34m                  kubelet     Node k8s-master-node1 status is now: NodeReady
  Normal   Starting                 63m                    kubelet     Starting kubelet.
  Warning  InvalidDiskCapacity      63m                    kubelet     invalid capacity 0 on image filesystem
  Normal   NodeHasSufficientMemory  63m (x7 over 63m)      kubelet     Node k8s-master-node1 status is now: NodeHasSufficientMemory
  Normal   NodeHasSufficientPID     63m (x7 over 63m)      kubelet     Node k8s-master-node1 status is now: NodeHasSufficientPID
  Normal   NodeAllocatableEnforced  63m                    kubelet     Updated Node Allocatable limit across pods
  Normal   NodeHasNoDiskPressure    62m (x8 over 63m)      kubelet     Node k8s-master-node1 status is now: NodeHasNoDiskPressure
  Normal   Starting                 61m                    kube-proxy  Starting kube-proxy.

```



### 三 POD

Pod 是k8s中操作的基本单元。 每个Pod 都有一个根容器( Pause 容器 )，Pause 容器的状态代表整个容器组的状态，其他业务容器共享 Pause 的 IP, 即Pod IP, 共享 Pause 挂载的 Volume, 这样简化了同个Pod中不同容器之间的网络问题和文件共享问题。  



![pod](https://res.cloudinary.com/dqxtn0ick/image/upload/v1510578930/article/kubernetes/concept/pod.png)



1. k8s集群中，同宿主机的或不同宿主机的 Pod 之间要求能够TCP/IP直接通信，因此采用虚拟二层网络技术来实现，例如 Flannel, Openvswitch( OVS ) 等，这样在同一个集群中不同的宿主机的 POD IP 为不同 IP段，集群中的所有 POD IP 都是唯一的，不同 POD 之间可以直接通信。

2. POD 有两种类型： 普通 POD 和 静态 POD 。 静态 POD 即不通过 K8S 调度和创建，直接在某个具体的 Node 机器上通过具体的文件来启动。 普通 POD 则是由 K8S 创建、调度，同时数据存放在 ETCD 中。 

3. POD IP 和具体的容器端口 (ContainnerPort) 组成一个具体的通信地址，即 Endpoint 。 一个 POD 中可以存在多个容器，可以有多个端口，POD IP 一样，即有多个 Endpoint。

4. POD Volume 是定义在 POD 之上，被各个容器挂载自己的文件系统中，可以用`分布式文件系统`实现后端存储功能。

5. POD 中的 Event 事件可以用来排查问题，可以通过 `kubectl describe pod xxx ` 来查看对应的事件。

6. 每个 POD 可以对其能使用的服务器上的计算资源设置限额，一般为 CPU 和 Memory。 K8S 中一般将 千分之一个的 CPU 配置作为最小单元，用m表示，是一个绝对值，即100m对于一个core 的机器还是 48个 core 的机器都是一样的大小。 Memory 配额也是个绝对值，单位为内存字节数。

7. 资源配额的两个参数。

   1. `Requests` : 该资源的最小申请量，系统必须满足要求。

   2. `Limits`: 该资源最大允许使用量，当超过该量，k8s 会 kill 并重启POD

      

![pod2](https://res.cloudinary.com/dqxtn0ick/image/upload/v1510578930/article/kubernetes/concept/pod2.png)

### 四 Label

1. Label是一个键值对，可以附加在任何对象上，比如Node,Pod,Service,RC等。Label和资源对象是多对多的关系，即一个Label可以被添加到多个对象上，一个对象也可以定义多个Label。
2. Label的作用主要用来实现精细的、多维度的资源分组管理，以便进行资源分配，调度，配置，部署等工作。
3. Label通俗理解就是“标签”，通过标签来过滤筛选指定的对象，进行具体的操作。k8s通过Label Selector(标签选择器)来筛选指定Label的资源对象，类似SQL语句中的条件查询（WHERE语句）。
4. Label Selector有基于等式和基于集合的两种表达方式，可以多个条件进行组合使用。
5. 基于等式：name=redis-slave（匹配name=redis-slave的资源对象）;env!=product(匹配所有不具有标签env=product的资源对象)
6. 基于集合：name in (redis-slave,redis-master);name not in (php-frontend)（匹配所有不具有标签name=php-frontend的资源对象）

**使用场景**

1. kube-controller进程通过资源对象RC上定义的Label Selector来筛选要监控的Pod副本数，从而实现副本数始终保持预期数目。
2. kube-proxy进程通过Service的Label Selector来选择对应Pod，自动建立每个Service到对应Pod的请求转发路由表，从而实现Service的智能负载均衡机制。
3. kube-scheduler实现Pod定向调度：对Node定义特定的Label，并且在Pod定义文件中使用NodeSelector标签调度策略。

### 五   `RC`



RC (Replication Controller) 是K8S 系统中的核心概念，定义了一个期望的场景。

主要包括

- POD 期望的副本数 `replicas`
- 用于筛选目标 POD 的 `Label Selector`
- 用于创建 POD 的模板 `template`

RC 特性说明 :

1. POD 的缩放可以通过以下命令实现  `kubectl scale rc redis-slave --replicas=3 `
2. 删除 RC 并不会删除该 RC 创建的 POD , 可以将副本数设置为0，即可删除对应 POD 。 或者通过 `kubectl stop / delete` 命令来一次性删除 RC 和其创建的 POD。
3. 改变 RC 中 POD 模板的镜像版本可以实现滚动升级 (`Rolling Update `) 具体操作见`https://kubernetes.io/docs/tasks/run-application/rolling-update-replication-controller` 
4. kubernetes1.2 以上版本将 RC 升级为 `Replica Set `, 它与当前 RC 的唯一区别在于 `Replica SET` 支持基于集合的 `Label Selector (Set-based selector)`, 而旧版本 RC 只支持基于等式的` Label Selector (equality-based selector)`。
5. kubernetes1.2 以上版本通过 `Deployment` 来维护 `Replica Set ` 而不是单独使用 `Replica Set`。 即控制流为 ` Deployment -> Replica Set -> POD `。 即新版本的 `Deployment + Replica Set` 替代了 RC 的作用。

###  六 `Deployment` 

Deployment 是 kubernetes 1.2 引入的概念， 用来解决 POD 的编排问题。 Deployment 可以理解为 RC 的 升级版 `RC + Replica Set` 。 特点在于可以随时知道 POD 的部署进度，即对 POD 的创建、调度、绑定节点、启动容器完整过程的进度展示。



**使用场景**

1. 创建一个 Deployment 对象来生成对应的 Replica Set 并完成 POD 副本的创建过程。
2. 检查 Deployment 的状态来确认部署动作是否完成 （POD 副本的数量是否达到预期值）
3. 更新 Deployment 以创建新的 POD (例如镜像升级的场景)
4. 如果当前 Deployment 不稳定，回退到上一个 Deployment 版本。
5. 挂起或恢复一个 Deployment。

可以通过 `kubectl describe deployment ` 来查看 Deployment 控制的 POD的水平扩展过程。

```shell
[root@k8s-master-node1 ~]# kubectl describe deployment
Name:                   ingress-demo-app
Namespace:              default
CreationTimestamp:      Wed, 11 Jan 2023 09:35:45 +0800
Labels:                 app=ingress-demo-app
Annotations:            deployment.kubernetes.io/revision: 1
Selector:               app=ingress-demo-app
Replicas:               2 desired | 2 updated | 2 total | 2 available | 0 unavailable
StrategyType:           RollingUpdate
MinReadySeconds:        0
RollingUpdateStrategy:  25% max unavailable, 25% max surge
Pod Template:
  Labels:  app=ingress-demo-app
  Containers:
   whoami:
    Image:        traefik/whoami:v1.7.1
    Port:         80/TCP
    Host Port:    0/TCP
    Environment:  <none>
    Mounts:       <none>
  Volumes:        <none>
Conditions:
  Type           Status  Reason
  ----           ------  ------
  Progressing    True    NewReplicaSetAvailable
  Available      True    MinimumReplicasAvailable
OldReplicaSets:  <none>
NewReplicaSet:   ingress-demo-app-6c88bddcf8 (2/2 replicas created)
Events:
  Type    Reason             Age    From                   Message
  ----    ------             ----   ----                   -------
  Normal  ScalingReplicaSet  6h35m  deployment-controller  Scaled up replica set ingress-demo-app-6c88bddcf8 to 2

```



### 七 `HAP `

>  Horizontal Pod Autoscaler(HPA)即Pod横向自动扩容，与RC一样也属于k8s的资源对象。



HPA原理：通过追踪分析RC控制的所有目标Pod的负载变化情况，来确定是否针对性调整Pod的副本数。

POD 负载度量指标：

- `CPUUtilizationPercentage`: POD 所有副本自身的 CPU 利用率的平均值。 即当前 POD 的 CPU 使用量除以 POD Request 的值。
- 应用自定义的度量指标，比如服务每秒内响应的请求数 （TPS/QPS）。

###  八 `Service(服务)`

#### 1 概述

![service](https://res.cloudinary.com/dqxtn0ick/image/upload/v1510578930/article/kubernetes/concept/service.png)

Service 定义了一个服务的访问入口地址，前端应用通过这个入口地址访问其背后的一组由 POD 副本组成的集群实例，Service 与其后端 POD 副本集群之间是通过 `Label Selector ` 来实现 无缝对接。 RC 保证 Service 的 POD 副本实例数目保持预期水平。



#### 2 k8s 的服务发现机制

主要通过`kube-dns` 这个组件来进行DNS方式的服务发现。

#### 3 外部系统访问 Service 的问题

| IP类型     | 说明             |
| ---------- | ---------------- |
| Node IP    | Node节点的IP地址 |
| Pod IP     | Pod的IP地址      |
| Cluster IP | Service的IP地址  |



##### 3.1 `NODE IP`

Node IP 是集群中每个节点的物理网卡 IP 地址，是真实存在的物理网络，kubernetes 集群之外的节点访问 kubernetes 内的某个节点或 TCP/IP 服务的时候，需要通过 NODE IP 进行通信。

##### 3.2 `POD IP`

POD IP 是每个 POD 的 IP 地址，是 Docker Engine 根据 docker0 网桥的 IP 段地址进行分配的，是一个虚拟的二层网络，集群中一个 POD 的容器访问另一个 POD 中的容器，是通过 POD IP 进行通信的，而真实的 TCP/IP 流量是通过 `NODE IP`所在的网卡流出的。



#####  3.3 `Cluster IP`

1. Service 的 Cluster IP 是一个虚拟 IP , 只作用于 Service 这个对象，由kubernetes 管理和分配 IP 地址。(来源于 Cluster IP 地址池)
2. Cluster IP 无法被 ping  通，因为没有一个实体网络对象来响应。
3. Cluster IP 结合 Service Port 组成的具体通信端口才具备 TCP/IP 通信基础，属于kubernetes 集群内，集群外访问该IP和端口需要额外处理。
4. k8s 集群内Node IP、 Pod IP 、 Cluster IP 之间的通信采取 k8s 自己特殊的路由规则，与传统IP路由不同。

##### 3.4 外部访问kubernetes集群

通过宿主机与容器端口映射的方式进行访问，例如： Service 定位文件如下：`(原文就没有图)`

可以通过任意Node的IP 加端口访问该服务。也可以通过 Nginx 或 HaProxy 来设置负载均衡。

### 九 `Volume (存储卷)`

#### 1 Volume 的功能

1. Volume 是 POD 中能够被多个容器访问的共享目录，可以让容器的数据写到宿主机上或者写文件到网络存储中。
2. 可以实现容器配置文件集中化定义与管理，通过ConfigMap 资源对象来实现。



#### 2 Volume 的特点

k8s中的 Volume 与 Docker 的 Volume 相似，但不完成相同。

1. k8s 上 Volume 定义在 POD 上，然后被一个 POD 中的多个容器挂载到具体的文件目录下。
2. k8s 的 Volume 与 POD 生命周期相关而不是容器的生命周期， 即容器挂掉，数据不会丢失但是 POD 挂掉,数据则会丢失。 
3. k8s 中的 Volume 支持多种类型 Volume : Ceph、 GlusterFS 等分布式系统。

#### 3 Volume 的使用方式

先在 POD 声明一个 Volume, 然后容器引用该 Volume 并 Mount 到容器的某个目录。

#### 4 Volume 类型

##### 4.1 `emptyDir`

`emptyDri Volume` 是在 POD 分配到 NODE 是创建的，初始内容为空，无需指定宿主机上对应的目录文件，由 K8S 自动分配一个目录，当 POD 被删除时，对应的 emptyDir 数据也会永久删除。

***作用：***

1. 临时文件，例如程序的临时文件，无需永久保留。
2. 长时间任务的中间过程 CheckPoint 的临时保存目录。
3. 一个容器需要从另一个容器中获取数据的目录 ( 即多容器共享目录 )

##### 4.2 `hostPath`

hostPath 是在 POD 上挂载宿主机上的文件或目录。

***作用***

1. 容器应用日志需要持久化时，可以使用宿主机的高速文件系统进行存储
2. 需要访问宿主机上Docker引擎内部数据结构的容器应用时，可以通过定义hostPath为宿主机/var/lib/docker目录，使容器内部应用可以直接访问Docker的文件系统。

**注意点：**

1. 在不同的Node上具有相同配置的Pod可能会因为宿主机上的目录或文件不同导致对Volume上目录或文件的访问结果不一致。
2. 如果使用了资源配额管理，则kubernetes无法将hostPath在宿主机上使用的资源纳入管理。



##### 4.3 `gcePersistentDisk`

表示使用谷歌公有云提供的永久磁盘（Persistent Disk ,PD）存放Volume的数据，它与EmptyDir不同，PD上的内容会被永久保存。当Pod被删除时，PD只是被卸载时，但不会被删除。需要先创建一个永久磁盘，才能使用gcePersistentDisk。

使用gcePersistentDisk的限制条件：

- Node(运行kubelet的节点)需要是GCE虚拟机。
- 虚拟机需要与PD存在于相同的GCE项目中和Zone中。



### 十 `Persistent Volume`

Volume 定义在 POD 上，属于“计算资源” 的一部分， 而 `Persistent Volume` 和 `Persistent Volume Claim`是网络存储，简称 PV 和 PVC , 可以理解为 k8s 集群中某个网络存储中对应的一块存储。

- PV 是网络存储，不属于任何 Node,但可以在每个 Node 上访问。
- PV 不是定义在Pod上, 而是独立于 Pod 之外定义。
- PV 常见类型： `GCE Persistent Disks  、 NFS 、 RBD `等。

PV 是有状态的对象，状态类型如下：

- `Available`: 空闲状态。
- `Bound`: 已经绑定到某个PVC上。
- `Released`: 对应的 PVC 已经删除，但资源还没有回收。
- `Failed`: PV自动回收失败。

### 十一 `Namespace`

namespace 即命名空间，主要用于多租户的资源隔离，通过将资源对象分配到不同的 namespace 上，便于不同的分组在共享资源的同时可以被分别管理。

k8s 集群启动后会默认创建一个 `default`的 namespace。 可以通过 `kubectl get namespaces`查看。

可以通过 `kubectl config use-context namespace` 配置当前 k8s 客户端的环境，通过 `kubectl get pods ` 获取当前 namespace 的 POD 。 或者通过 `kubectl get pods --namespace = {namespace}`来获取指定 namespace 的 POD 。

 

### 十二 `Annotation (注解)`

Annotation 与 Label 类似，也使用 key/value 的形式进行定义，Label定义元数据 `Matadata`, Annotation 定义“附加”信息。

通常 Annotation 记录信息如下：

- build 信息，release 信息， Docker 镜像信息等。
- 日志库，监控库等。

