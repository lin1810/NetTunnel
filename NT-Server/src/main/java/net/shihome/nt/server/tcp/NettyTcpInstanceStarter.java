package net.shihome.nt.server.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.NettyRuntime;
import net.shihome.nt.comm.constants.NtConstant;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.netty.codec.NettyDataEntryDecoder;
import net.shihome.nt.comm.netty.codec.NettyDataEntryEncoder;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import net.shihome.nt.server.config.ServerInstance;
import net.shihome.nt.server.config.ServerProperties;
import net.shihome.nt.server.rpc.NettyManageServerStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyTcpInstanceStarter {

  private static final Logger logger = LoggerFactory.getLogger(NettyTcpInstanceStarter.class);
  private final List<EventLoopGroup> workGroupList = new ArrayList<>();
  private final List<Channel> channelList = new ArrayList<>();
  private final EventLoopGroup bossGroup;
  private final ServerProperties serverProperties;
  private final NettyManageServerStarter nettyServerStarter;
  private final GlobalTrafficShapingHandler globalTrafficShapingHandler;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;
  private ThreadPoolExecutor serverHandlerPool;

  public NettyTcpInstanceStarter(
      EventLoopGroup bossGroup,
      ServerProperties serverProperties,
      NettyManageServerStarter nettyServerStarter,
      GlobalTrafficShapingHandler globalTrafficShapingHandler,
      RpcResponseFutureHandler rpcResponseFutureHandler) {
    this.bossGroup = bossGroup;
    this.serverProperties = serverProperties;
    this.nettyServerStarter = nettyServerStarter;
    this.globalTrafficShapingHandler = globalTrafficShapingHandler;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
  }

  public void start() {

    List<ServerInstance> instanceList = serverProperties.getInstanceList();
    if (instanceList != null && instanceList.size() > 0) {
      serverHandlerPool =
          ThreadPoolUtil.makeServerThreadPool(
              ClassUtils.getShortName(NettyTcpInstanceStarter.class));
      for (ServerInstance instance : instanceList) {
        Thread thread =
            new Thread(
                () -> {
                  String instanceName = instance.getInstanceName();
                  int port = instance.getPort();
                  int workGroupThreads = instance.getWorkGroupThreads();
                  String address = instance.getAddress();
                  if (port <= 0) {
                    throw new NtException(
                        ExceptionLevelEnum.error, instanceName + " port is invalid ");
                  }

                  workGroupThreads =
                      workGroupThreads >= 0
                          ? workGroupThreads
                          : Math.max(NettyRuntime.availableProcessors() * 2, 4);

                  logger.info(
                      "instance[{}], workgroup thread count = {}", instanceName, workGroupThreads);
                  EventLoopGroup workerGroup =
                      new NioEventLoopGroup(
                          workGroupThreads,
                          new CustomizableThreadFactory(instanceName.toUpperCase() + "_WORK_"));
                  workGroupList.add(workerGroup);

                  try {
                    ServerBootstrap bootstrap = new ServerBootstrap();
                    bootstrap.group(bossGroup, workerGroup);
                    bootstrap.channel(NioServerSocketChannel.class);
                    bootstrap
                        .childHandler(
                            new ChannelInitializer<SocketChannel>() {
                              @Override
                              protected void initChannel(SocketChannel channel) {
                                channel
                                    .pipeline()
                                    .addLast(globalTrafficShapingHandler)
                                    .addLast(
                                        new IdleStateHandler(
                                            0, 0, NtConstant.INSTANCE_KEEP_ALIVE, TimeUnit.MINUTES))
                                    .addLast(new ChunkedWriteHandler())
                                    .addLast(new NettyDataEntryDecoder())
                                    .addLast(new NettyDataEntryEncoder())
                                    .addLast(
                                        new NettyTcpInstanceHandler(
                                            instanceName,
                                            serverHandlerPool,
                                            nettyServerStarter,
                                            instance,
                                            rpcResponseFutureHandler));
                              }
                            })
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                    ChannelFuture bind;
                    if (StringUtils.hasText(address)) {
                      bind = bootstrap.bind(address, port);
                    } else {
                      bind = bootstrap.bind(port);
                    }
                    bind.addListener(
                        (ChannelFutureListener)
                            f1 -> {
                              if (f1.isDone()) {
                                if (f1.isSuccess()) {
                                  channelList.add(f1.channel());
                                  logger.info(
                                      "tcp instance[{}] start success, ip = {}, port = {}",
                                      instanceName,
                                      address,
                                      port);
                                } else {
                                  logger.error(
                                      "tcp instance["
                                          + instanceName
                                          + "] start failed, ip = "
                                          + address
                                          + ", port = "
                                          + port
                                          + ", cause = "
                                          + f1.cause(),
                                      f1.cause());
                                }
                              }
                            });

                  } catch (Throwable throwable) {
                    logger.error("rpc remoting server error.", throwable);
                  }
                });
        thread.start();
      }
    } else {
      throw new NtException(ExceptionLevelEnum.error, "tcp instance config is undefined");
    }
  }

  public void stop() {
    logger.info("start to stop server");

    for (Channel channel : channelList) {
      try {
        channel.close();
      } catch (Throwable throwable) {
        logger.error("channel close exception caught " + channel, throwable);
      }
    }

    for (EventLoopGroup group : workGroupList) {
      try {
        group.shutdownGracefully();
      } catch (Throwable throwable) {
        logger.error("EventLoopGroup close exception caught " + group, throwable);
      }
    }

    try {
      if (bossGroup != null) {
        bossGroup.shutdownGracefully();
      }
    } catch (Throwable throwable) {
      logger.error("bossGroup close exception caught " + bossGroup, throwable);
    }

    try {
      serverHandlerPool.shutdown();
    } catch (Throwable throwable) {
      logger.error("serverHandlerPool shutdown exception caught", throwable);
    }
  }
}
