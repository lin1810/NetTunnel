package net.shihome.nt.client.rpc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.shihome.nt.client.service.ClientService;
import net.shihome.nt.client.tcp.NettyTcpInstanceClient;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcRequestHelper;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.model.RpcResponseTypeEnum;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.utils.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadPoolExecutor;

public class NettyClientServiceHandler extends SimpleChannelInboundHandler<RpcRequest> {
  private static final Logger logger = LoggerFactory.getLogger(NettyClientServiceHandler.class);
  private final ThreadPoolExecutor serverHandlerPool;

  private final ClientService clientService;

  public NettyClientServiceHandler(
      ThreadPoolExecutor serverHandlerPool, ClientService clientService) {
    this.serverHandlerPool = serverHandlerPool;
    this.clientService = clientService;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, RpcRequest rpcRequest) throws Exception {
    String channelId = rpcRequest.getChannelId();
    try {
      // do invoke
      serverHandlerPool.execute(
          () -> {
            try {
              if (rpcRequest.isNeedAck()) {
                RpcResponse ackResponse = new RpcResponse();
                ackResponse.setRequestId(RpcRequestHelper.getAckRequestId(rpcRequest));
                ackResponse.setChannelId(rpcRequest.getChannelId());
                ackResponse.setType(RpcResponseTypeEnum.ack);
                DataEntry data = rpcRequest.getData();
                if (data != null) {
                  ackResponse.setResult(data.getSpanId());
                }
                ctx.writeAndFlush(ackResponse);
              }
              RpcResponse rpcResponse = clientService.invoke(rpcRequest);
              if (rpcResponse != null) {
                ctx.writeAndFlush(rpcResponse);
              }
            } catch (Throwable e) {
              ExceptionUtil.printException(
                  logger, "exception caught, channel[{}]", new Object[] {ctx.channel()}, e);
              try {
                if (StringUtils.hasText(channelId))
                  NettyTcpInstanceClient.getInstance().closeRemoteConnection(null, channelId, null);
              } catch (Throwable e2) {
                ExceptionUtil.printException(
                    logger,
                    "exception caught during close remote connection , channel[{}]",
                    new Object[] {ctx.channel()},
                    e2);
              }
            }
          });
    } catch (Throwable e) {
      ExceptionUtil.printException(
          logger, "exception caught, channel[{}]", new Object[] {ctx.channel()}, e);
      try {
        if (StringUtils.hasText(channelId))
          NettyTcpInstanceClient.getInstance().closeRemoteConnection(null, channelId, null);
      } catch (Throwable e2) {
        ExceptionUtil.printException(
            logger,
            "exception caught during close remote connection , channel[{}]",
            new Object[] {ctx.channel()},
            e2);
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ExceptionUtil.printException(
        logger, "server caught exception, channel[{}]", new Object[] {ctx.channel()}, cause);
    ctx.close();
  }
}
