package net.shihome.nt.client.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import net.shihome.nt.client.config.ClientInstance;
import net.shihome.nt.client.rpc.NettyRpcClient;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.lang.SlidingWindowList;
import net.shihome.nt.comm.model.*;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.rpc.RpcFutureCallback;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.ExceptionUtil;
import net.shihome.nt.comm.utils.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyTcpInstanceClientHandler extends SimpleChannelInboundHandler<DataEntry>
    implements RpcFutureCallback<RpcResponse> {
  private static final Logger logger = LoggerFactory.getLogger(NettyTcpInstanceClientHandler.class);

  private final ThreadPoolExecutor serverHandlerPool;
  private final NettyRpcClient nettyRpcClient;
  private final ClientInstance clientInstance;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;

  public NettyTcpInstanceClientHandler(
      ClientInstance clientInstance,
      ThreadPoolExecutor serverHandlerPool,
      NettyRpcClient nettyRpcClient,
      RpcResponseFutureHandler rpcResponseFutureHandler) {
    this.clientInstance = clientInstance;
    this.serverHandlerPool = serverHandlerPool;
    this.nettyRpcClient = nettyRpcClient;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, DataEntry msg) {
    Channel channel = ctx.channel();
    NettyTcpInstanceClientContext nettyTcpInstanceClientContext =
        channel.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).get();
    ClientInstance instanceConfig = channel.attr(NettyTcpInstanceClient.INSTANCE_CONFIG_KEY).get();
    String instanceName = clientInstance.getInstanceName();
    if (instanceConfig == null || !Objects.equals(instanceConfig.getInstanceName(), instanceName)) {
      throw new NtException(
          ExceptionLevelEnum.error, "current channel is different from current handler");
    }
    if (nettyTcpInstanceClientContext == null) {
      throw new NtException(ExceptionLevelEnum.warn, "current channel context is null");
    }

    String channelId = nettyTcpInstanceClientContext.getChannelId();
    long spanId = nettyTcpInstanceClientContext.getCurrentResponseSpanId().getAndIncrement();
    msg.setChannelId(channelId);
    msg.setInstanceName(instanceName);
    msg.setSpanId(spanId);
    if (logger.isDebugEnabled()) {
      logger.debug("receive data entry from channel id[{}], span id[{}]", channelId, spanId);
    }

    sendDataEntry(msg, channel, nettyTcpInstanceClientContext);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    NettyTcpInstanceClientContext nettyTcpInstanceClientContext =
        ctx.channel().attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).get();
    String channelId = null;
    if (nettyTcpInstanceClientContext != null) {
      channelId = nettyTcpInstanceClientContext.getChannelId();
    }
    ExceptionUtil.printException(
        logger,
        clientInstance.getInstanceName()
            + " tcp instance caught exception, channel[{}], channelId[{}]",
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

  @Override
  public void accept(RpcResponse rpcResponse) {
    assert Objects.equals(rpcResponse.getType(), RpcResponseTypeEnum.ack);
    String channelId = rpcResponse.getChannelId();
    Long result = (Long) rpcResponse.getResult();
    Channel channel = NettyTcpInstanceClient.getInstance().getChannel(channelId);
    if (logger.isDebugEnabled()) {
      logger.debug("ACKed, channel id[{}], span id[{}]", channelId, result);
    }
    if (channel != null) {
      NettyTcpInstanceClientContext nettyTcpInstanceClientContext =
          channel.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).get();
      if (nettyTcpInstanceClientContext == null) {
        logger.debug("ACKed and channel[{}] is closed", channelId);
        return;
      }
      ConcurrentSkipListSet<DataEntry> pendingToManageSet =
          nettyTcpInstanceClientContext.getPendingToManageSet();
      SlidingWindowList slidingWindowList = nettyTcpInstanceClientContext.getSlidingWindowList();
      if (slidingWindowList != null) {
        if (slidingWindowList.remove(result)) {
          DataEntry dataEntry = pendingToManageSet.pollFirst();
          while (dataEntry != null
              && sendDataEntry(dataEntry, channel, nettyTcpInstanceClientContext)) {
            logger.debug(
                "ACKed, sending pending data, channel id[{}], span id[{}]",
                channelId,
                dataEntry.getSpanId());
            dataEntry = pendingToManageSet.pollFirst();
          }
        }
      }
    } else {
      logger.debug("ACKed and channel[{}] is closed", channelId);
    }
  }

  private boolean sendDataEntry(
      DataEntry msg, Channel channel, NettyTcpInstanceClientContext nettyTcpInstanceClientContext) {
    String channelId = nettyTcpInstanceClientContext.getChannelId();
    SlidingWindowList slidingWindowList = nettyTcpInstanceClientContext.getSlidingWindowList();
    RpcRequest rpcRequest = new RpcRequest();
    rpcRequest.setRequestType(RpcRequestTypeEnum.data);
    rpcRequest.setChannelId(channelId);
    rpcRequest.setData(msg);
    rpcRequest.setNeedAck(true);
    if (slidingWindowList.add(msg.getSpanId())) {
      serverHandlerPool.execute(
          () -> {
            Semaphore semaphore = nettyTcpInstanceClientContext.getSemaphore();
            try {
              if (semaphore == null
                  || semaphore.tryAcquire(
                      clientInstance.getPermitsTimeoutInSecond(), TimeUnit.SECONDS)) {
                rpcRequest.setRequestId(IdUtil.getNextId());
                rpcResponseFutureHandler.createResponseFuture(
                    rpcRequest, RpcRequestHelper.getAckRequestId(rpcRequest), this);
                Future<Void> asyncSendFuture = nettyRpcClient.asyncSend(rpcRequest);
                asyncSendFuture.addListener(
                    (FutureListener<Void>)
                        f1 -> {
                          if (f1.isDone()) {
                            if (!f1.isSuccess()) {
                              logger.warn(
                                  "message received from target server send failed, cause:{}",
                                  f1.cause().toString());
                              rpcResponseFutureHandler.removeInvokerFuture(
                                  RpcRequestHelper.getAckRequestId(rpcRequest));
                              channel.close();
                            }
                            if (semaphore != null) {
                              semaphore.release();
                            }
                          }
                        });
              } else {
                logger.warn(
                    "[{}]get permit timeout, and close current channel[{}]", channelId, channel);
                channel.close();
              }
            } catch (Throwable throwable) {
              ExceptionUtil.printException(
                  logger, "exception caught during send data request", null, throwable);
              if (semaphore != null) {
                semaphore.release();
              }
              channel.close();
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
      nettyTcpInstanceClientContext.getPendingToManageSet().add(msg);
      return false;
    }
  }
}
