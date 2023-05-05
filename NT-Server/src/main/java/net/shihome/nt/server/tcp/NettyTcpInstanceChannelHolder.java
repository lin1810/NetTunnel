package net.shihome.nt.server.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcRequestTypeEnum;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.utils.IdUtil;
import net.shihome.nt.server.rpc.NettyManageServerStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

public class NettyTcpInstanceChannelHolder {

  private static final Logger logger = LoggerFactory.getLogger(NettyTcpInstanceChannelHolder.class);

  private static NettyTcpInstanceChannelHolder INSTANCE;
  private final ConcurrentMap<String, Channel> regMap = new ConcurrentHashMap<>();
  private final ChannelHealthChecker healthCheck = ChannelHealthChecker.ACTIVE;
  private EventLoopGroup workerGroup;

  @Resource private NettyManageServerStarter nettyServerStarter;

  protected NettyTcpInstanceChannelHolder() {
    INSTANCE = this;
  }

  public static NettyTcpInstanceChannelHolder getInstance() {
    return INSTANCE;
  }

  public void start() {
    int workGroupProcess = Math.max(NettyRuntime.availableProcessors() * 2, 4);
    workerGroup =
        new NioEventLoopGroup(workGroupProcess, new CustomizableThreadFactory("TCP_INST_PRO_"));
    logger.info("TCP INSTANCE PROMISE GROUP= {}", workGroupProcess);
  }

  public void stop() {
    try {
      workerGroup.shutdownGracefully();
    } catch (Throwable throwable) {
      logger.error("NettyTcpInstanceChannelHolder workgroup shutdown failed", throwable);
    }
  }

  public Future<Void> asyncSend(DataEntry dataEntry) {
    Promise<Void> asyncSendFuture = workerGroup.next().newPromise();

    String channelId = dataEntry.getChannelId();
    Future<Channel> channelFuture = getChannel(channelId);
    channelFuture.addListener(
        (FutureListener<Channel>)
            f1 -> {
              if (f1.isDone()) {
                if (f1.isSuccess()) {
                  asyncSend0(f1.get(), dataEntry);
                  asyncSendFuture.trySuccess(null);
                } else {
                  asyncSendFuture.tryFailure(f1.cause());
                }
              }
            });

    return asyncSendFuture;
  }

  public void asyncSend0(Channel channel, DataEntry dataEntry) {
    NettyTcpInstanceContext nettyTcpInstanceContext =
        channel.attr(NettyTcpInstanceHandler.CONTEXT_ATTRIBUTE_KEY).get();
    if (nettyTcpInstanceContext == null) {
      return;
    }
    String channelId = nettyTcpInstanceContext.getChannelId();
    ConcurrentSkipListSet<DataEntry> dataEntries = nettyTcpInstanceContext.getPendingToSendSet();
    dataEntries.add(dataEntry);
    AtomicLong currentSpanIdAtomicLong = nettyTcpInstanceContext.getCurrentResponseSpanId();
    synchronized (channelId) {
      while (!dataEntries.isEmpty()) {
        DataEntry pendSending = dataEntries.pollFirst();
        if (pendSending != null) {
          if (Objects.equals(currentSpanIdAtomicLong.get(), pendSending.getSpanId())) {
            if (pendSending.isClose()) {
              channel.close();
              break;
            } else {
              channel.writeAndFlush(pendSending);
            }
            currentSpanIdAtomicLong.getAndIncrement();
          } else {
            dataEntries.add(pendSending);
            break;
          }
        }
      }
    }
  }

  public Future<Channel> getChannel(String id) {
    return getChannel(id, workerGroup.next().newPromise());
  }

  public Future<Channel> getChannel(String id, final Promise<Channel> promise) {
    return acquireHealthyFromPool(id, ObjectUtil.checkNotNull(promise, "promise"));
  }

  private Future<Channel> acquireHealthyFromPool(String id, final Promise<Channel> promise) {
    try {
      Channel ch = regMap.get(id);
      if (ch != null) {
        EventLoop loop = ch.eventLoop();
        if (loop.inEventLoop()) {
          doHealthCheck(ch, promise);
        } else {
          loop.execute(() -> doHealthCheck(ch, promise));
        }
      } else {
        promise.tryFailure(
            new NtException(
                ExceptionLevelEnum.warn, "Registered channel is missing, channel id=" + id));
      }
    } catch (Throwable cause) {
      promise.tryFailure(cause);
    }
    return promise;
  }

