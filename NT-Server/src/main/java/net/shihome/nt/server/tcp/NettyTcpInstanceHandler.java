package net.shihome.nt.server.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import net.shihome.nt.comm.lang.SlidingWindowList;
import net.shihome.nt.comm.model.*;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.rpc.RpcFutureCallback;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.ExceptionUtil;
import net.shihome.nt.comm.utils.IdUtil;
import net.shihome.nt.server.config.ServerInstance;
import net.shihome.nt.server.rpc.NettyManageServerStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyTcpInstanceHandler extends SimpleChannelInboundHandler<DataEntry>
    implements RpcFutureCallback<RpcResponse> {
  public static final AttributeKey<NettyTcpInstanceContext> CONTEXT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(NettyTcpInstanceHandler.class.getName() + "_CONTEXT_ATTRIBUTE_KEY");
  private static final Logger logger = LoggerFactory.getLogger(NettyTcpInstanceHandler.class);
  private final String instanceName;
  private final ThreadPoolExecutor serverHandlerPool;
  private final NettyManageServerStarter nettyServerStarter;
  private final ServerInstance instance;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;

  public NettyTcpInstanceHandler(
      String instanceName,
      ThreadPoolExecutor serverHandlerPool,
      NettyManageServerStarter nettyServerStarter,
      ServerInstance instance,
      RpcResponseFutureHandler rpcResponseFutureHandler) {
    this.instanceName = instanceName;
    this.serverHandlerPool = serverHandlerPool;
    this.nettyServerStarter = nettyServerStarter;
    this.instance = instance;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    Channel channel = ctx.channel();
    NettyTcpInstanceContext tcpInstanceContext = new NettyTcpInstanceContext();
    String id = NettyTcpInstanceChannelHolder.getInstance().channelRegistered(channel);
    tcpInstanceContext.setInstanceName(instanceName);
    tcpInstanceContext.setChannelId(id);
    int permits = instance.getPermits();
    if (permits > 0) {
      tcpInstanceContext.setSemaphore(new Semaphore(permits, true));
    }
    int slidingWindowSize = instance.getSlidingWindowSize();
    if (slidingWindowSize > 1) {
      tcpInstanceContext.setSlidingWindowList(new SlidingWindowList(slidingWindowSize));
    }
    NettyTcpInstanceContext nettyTcpInstanceContext =
        channel.attr(CONTEXT_ATTRIBUTE_KEY).setIfAbsent(tcpInstanceContext);
    if (nettyTcpInstanceContext != null) {
      id = nettyTcpInstanceContext.getChannelId();
      logger.warn("current channel contain id [{}], channel[{}]", id, ctx.channel());
    }

    if (instance.isEnableConnectionLog() || logger.isDebugEnabled()) {
      logger.info("[{}] new connection from {}", id, ctx.channel());
    }
    super.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    Channel channel = ctx.channel();
    NettyTcpInstanceContext nettyTcpInstanceContext = channel.attr(CONTEXT_ATTRIBUTE_KEY).get();
    if (nettyTcpInstanceContext != null) {
      String id = nettyTcpInstanceContext.getChannelId();
      NettyTcpInstanceChannelHolder.getInstance().channelUnregistered(id);
      nettyTcpInstanceContext.getPendingToSendSet().clear();
      logger.debug("channelInactive id[{}] channel[{}]", id, channel);
    }
    super.channelInactive(ctx);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, DataEntry msg) {
    Channel channel = ctx.channel();
    NettyTcpInstanceContext nettyTcpInstanceContext = channel.attr(CONTEXT_ATTRIBUTE_KEY).get();
    String channelId = nettyTcpInstanceContext.getChannelId();
    long spanId = nettyTcpInstanceContext.getCurrentRequestSpanId().getAndIncrement();
    msg.setChannelId(channelId);
    msg.setInstanceName(instanceName);
    msg.setSpanId(spanId);

    if (logger.isDebugEnabled()) {
      logger.debug("receive data entry from channel id[{}], span id[{}]", channelId, spanId);
    }

    sendDataEntry(msg, channel, nettyTcpInstanceContext);
  }

  @Override
  public void accept(RpcResponse rpcResponse) {
    assert Objects.equals(rpcResponse.getType(), RpcResponseTypeEnum.ack);
    String channelId = rpcResponse.getChannelId();
    Long result = (Long) rpcResponse.getResult();
    if (logger.isDebugEnabled()) {
      logger.debug("ACKed, channel id[{}], span id[{}]", channelId, result);
    }
    Future<Channel> channelFuture =
        NettyTcpInstanceChannelHolder.getInstance().getChannel(channelId);
    channelFuture.addListener(
        (FutureListener<Channel>)
            (future) -> {
              if (future.isDone()) {
                if (future.isSuccess()) {
                  Channel channel = future.get();
                  NettyTcpInstanceContext nettyTcpInstanceContext =
                      channel.attr(CONTEXT_ATTRIBUTE_KEY).get();
                  if (nettyTcpInstanceContext == null) {
                    logger.debug("ACKed and channel[{}] is closed", channelId);
                    return;
                  }
                  ConcurrentSkipListSet<DataEntry> pendingToManageSet =
                      nettyTcpInstanceContext.getPendingToManageSet();
                  SlidingWindowList slidingWindowList =
                      nettyTcpInstanceContext.getSlidingWindowList();
                  if (slidingWindowList != null) {
                    if (slidingWindowList.remove(result)) {
                      DataEntry dataEntry = pendingToManageSet.pollFirst();
                      while (dataEntry != null
                          && sendDataEntry(dataEntry, channel, nettyTcpInstanceContext)) {
                        logger.debug(
                            "ACKed, sending pending data, channel id[{}], span id[{}]",
                            channelId,
                            dataEntry.getSpanId());
                        dataEntry = pendingToManageSet.pollFirst();
                      }
                    }
                  }
                } else {
                  logger.info("get channel failed, cause:" + future.cause());
                }
              }
            });
  }

  private boolean sendDataEntry(
      DataEntry msg, Channel channel, NettyTcpInstanceContext nettyTcpInstanceContext) {
    String id = nettyTcpInstanceContext.getChannelId();
    SlidingWindowList slidingWindowList = nettyTcpInstanceContext.getSlidingWindowList();

    RpcRequest rpcRequest = new RpcRequest();
    rpcRequest.setRequestType(RpcRequestTypeEnum.data);
    rpcRequest.setChannelId(id);
    rpcRequest.setData(msg);
    rpcRequest.setNeedAck(true);

    if (slidingWindowList.add(msg.getSpanId())) {
      serverHandlerPool.execute(
          () -> {
            Semaphore semaphore = nettyTcpInstanceContext.getSemaphore();
            try {
              if (semaphore == null
                  || semaphore.tryAcquire(instance.getPermitsTimeoutInSecond(), TimeUnit.SECONDS)) {
                rpcRequest.setRequestId(IdUtil.getNextId());
                rpcResponseFutureHandler.createResponseFuture(
                    rpcRequest, RpcRequestHelper.getAckRequestId(rpcRequest), this);
                Future<Void> voidFuture = nettyServerStarter.asyncSend(rpcRequest);
                voidFuture.addListener(
                    (FutureListener<Void>)
                        f1 -> {
                          if (f1.isDone()) {
                            if (!f1.isSuccess()) {
                              rpcResponseFutureHandler.removeInvokerFuture(
                                  RpcRequestHelper.getAckRequestId(rpcRequest));
                              Throwable cause = f1.cause();
                              logger.debug("send failed, cause:" + cause);
                              channel.close();
                            }
                            if (semaphore != null) {
                              semaphore.release();
                            }
                          }
                        });
              } else {
                logger.warn("[{}]get permit timeout, and close current channel[{}]", id, channel);
                channel.close();
              }
            } catch (Throwable throwable) {
              ExceptionUtil.printException(
                  logger, "exception caught during send data request", null, throwable);
              channel.close();
              if (semaphore != null) {
                semaphore.release();
              }
            }
          });
      return true;
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "add pending to manage set, channel Id:[{}], msg spanId:[{}], pending size:[{}]",
            msg.getChannelId(),
            msg.getSpanId(),
            slidingWindowList.getSize());
      }
      nettyTcpInstanceContext.getPendingToManageSet().add(msg);
      return false;
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    NettyTcpInstanceContext nettyTcpInstanceContext =
        ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get();
    String channelId = null;
    if (nettyTcpInstanceContext != null) {
      channelId = nettyTcpInstanceContext.getChannelId();
    }
    ExceptionUtil.printException(
        logger,
        instanceName + " tcp instance caught exception, channel[{}], channel id[{}]",
        new Object[] {ctx.channel(), channelId},
        cause);
    ctx.channel().close();
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      logger.info(
          "tcp netty server close an idle channel. idle state:{}, channel:{}", evt, ctx.channel());
      ctx.channel().close();
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }
}
