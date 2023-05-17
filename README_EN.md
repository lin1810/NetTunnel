# About Net Tunnel

Net Tunnel is an open source, safe, efficient, and stable intranet penetration and reverse proxy tool developed based on Netty, providing a solution for exposing intranet services without public IP.

Net Tunnel creates several encrypted long-connection tunnels between the public network and the intranet, and forwards public network requests to the service port on the intranet host to realize the exposure and sharing of intranet services.



[GitHub](https://github.com/lin1810/NetTunnel) [Gitee](https://gitee.com/lin1810/NetTunnel)

include:

- Server side: Deployed in the public network environment, exposing the internal network service port
- Client side: Deployed on the intranet for forwarding services



# How to use server side image

If you don’t have a cloud server, you can take a look at 

[Tencent Cloud 30~50 Yuan’s special offer server](https://cloud.tencent.com/act/cps/redirect?redirect=2446&cps_key=df09cb80975a46e4aeef9a07e6a5bf78&from=console)

[Alibaba Cloud Server 0 yuan trial first purchase as low as 0.9 yuan/month](https://www.aliyun.com/minisite/goods?userCode=69jhyszs)

By default, no thunnel instance is configured, additional ones can be added.

## Docker container

Run the following code on the server where the Docker environment is installed:

```bash
docker run -d --name nt-server \
    -v ./server_config:/NT/config \
    -p 9070:9070 \
    --restart unless-stopped \
    lyndonshi/net-tunnel-server:latest
```



## Docker-compose

Please make sure the server supports docker and docker-compose

1. Create docker-compose.yml file

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

2. Execution

```bash
docker-compose up -d
```



After the deployment of the above two methods is completed, a container named `nt-server` will be created, and configurations and certificates will be automatically generated in the `server_config` directory.

## Directory server_config 

| Documents        | Remarks                                                      |
| ---------------- | ------------------------------------------------------------ |
| application*.yml | Configuration file, no need to modify under normal circumstances, every restart will overwrite the configuration |
| ca.*             | Certificate Authority, it will be recreated every time it restarts if it does not exist, it needs to be consistent with the client's ca certificate |
| server.*         | Use the Server Side certificate signed by CA, if it does not exist, it will be recreated every time |
| client.*         | Use the client side certificate signed by CA, if it does not exist, it will be recreated every time |
| client_config    | Automatically generate client side configuration files       |


After startup, port 9070 is opened by default to receive connections from net-tunnel clients. Please refer to `docker`/`docker-compose` to expose `9070` port

## Create tunnel instance

### Configuration

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

Use `-i` to create the tunnel instance, The server needs at least one tunnel instance, otherwise it will fail to start.

The tunnel instance parameter `<name;port>` is a mandatory input option, where `name` is the tunnel name, and `port` is the port mapped on the server side.

### Examples

Create the following instance:

| Tunnel name | Port | Remarks                                                      |
| ----------- | ---- | ------------------------------------------------------------ |
| nginx       | 443  |                                                              |
| ssh         | 822  | Port 22 will be occupied by the host, please modify other ports instead |

> ⚠️Note to port exposing via `-p` or `ports`

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





# How to use client side image

By default, no tunnel instance is configured, additional ones can be added.

## Docker container

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

## Directory client_config 

Please copy all the files under `server_config/client_config` on the server side to `/NT/config` under the client container directory.

Includes:

| Name             | Remarks                                                      |
| ---------------- | ------------------------------------------------------------ |
| ca.crt           | Certificate Authority certificate, used to verify the server and establish a trusted tunnel |
| client.crt       | CA signed client certificate                                 |
| client_pkcs8.key | client side private key                                      |


The `client.crt` in the client must be signed by the ca certificate, otherwise the tunnel will fail to be created.

If the ca certificate of the server changes, please repeat the above copying steps.

## Configuration

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
### Add tunnel instance

Pass `-i` to create the tunnel instance. format: `<name;address:port>[;slidingWindowSize]`

`<name;address:port>` is a required input option

`name`: the name same with the instance of the server side

`address:port`: The address and port of the target service. For example, to map the ssh service of 192.168.0.2, the configuration should be `192.168.0.2:22`

### Set server address

Set the server address by `-s`. format: `<server address:port>`

### Examples

Create the following instance:

Server address: 172.17.0.2:9070

| Tunnel Name | IP Address  | Port |
| ----------- | ----------- | ---- |
| nginx       | 192.168.0.2 | 443  |
| ssh         | 192.168.0.3 | 22   |

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



# Note

- ⚠️http: The HTTP body is transmitted in clear text on the network, and there is a risk of being stolen and tampered with. It is recommended to enable the https protocol

- ⚠️ssh : To avoid brute force password attacks, it is recommended to disable root login and Certificate-based authentication




# license

Commercial use of this project is prohibited (including but not limited to post-build advertisements or sale of members, packaged and put on store shelves, etc.), in non-commercial cases follow the [MIT license](https://github.com/lin1810/NetTunnel/blob /main/LICENSE), when the two conflict, the non-commercial principle takes precedence.

# Issues

If you have any problems with or questions, please contact me through a [GitHub issue](https://github.com/lin1810/NetTunnel/issues).