  private void doHealthCheck(final Channel channel, final Promise<Channel> promise) {
    try {
      assert channel.eventLoop().inEventLoop();
      Future<Boolean> f = healthCheck.isHealthy(channel);
      if (f.isDone()) {
        notifyHealthCheck(f, channel, promise);
      } else {
        f.addListener(
            (FutureListener<Boolean>) future -> notifyHealthCheck(future, channel, promise));
      }
    } catch (Throwable cause) {
      closeAndFail(channel, cause, promise);
    }
  }

  private void notifyHealthCheck(
      Future<Boolean> future, Channel channel, Promise<Channel> promise) {
    try {
      assert channel.eventLoop().inEventLoop();
      if (future.isSuccess() && future.getNow()) {
        promise.setSuccess(channel);
      } else {
        closeAndFail(
            channel, new NtException(ExceptionLevelEnum.info, "channel is unhealthy"), promise);
      }
    } catch (Throwable cause) {
      closeAndFail(channel, cause, promise);
    }
  }

  private void closeAndFail(Channel channel, Throwable cause, Promise<?> promise) {
    if (channel != null) {
      try {
        closeChannel(channel);
      } catch (Throwable t) {
        promise.tryFailure(t);
      }
    }
    promise.tryFailure(cause);
  }

  private void closeChannel(Channel channel) {
    if (logger.isInfoEnabled()) {
      NettyTcpInstanceContext nettyTcpInstanceContext =
          channel.attr(NettyTcpInstanceHandler.CONTEXT_ATTRIBUTE_KEY).get();
      String channelId =
          nettyTcpInstanceContext != null ? nettyTcpInstanceContext.getChannelId() : null;
      logger.info("close id[{}] channel[{}]", channelId, channel);
    }
    channel.close();
  }

  public String channelRegistered(Channel channel) {
    String channelId = IdUtil.getNextId();
    channel
        .closeFuture()
        .addListener((ChannelFutureListener) channelFuture -> closeAndRelease(channel, channelId));
    regMap.put(channelId, channel);
    logger.info("register channel with id[{}], channel[{}]", channelId, channel);
    return channelId;
  }

  protected void closeAndRelease(Channel channel, String channelId) {
    logger.debug("close channel id[{}]", channelId);
    closeRemoteConnection(channel, channelId);
    channelUnregistered(channelId);
  }

  protected void closeRemoteConnection(Channel channel, String channelId) {
    NettyTcpInstanceContext nettyTcpInstanceContext =
        channel.attr(NettyTcpInstanceHandler.CONTEXT_ATTRIBUTE_KEY).get();
    RpcRequest rpcRequest = new RpcRequest();
    rpcRequest.setRequestId(IdUtil.getNextId());
    rpcRequest.setRequestType(RpcRequestTypeEnum.close);
    rpcRequest.setChannelId(channelId);
    if (nettyTcpInstanceContext != null) {
      rpcRequest.setRequestType(RpcRequestTypeEnum.data);
      DataEntry msg = new DataEntry();
      String id = nettyTcpInstanceContext.getChannelId();
      AtomicLong atomicLong = nettyTcpInstanceContext.getCurrentRequestSpanId();
      msg.setChannelId(id);
      msg.setInstanceName(nettyTcpInstanceContext.getInstanceName());
      msg.setSpanId(atomicLong.getAndIncrement());
      msg.setClose(true);
      rpcRequest.setData(msg);
    } else {
      logger.warn("close channel context is null");
    }

    Future<Void> asyncSendFuture = nettyServerStarter.asyncSend(rpcRequest);
    asyncSendFuture.addListener(
        (FutureListener<Void>)
            f1 -> {
              if (f1.isDone()) {
                if (f1.isSuccess()) {
                  logger.debug("send remote request to close channel id[{}]", channelId);
                } else {
                  logger.info("close remote connection failed, cause:{}", f1.cause().toString());
                }
              }
            });
  }

  public void channelUnregistered(String id) {
    Channel remove = regMap.remove(id);
    logger.debug("un-register id[{}] with channel[{}]", id, remove);
  }
}
