package net.shihome.nt.client.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import net.shihome.nt.client.config.ClientInstance;
import net.shihome.nt.client.config.ClientProperties;
import net.shihome.nt.client.rpc.NettyRpcClient;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.lang.SlidingWindowList;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcRequestTypeEnum;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.IdUtil;
import net.shihome.nt.comm.utils.IpUtil;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.comparator.Comparators;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public abstract class NettyTcpInstanceClient {
  public static final AttributeKey<NettyTcpInstanceClientContext> CONTEXT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(NettyTcpInstanceClient.class.getName() + "_CONTEXT_ATTRIBUTE_KEY");
  protected static final AttributeKey<ClientInstance> INSTANCE_CONFIG_KEY =
      AttributeKey.valueOf(NettyTcpInstanceClient.class.getName() + "_INSTANCE_KEY");
  private static final Logger logger = LoggerFactory.getLogger(NettyTcpInstanceClient.class);
  private static NettyTcpInstanceClient INSTANCE;
  private final ConcurrentMap<String, Channel> channelMap = new ConcurrentHashMap<>();
  private final ChannelHealthChecker healthCheck = ChannelHealthChecker.ACTIVE;
  private final ClientProperties clientProperties;
  private final NettyRpcClient nettyRpcClient;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;
  private AbstractChannelPoolMap<String, ChannelPool> poolMap;
  private EventLoopGroup group;
  private ThreadPoolExecutor serverHandlerPool;

  public NettyTcpInstanceClient(
      ClientProperties clientProperties,
      NettyRpcClient nettyRpcClient,
      RpcResponseFutureHandler rpcResponseFutureHandler) {
    this.clientProperties = clientProperties;
    this.nettyRpcClient = nettyRpcClient;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
    INSTANCE = this;
  }

  public static NettyTcpInstanceClient getInstance() {
    return INSTANCE;
  }

  public void start() {

    serverHandlerPool =
        ThreadPoolUtil.makeServerThreadPool(ClassUtils.getShortName(NettyTcpInstanceClient.class));
    int workGroupProcess = clientProperties.getWorkGroupThreads();
    if (workGroupProcess <= 0) {
      workGroupProcess = Math.max(NettyRuntime.availableProcessors() * 2, 4);
    }

    logger.info("TCP CLIENT WORKER GROUP = {}", workGroupProcess);
    this.group =
        new NioEventLoopGroup(workGroupProcess, new CustomizableThreadFactory("TCP_WORK_"));
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

    poolMap =
        new AbstractChannelPoolMap<String, ChannelPool>() {
          @Override
          protected ChannelPool newPool(String instanceName) {
            ClientInstance clientInstance =
                clientProperties.getInstanceList().stream()
                    .filter(cl -> Objects.equals(cl.getInstanceName(), instanceName))
                    .findAny()
                    .orElse(null);

            if (clientInstance == null) {
              throw new NtException(
                  ExceptionLevelEnum.error, "undefined instance name:" + instanceName);
            }

            Object[] array = IpUtil.parseIpPort(clientInstance.getAddress());
            String host = (String) array[0];
            int port = (int) array[1];

            return new FixedChannelPool(
                bootstrap.remoteAddress(host, port),
                new NettyTcpInstanceClientChannelPoolHandler(
                    clientInstance, serverHandlerPool, nettyRpcClient, rpcResponseFutureHandler),
                clientProperties.getMaxTcpInstanceConnections());
          }
        };
  }

  public Channel getChannel(String channelId) {
    return channelMap.get(channelId);
  }

  protected Channel putAndGetChannel(String channelId, Channel channel) {
    Channel previousChannel = channelMap.putIfAbsent(channelId, channel);
    channel = channelMap.get(channelId);
    if (previousChannel == null) {
      ClientInstance instance = channel.attr(NettyTcpInstanceClient.INSTANCE_CONFIG_KEY).get();
      NettyTcpInstanceClientContext instanceClientContext = new NettyTcpInstanceClientContext();
      instanceClientContext.setChannelId(channelId);
      int permits = instance.getPermits();
      if (permits > 0) {
        instanceClientContext.setSemaphore(new Semaphore(permits, true));
      }
      int slidingWindowSize = instance.getSlidingWindowSize();
      if (slidingWindowSize <= 0) {
        throw new NtException(ExceptionLevelEnum.error, "slidingWindowSize is invalid");
      }
      instanceClientContext.setSlidingWindowList(new SlidingWindowList(slidingWindowSize));
      channel.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).setIfAbsent(instanceClientContext);
      channel
          .closeFuture()
          .addListener(
              (ChannelFutureListener)
                  channelFuture -> {
                    Channel channelPendingToClose = channelFuture.channel();
                    closeAndRelease(instance.getInstanceName(), channelId, channelPendingToClose);
                  });
    } else {
      logger.warn(
          "duplicate channel[{}], new channel[{}], previous channel[{}]",
          channelId,
          channel,
          previousChannel);
    }
    return channel;
  }

  public Future<Void> asyncSend(DataEntry dataEntry) {
    Promise<Void> asyncSendFuture = group.next().newPromise();
    String channelId = dataEntry.getChannelId();
    String instanceName = dataEntry.getInstanceName();
    Channel channel = getChannel(channelId);
    boolean close = dataEntry.isClose();
    if (channel == null && close) {
      asyncSendFuture.setFailure(
          new NtException(ExceptionLevelEnum.info, "receive close request and channel is closed"));
      return asyncSendFuture;
    } else if (channel == null && dataEntry.getSpanId() > 0) {
      asyncSendFuture.setFailure(
          new NtException(ExceptionLevelEnum.info, "channel id[" + channelId + "] is close"));
      return asyncSendFuture;
    }
    if (channel == null) {
      ChannelPool channelPool = poolMap.get(instanceName);
      Future<Channel> acquire = channelPool.acquire();
      acquire.addListener(
          (FutureListener<Channel>)
              f1 -> {
                if (f1.isDone()) {
                  if (f1.isSuccess()) {
                    Channel channel1 = f1.get();
                    channel1 = putAndGetChannel(channelId, channel1);
                    asyncSend0(channel1, dataEntry, asyncSendFuture);
                  } else {
                    logger.warn(
                        "acquire channel failed, instanceName:{}, cause:{}",
                        instanceName,
                        f1.cause().toString());
                    asyncSendFuture.tryFailure(f1.cause());
                  }
                }
              });
    } else {
      Future<Boolean> healthy = healthCheck.isHealthy(channel);
      healthy.addListener(
          (FutureListener<Boolean>)
              f1 -> {
                if (f1.isDone()) {
                  if (f1.isSuccess()) {
                    asyncSend0(channel, dataEntry, asyncSendFuture);
                  } else {
                    logger.warn(instanceName + " is unhealthy, cause:{}", f1.cause().toString());
                    asyncSendFuture.tryFailure(f1.cause());
                    closeAndRelease(instanceName, channelId, channel);
                  }
                }
              });
    }
    return asyncSendFuture;
  }

  public void asyncSend0(Channel channel, DataEntry dataEntry, Promise<Void> asyncSendFuture) {
    try {
      NettyTcpInstanceClientContext nettyTcpInstanceClientContext =
          channel.attr(CONTEXT_ATTRIBUTE_KEY).get();
      if (nettyTcpInstanceClientContext == null) {
        throw new NtException(
            ExceptionLevelEnum.warn,
            "asyncSend0->current channel context is null, channel is active?: "
                + channel.isActive()
                + " isRegistered?: "
                + channel.isRegistered());
      }
      String channelId = nettyTcpInstanceClientContext.getChannelId();
      ConcurrentSkipListSet<DataEntry> dataEntries =
          nettyTcpInstanceClientContext.getPendingToSendSet();
      dataEntries.add(dataEntry);
      AtomicLong currentSpanIdAtomicLong = nettyTcpInstanceClientContext.getCurrentRequestSpanId();
      synchronized (channelId) {
        while (!dataEntries.isEmpty()) {
          DataEntry pendSending = dataEntries.pollFirst();
          if (pendSending != null) {
            if (Objects.equals(currentSpanIdAtomicLong.get(), pendSending.getSpanId())) {
              if (pendSending.isClose()) {
                logger.debug("receive close data entry, channel id:{}", channelId);
                channel.close();
                break;
              } else {
                channel.writeAndFlush(pendSending);
                logger.debug(
                    "write and flush, channel id:{}, current index:{}, pending size:{}",
                    channelId,
                    pendSending.getSpanId(),
                    dataEntries.size());
              }
              currentSpanIdAtomicLong.getAndIncrement();
            } else {
              dataEntries.add(pendSending);
              logger.debug(
                  "add new pending data index:{}, channel id:{}, top stack index:{}, pending index:{}, pending size:{}, close:{}",
                  dataEntry.getSpanId(),
                  channelId,
                  pendSending.getSpanId(),
                  currentSpanIdAtomicLong.get(),
                  dataEntries.size(),
                  pendSending.isClose());
              break;
            }
          }
        }
      }

      asyncSendFuture.trySuccess(null);
    } catch (Throwable throwable) {
      asyncSendFuture.tryFailure(throwable);
    }
  }

  public void closeAndRelease(String instanceName, String channelId, Channel channel) {
    channelMap.remove(channelId);
    closeRemoteConnection(instanceName, channelId, channel);
    if (channel != null) {
      NettyTcpInstanceClientContext context =
          channel.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).getAndSet(null);
      if (context != null) {
        context.clear();
      }
      poolMap.get(instanceName).release(channel);
    }
  }

  public void closeRemoteConnection(String instanceName, String channelId, Channel channel) {
    RpcRequest rpcRequest = new RpcRequest();
    rpcRequest.setRequestId(IdUtil.getNextId());
    rpcRequest.setRequestType(RpcRequestTypeEnum.close);
    rpcRequest.setChannelId(channelId);
    NettyTcpInstanceClientContext nettyTcpInstanceClientContext = null;
    if (channel != null) {
      nettyTcpInstanceClientContext =
          channel.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).get();
    }
    if (nettyTcpInstanceClientContext != null) {
      rpcRequest.setRequestType(RpcRequestTypeEnum.data);
      DataEntry msg = new DataEntry();
      String id = nettyTcpInstanceClientContext.getChannelId();
      AtomicLong atomicLong = nettyTcpInstanceClientContext.getCurrentResponseSpanId();
      msg.setChannelId(id);
      msg.setInstanceName(instanceName);
      msg.setSpanId(atomicLong.getAndIncrement());
      msg.setClose(true);
      rpcRequest.setData(msg);
    } else {
      logger.warn("close channel context is null, channel:{}", channel);
    }
    Future<Void> asyncSendFuture = nettyRpcClient.asyncSend(rpcRequest);
    asyncSendFuture.addListener(
        (FutureListener<Void>)
            f1 -> {
              if (f1.isDone() && !f1.isSuccess()) {
                logger.info("close remote connection failed, cause:{}", f1.cause().toString());
              }
            });
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
}
