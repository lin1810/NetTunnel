package net.shihome.nt.server.rpc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcRequestHelper;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.model.RpcResponseTypeEnum;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.utils.ExceptionUtil;
import net.shihome.nt.server.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

public class NettyManageServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
  private static final Logger logger = LoggerFactory.getLogger(NettyManageServerHandler.class);
  private final ThreadPoolExecutor serverHandlerPool;

  private final ServerService serverService;

  public NettyManageServerHandler(
      ThreadPoolExecutor serverHandlerPool, ServerService serverService) {
    this.serverHandlerPool = serverHandlerPool;
    this.serverService = serverService;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, RpcRequest rpcRequest) {
    try {
      // do invoke
      serverHandlerPool.execute(
          () -> {
            try {
              if (rpcRequest.isNeedAck()) {
                RpcResponse ackResponse = new RpcResponse();
                ackResponse.setRequestId(RpcRequestHelper.getAckRequestId(rpcRequest));
                ackResponse.setType(RpcResponseTypeEnum.ack);
                ackResponse.setChannelId(rpcRequest.getChannelId());
                DataEntry data = rpcRequest.getData();
                if (data != null) {
                  ackResponse.setResult(data.getSpanId());
                }
                ctx.writeAndFlush(ackResponse);
              }
              RpcResponse rpcResponse = serverService.invoke(rpcRequest);
              if (rpcResponse != null) ctx.writeAndFlush(rpcResponse);
            } catch (Exception e) {
              RpcResponse rpcResponse = new RpcResponse();
              rpcResponse.setRequestId(rpcRequest.getRequestId());
              rpcResponse.setErrorMsg(ExceptionUtil.getErrorInfo(e));
              ctx.writeAndFlush(rpcResponse);
            }
          });
    } catch (Exception e) {
      // catch error
      RpcResponse rpcResponse = new RpcResponse();
      rpcResponse.setRequestId(rpcRequest.getRequestId());
      rpcResponse.setErrorMsg(ExceptionUtil.getErrorInfo(e));
      ctx.writeAndFlush(rpcResponse);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ExceptionUtil.printException(
        logger,
        "server caught exception, channel[{}]",
        new Object[] {ctx.channel()},
        cause,
        ExceptionLevelEnum.warn);
    ctx.close();
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      logger.info(
          "rpc netty server close an idle channel. idle state:{}, channel:{}", evt, ctx.channel());
      ctx.channel().close();
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }
}
