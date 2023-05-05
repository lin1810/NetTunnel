package net.shihome.nt.client.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import net.shihome.nt.client.config.ClientProperties;
import net.shihome.nt.client.service.ClientService;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.netty.pool.IdleSchedulerChannelPoolPool;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.service.RpcSerializer;
import net.shihome.nt.comm.utils.IpUtil;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadPoolExecutor;

public class NettyRpcClient {

  private static final Logger logger = LoggerFactory.getLogger(NettyRpcClient.class);
  private final RpcSerializer rpcSerializer;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;
  private final ClientProperties clientProperties;
  private final ClientService clientService;
  private AbstractChannelPoolMap<String, ChannelPool> poolMap;
  private EventLoopGroup group;
  private ThreadPoolExecutor serverHandlerPool;

  public NettyRpcClient(
      RpcSerializer rpcSerializer,
      RpcResponseFutureHandler rpcResponseFutureHandler,
      ClientProperties clientProperties,
      ClientService clientService) {
    this.rpcSerializer = rpcSerializer;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
    this.clientProperties = clientProperties;
    this.clientService = clientService;
  }

  public void start() {

    int workGroupProcess = clientProperties.getWorkGroupThreads();
    if (workGroupProcess <= 0) {
      workGroupProcess = Math.max(NettyRuntime.availableProcessors() * 2, 4);
    }
    this.group =
        new NioEventLoopGroup(workGroupProcess, new CustomizableThreadFactory("RPC_WORK_"));
    logger.info("RPC CLIENT WORKER GROUP = {}", workGroupProcess);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
    serverHandlerPool =
        ThreadPoolUtil.makeServerThreadPool(ClassUtils.getShortName(NettyRpcClient.class));

    poolMap =
        new AbstractChannelPoolMap<String, ChannelPool>() {
          @Override
          protected ChannelPool newPool(String address) {
            Object[] array = IpUtil.parseIpPort(address);
            String host = (String) array[0];
            int port = (int) array[1];

            return new IdleSchedulerChannelPoolPool(
                bootstrap.remoteAddress(host, port),
                new NettyClientChannelPoolHandler(
                    rpcSerializer,
                    rpcResponseFutureHandler,
                    serverHandlerPool,
                    clientService,
                    clientProperties),
                clientProperties.getMaxConnectionInPool(),
                clientProperties.getMinConnectionInPool(),
                clientProperties.getSchedulerMinIdleCheckPerSecond(),
                clientProperties.getSchedulerHealthcarePerSecond(),
                clientProperties.isSchedulerHealthCheck());
          }
        };
    String serverAddress = clientProperties.getServerAddress();
    if (StringUtils.hasText(serverAddress)) {
      poolMap.get(serverAddress);
    } else {
      throw new NtException(ExceptionLevelEnum.error, "server address is empty");
    }
  }

  public void stop() {
    try {
      poolMap.close();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    try {
      group.shutdownGracefully();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    try {
      serverHandlerPool.shutdown();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  public Future<Void> asyncSend(RpcRequest rpcRequest) {
    Promise<Void> future = group.next().newPromise();
    String serverAddress = clientProperties.getServerAddress();
    ChannelPool channelPool = poolMap.get(serverAddress);
    channelPool
        .acquire()
        .addListener(
            (FutureListener<Channel>)
                f1 -> {
                  if (f1.isSuccess()) {
                    Channel ch = f1.getNow();
                    try {
                      ch.writeAndFlush(rpcRequest);
                    } finally {
                      channelPool.release(ch);
                    }
                    future.trySuccess(null);
                  } else {
                    future.tryFailure(f1.cause());
                  }
                });
    return future;
  }
}
