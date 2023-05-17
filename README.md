# 关于 Net Tunnel

Net Tunnel 是基于 Netty 开发，开源，安全，高效，稳定的内网穿透和反向代理工具，提供没有公网IP希望暴露内网服务的解决方案。

Net Tunnel 通过公网和内网之间创建数个加密的长连接隧道，将公网请求转发到内网主机上服务端口，实现内网服务的暴露和共享。



[GitHub](https://github.com/lin1810/NetTunnel)  [Gitee](https://gitee.com/lin1810/NetTunnel)

包括：

- 服务器端：部署在公网环境，暴露内网服务端口
- 客户端：部署在内网，用于转发服务



# 如何使用服务端镜像

如果您没有云服务器，可以看看[腾讯云 30~50元首单的特价服务器](https://cloud.tencent.com/act/cps/redirect?redirect=2446&cps_key=df09cb80975a46e4aeef9a07e6a5bf78&from=console)，[阿里云 服务器0元试用 首购低至0.9元/月起](https://www.aliyun.com/minisite/goods?userCode=69jhyszs)

默认情况下没有配置通道实例，可以在后续增加。

## Docker 单容器部署

在安装了 Docker 环境的服务器运行如下代码：

```bash
docker run -d --name nt-server \
    -v ./server_config:/NT/config \
    -p 9070:9070 \
    --restart unless-stopped \
    lyndonshi/net-tunnel-server:latest
```



## Docker-compose

请确保服务器支持docker和docker-compose

1. 创建 docker-compose.yml 文件

```yaml
version: "3.2"
services:
  nt-server:
    image: lyndonshi/net-tunnel-server:latest
    container_name: nt-server
    volumes:
      - ${PWD}/server_config:/NT/config
    ports:
      - "9070:9070"
    restart: unless-stopped
```

2. 执行

```bash
docker-compose up -d
```



以上两种方式部署完成后，将会创建名为`nt-server`的容器，并自动在 `server_config` 目录下生成配置和证书。

## server_config 目录结构

| 文件             | 备注                                                         |
| ---------------- | ------------------------------------------------------------ |
| application*.yml | 配置文件，正常情况下无需修改，每次重启都会覆盖配置           |
| ca.*             | Certificate Authority 证书，每次重启若不存在则会重新创建，需要和客户端的 ca 证书保持一致 |
| server.*         | 使用 CA 签名后的Server Side 证书，每次重启若不存在则会重新创建 |
| client.*         | 使用 CA 签名后的Client Side 证书，每次重启若不存在则会重新创建 |
| client_config    | 自动生成client side 的配置文件                               |

启动后默认开启9070端口，用于接收来着net-tunnel 客户端的连接。请参考 `docker`/`docker-compose` 暴露`9070`端口

## 创建通道实例

### 配置项

```bash
$ docker run --rm -it lyndonshi/net-tunnel-server -h
Usage: entrypoint.sh [-opt] [command]
Options (fields in '[]' are optional, '<>' are required):
    -h          This help
    -i "<name;port>[;slidingWindowSize]"
                Configure a tunnel
                required arg: "<name>;<port>"
                <name> tunnel name
                <port> tunnel bind port
                NOTE: for the default value, just leave blank
                [slidingWindowSize] default:'10'
```

使用`-i`增加通道实例，服务器需要至少1个通道实例，否则会启动失败。

通道实例参数`<name;port>`为必须输入选项，其中 `name` 为通道名称，`port` 为映射在服务端的端口。

### 例子

创建如下实例：

| 通道实例名 | 端口 | 备注                                     |
| ---------- | ---- | ---------------------------------------- |
| nginx      | 443  |                                          |
| ssh        | 822  | 22端口会被宿主机占用，请修改其他端口代替 |

> ⚠️留意通过`-p` 或 `ports`  映射端口 


#### Docker

```bash
docker run -d --name nt-server \
    -v ./server_config:/NT/config \
    -p 9070:9070 -p 443:443 -p 822:822 \
    --restart unless-stopped \
    lyndonshi/net-tunnel-server:latest \
    -i "nginx;443" -i "ssh;822"
```

#### Docker-compose

```yaml
version: "3.2"
services:
  nt-server:
    image: lyndonshi/net-tunnel-server:latest
    container_name: nt-server
    volumes:
      - ${PWD}/server_config:/NT/config
    ports:
      - "9070:9070"
      - "443:443"
      - "822:822"
    restart: unless-stopped
    command: ["-i", "nginx;443", "-i", "ssh;822"]
```





# 如何使用客户端镜像

默认情况下没有配置通道实例，可以在后续增加。

## Docker 单容器部署

```bash
docker run -d --name nt-client \
    -v ./client_config:/NT/config \
    --restart unless-stopped \
    lyndonshi/net-tunnel-client:latest
```

## Docker-compose

```yaml
version: "3.2"
services:
  nt-client:
    image: lyndonshi/net-tunnel-client:latest
    container_name: nt-client
    volumes:
      - ${PWD}/client_config:/NT/config
    restart: unless-stopped
```

## client_config 目录结构

请拷贝服务端 `server_config/client_config`下的所有文件到客户端容器目录`/NT/config`下。

文件包括：

| 文件             | 备注                                                         |
| ---------------- | ------------------------------------------------------------ |
| ca.crt           | Certificate Authority 证书，用于验证服务端，建立可信的通道 |
| client.crt       | CA 签署过的客户端证书                                        |
| client_pkcs8.key | 客户端private key                                            |

客户端中的`client.crt` 必须通过 ca 证书签署过，否则通道将创建失败。

若服务器的 ca 证书发生变化，请重复以上拷贝步骤。

## 配置

```bash
docker run --rm -it lyndonshi/net-tunnel-client -h
Usage: entrypoint.sh [-opt] [command]
Options (fields in '[]' are optional, '<>' are required):
    -h          This help
    -i "<name;address:port>[;slidingWindowSize]"
                Configure a tunnel
                required arg: "<name>;<address:port>"
                <name> tunnel name
                <address:port> target service IP address and port
                NOTE: for the default value, just leave blank
                [slidingWindowSize] default:'10'
    -s "<server address:port>"
                required arg: "<server address:port>"
                NOTE: for the default value, just leave blank
                <server address:port> server side IP address and port default:'127.0.0.1:9070'
```

### 增加通道实例

通过`-i`增加通道实例。参数格式：`<name;address:port>[;slidingWindowSize]`

其中`<name;address:port>`为必须输入选项

`name`:服务端的实例一致

`address:port`:映射服务的地址和端口。例如映射192.168.0.2的 ssh 服务，则配置应该是`192.168.0.2:22`

### 设置服务器配置

通过`-s`设置服务器地址。参数格式：`<server address:port>`

### 例子

创建如下实例：

服务端地址：172.17.0.2:9070

| 通道实例名 | IP 地址     | 端口 |
| ---------- | ----------- | ---- |
| nginx      | 192.168.0.2 | 443  |
| ssh        | 192.168.0.3 | 22   |

#### Docker

```bash
docker run -d --name nt-client \
    -v ./client_config:/NT/config \
    --restart unless-stopped \
    lyndonshi/net-tunnel-client:latest \
    -i "nginx;192.168.0.2:443" -i "ssh;192.168.0.3:22" -s "172.17.0.2:9070"
```

#### Docker-compose

```yaml
version: "3.2"
services:
  nt-client:
    image: lyndonshi/net-tunnel-client:latest
    container_name: nt-client
    volumes:
      - ${PWD}/client_config:/NT/config
    restart: unless-stopped
    command:
      - "-i"
      - "nginx;192.168.0.2:443"
      - "-i"
      - "ssh;192.168.0.3:22"
      - "-s"
      - "172.17.0.2:9070"
```



# 注意事项

- ⚠️http ：报文在网络中是明文传输的，有被窃取篡改的风险，建议启用 https

- ⚠️ssh ：避免暴力破解，建议禁用 root 登录，并使用 SSH 密钥连接




# 授权

本项目禁止商用（包括但不限于搭建后挂广告或售卖会员、打包后上架商店销售等），在非商用的情况下遵循[MIT license](https://github.com/lin1810/NetTunnel/blob/main/LICENSE)，当两者冲突时，以非商用原则优先。



# 问题

如果您对此有任何问题或疑问，请与我联系  [GitHub issue](https://github.com/lin1810/NetTunnel/issues)。

