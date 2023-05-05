package net.shihome.nt.client.rpc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcRequestTypeEnum;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.ExceptionUtil;
import net.shihome.nt.comm.utils.IdUtil;

public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
  private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);
  private final RpcResponseFutureHandler rpcResponseFutureHandler;

  public NettyClientHandler(final RpcResponseFutureHandler rpcResponseFutureHandler) {
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, RpcResponse rpcResponse) {
    // notify response
    rpcResponseFutureHandler.notifyInvokerFuture(rpcResponse.getRequestId(), rpcResponse);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ExceptionUtil.printException(
        logger,
        "NettyClientHandler caught exception, channel[{}]",
        new Object[] {ctx.channel()},
        cause);
    ctx.close();
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      logger.debug(
          "idle state event, {}, current channel:{}, and send heart beat", evt, ctx.channel());
      RpcRequest request = new RpcRequest();
      request.setRequestType(RpcRequestTypeEnum.heartbeat);
      request.setRequestId(IdUtil.getNextId());
      ctx.writeAndFlush(request);
    }
    super.userEventTriggered(ctx, evt);
  }
}
