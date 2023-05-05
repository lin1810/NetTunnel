package net.shihome.nt.server.rpc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.ExceptionUtil;

import java.util.Deque;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

@ChannelHandler.Sharable
public class NettyManageServerRequestHandler extends SimpleChannelInboundHandler<RpcResponse> {

  private static final Logger logger = LoggerFactory.getLogger(NettyManageServerRequestHandler.class);

  private final RpcResponseFutureHandler rpcResponseFutureHandler;

  private final Deque<Channel> deque = PlatformDependent.newConcurrentDeque();

  private final ChannelHealthChecker healthCheck = ChannelHealthChecker.ACTIVE;

  private final ServerBootstrap bootstrap;

  public NettyManageServerRequestHandler(
      ServerBootstrap bootstrap, RpcResponseFutureHandler rpcResponseFutureHandler) {
    this.bootstrap = bootstrap;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, RpcResponse rpcResponse) {
    rpcResponseFutureHandler.notifyInvokerFuture(rpcResponse.getRequestId(), rpcResponse);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ExceptionUtil.printException(
        logger, "NettyServerRequestHandler caught exception", new Object[] {ctx.channel()}, cause);
    ctx.close();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);
    offerChannel(ctx.channel());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    super.channelInactive(ctx);
    deque.remove(ctx.channel());
  }

  protected Channel pollChannel() {
    return deque.pollFirst();
  }

  public Future<Void> asyncSend(RpcRequest rpcRequest) {
    Promise<Void> asyncSendFuture = bootstrap.config().childGroup().next().newPromise();
    acquire()
        .addListener(
            (FutureListener<Channel>)
                f1 -> {
                  if (f1.isSuccess()) {
                    Channel ch = f1.getNow();
                    try {
                      ch.writeAndFlush(rpcRequest);
                    } finally {
                      release(ch);
                    }
                    asyncSendFuture.trySuccess(null);
                  } else {
                    ExceptionUtil.printException(logger, "async send failed", null, f1.cause());
                    asyncSendFuture.tryFailure(f1.cause());
                  }
                });
    return asyncSendFuture;
  }

  public Future<Channel> acquire() {
    Promise<Channel> channelPromise = bootstrap.config().childGroup().next().newPromise();
    return acquireHealthyFromPoolOrNew(channelPromise);
  }

  public Future<Channel> acquireHealthyFromPoolOrNew(final Promise<Channel> promise) {

    try {
      final Channel ch = pollChannel();
      if (ch == null) {
        // No Channel left in the pool
        promise.tryFailure(new NtException(ExceptionLevelEnum.warn, "No Channel left in the pool"));
      } else {
        EventLoop loop = ch.eventLoop();
        if (loop.inEventLoop()) {
          doHealthCheck(ch, promise);
        } else {
          loop.execute(() -> doHealthCheck(ch, promise));
        }
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
        f.addListener((FutureListener<Boolean>) f1 -> notifyHealthCheck(f1, channel, promise));
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
        closeChannel(channel);
        acquireHealthyFromPoolOrNew(promise);
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
    logger.debug("close rpc channel[{}]", channel);
    channel.close();
  }

  protected boolean offerChannel(Channel channel) {
    return deque.offer(channel);
  }

  public final Future<Void> release(Channel channel) {
    Promise<Void> promise = bootstrap.config().childGroup().next().newPromise();
    return release(channel, promise);
  }

  public Future<Void> release(final Channel channel, final Promise<Void> promise) {
    try {
      checkNotNull(channel, "channel");
      checkNotNull(promise, "promise");
      EventLoop loop = channel.eventLoop();
      if (loop.inEventLoop()) {
        doReleaseChannel(channel, promise);
      } else {
        loop.execute(() -> doReleaseChannel(channel, promise));
      }
    } catch (Throwable cause) {
      closeAndFail(channel, cause, promise);
    }
    return promise;
  }

  private void doReleaseChannel(Channel channel, Promise<Void> promise) {
    try {
      assert channel.eventLoop().inEventLoop();
      doHealthCheckOnRelease(channel, promise);
    } catch (Throwable cause) {
      closeAndFail(channel, cause, promise);
    }
  }

  private void doHealthCheckOnRelease(final Channel channel, final Promise<Void> promise) {
    final Future<Boolean> f = healthCheck.isHealthy(channel);
    if (f.isDone()) {
      releaseAndOfferIfHealthy(channel, promise, f);
    } else {
      f.addListener(f1 -> releaseAndOfferIfHealthy(channel, promise, f));
    }
  }

  private void releaseAndOfferIfHealthy(
      Channel channel, Promise<Void> promise, Future<Boolean> future) {
    try {
      if (future.getNow()) { // channel turns out to be healthy, offering and releasing it.
        releaseAndOffer(channel, promise);
      } else { // channel not healthy, just releasing it.
        promise.setSuccess(null);
      }
    } catch (Throwable cause) {
      closeAndFail(channel, cause, promise);
    }
  }

  private void releaseAndOffer(Channel channel, Promise<Void> promise) {
    if (offerChannel(channel)) {
      promise.setSuccess(null);
    } else {
      closeAndFail(channel, new NtException(ExceptionLevelEnum.warn, "ChannelPool full"), promise);
    }
  }
}
