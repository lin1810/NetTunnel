package net.shihome.nt.server.rpc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.Future;
import jakarta.annotation.Resource;
import net.shihome.nt.comm.constants.NtConstant;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.netty.codec.NettyCodecTypeEnum;
import net.shihome.nt.comm.netty.codec.NettyObjectDecoder;
import net.shihome.nt.comm.netty.codec.NettyObjectEncoder;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.service.RpcSerializer;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import net.shihome.nt.server.common.IpRegionFilter;
import net.shihome.nt.server.common.IpRegionUtils;
import net.shihome.nt.server.config.ServerProperties;
import net.shihome.nt.server.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NettyManageServerStarter {

  private static final Logger logger = LoggerFactory.getLogger(NettyManageServerStarter.class);

  private final AtomicReference<Channel> channelAtomicReference = new AtomicReference<>();
  private final ServerService serverService;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;
  private final EventLoopGroup bossGroup;
  private Thread thread;
  @Resource
  private RpcSerializer rpcSerializer;
  @Resource
  private ServerProperties serverProperties;
  @Resource
  private GlobalTrafficShapingHandler globalTrafficShapingHandler;
  @Resource
  private IpRegionUtils ipRegionUtils;
  private NettyManageServerRequestHandler nettyManageServerRequestHandler;

  public NettyManageServerStarter(
      ServerService serverService,
      RpcResponseFutureHandler rpcResponseFutureHandler,
      EventLoopGroup bossGroup) {
    this.serverService = serverService;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
    this.bossGroup = bossGroup;
  }

  public void start() {

    thread =
        new Thread(
            () -> {
              final ThreadPoolExecutor serverHandlerPool =
                  ThreadPoolUtil.makeServerThreadPool(
                      ClassUtils.getShortName(NettyManageServerStarter.class));

              int workGroupProcess = Math.max(NettyRuntime.availableProcessors() * 2, 4);
              EventLoopGroup workerGroup =
                  new NioEventLoopGroup(
                      workGroupProcess, new CustomizableThreadFactory("RPC_WORK_"));

              logger.info("RPC thread count = {}", workGroupProcess);

              try {
                // start server
                ServerBootstrap bootstrap = new ServerBootstrap();
                nettyManageServerRequestHandler =
                    new NettyManageServerRequestHandler(bootstrap, rpcResponseFutureHandler);
                Map<NettyCodecTypeEnum, Class<?>> decodeMap = new HashMap<>();
                decodeMap.put(NettyCodecTypeEnum.client, RpcRequest.class);
                decodeMap.put(NettyCodecTypeEnum.server, RpcResponse.class);
                bootstrap
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(
                        new ChannelInitializer<SocketChannel>() {
                          @Override
                          public void initChannel(SocketChannel channel) throws Exception {
                            channel
                                .pipeline()
                                .addLast(new IpRegionFilter(ipRegionUtils, serverProperties.getAccessIpRegion()))
                                .addLast(globalTrafficShapingHandler)
                                .addLast(
                                    new IdleStateHandler(
                                        0,
                                        0,
                                        NtConstant.BEAT_HEAT_PER_MINUTES_SERVER,
                                        TimeUnit.MINUTES))
                                .addLast(new ChunkedWriteHandler())
                                .addLast(
                                    new NettyObjectEncoder(
                                        RpcResponse.class,
                                        rpcSerializer,
                                        NettyCodecTypeEnum.client))
                                .addLast(
                                    new NettyObjectEncoder(
                                        RpcRequest.class, rpcSerializer, NettyCodecTypeEnum.server))
                                .addLast(new NettyObjectDecoder(rpcSerializer, decodeMap))
                                .addLast(
                                    new NettyManageServerHandler(serverHandlerPool, serverService))
                                .addLast(nettyManageServerRequestHandler);
                            File keyFile = serverProperties.getServerKeyPath();
                            File certFile = serverProperties.getServerCertPath();
                            File caFile = serverProperties.getCaPath();
                            String serverKeyPassword = serverProperties.getServerKeyPassword();

                            if (certFile != null && keyFile != null && caFile != null) {
                              SslContext nettySslContext =
                                  SslContextBuilder.forServer(certFile, keyFile, serverKeyPassword)
                                      .trustManager(caFile)
                                      .clientAuth(ClientAuth.REQUIRE)
                                      .build();
                              channel
                                  .pipeline()
                                  .addFirst(nettySslContext.newHandler(channel.alloc()));
                            } else {
                              logger.warn(
                                  "current channel is un-safe, It is strongly recommended to enable SSL/TLS channel authentication");
                            }
                          }
                        })
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

                // bind
                String ipAddress = serverProperties.getIp();
                int ipPort = serverProperties.getPort();
                ChannelFuture bind = bootstrap.bind(ipAddress, ipPort);

                Channel channel = bind.channel();
                channelAtomicReference.set(channel);
                ChannelFuture future = bind.sync();

                logger.info("RPC server start success, ip = {}, port = {}", ipAddress, ipPort);

                // wait util stop
                future.channel().closeFuture().sync();

              } catch (Exception e) {
                if (e instanceof InterruptedException) {
                  logger.info("rpc remoting server stop.");
                } else {
                  logger.error("rpc remoting server error.", e);
                }
              } finally {
                // stop
                try {
                  serverHandlerPool.shutdown();
                } catch (Exception e) {
                  logger.error(e.getMessage(), e);
                }
                try {
                  workerGroup.shutdownGracefully();
                  bossGroup.shutdownGracefully();
                } catch (Exception e) {
                  logger.error(e.getMessage(), e);
                }
              }
            });
    thread.start();
  }

  public void stop() throws InterruptedException {
    logger.info("start to stop server");
    if (thread != null && thread.isAlive()) {
      Channel channel = channelAtomicReference.get();
      ChannelFuture closeFuture = channel.close();
      closeFuture.sync();
    }

    logger.info("rpc remoting server destroy success.");
  }

  public Future<Void> asyncSend(RpcRequest rpcRequest) {
    return nettyManageServerRequestHandler.asyncSend(rpcRequest);
  }
}
