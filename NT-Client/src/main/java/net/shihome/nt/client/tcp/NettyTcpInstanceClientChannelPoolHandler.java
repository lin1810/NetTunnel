package net.shihome.nt.client.tcp;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import net.shihome.nt.client.config.ClientInstance;
import net.shihome.nt.client.rpc.NettyRpcClient;
import net.shihome.nt.comm.constants.NtConstant;
import net.shihome.nt.comm.netty.codec.NettyDataEntryDecoder;
import net.shihome.nt.comm.netty.codec.NettyDataEntryEncoder;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyTcpInstanceClientChannelPoolHandler implements ChannelPoolHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(NettyTcpInstanceClientChannelPoolHandler.class);

  private final ThreadPoolExecutor serverHandlerPool;
  private final NettyRpcClient nettyRpcClient;
  private final ClientInstance clientInstance;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;

  public NettyTcpInstanceClientChannelPoolHandler(
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
  public void channelAcquired(Channel ch) {
    ch.attr(NettyTcpInstanceClient.INSTANCE_CONFIG_KEY).set(clientInstance);
    logger.debug("netty tcp instance handler channelAcquired. Channel ID: {}", ch.id());
  }

  @Override
  public void channelReleased(Channel ch) {
    NettyTcpInstanceClientContext nettyTcpInstanceClientContext =
        ch.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).getAndSet(null);
    if (nettyTcpInstanceClientContext != null) {
      nettyTcpInstanceClientContext.clear();
    }
    ch.attr(NettyTcpInstanceClient.INSTANCE_CONFIG_KEY).set(null);
    logger.debug("netty tcp instance handler channelReleased. Channel ID: {}", ch.id());
  }

  @Override
  public void channelCreated(Channel ch) {
    SocketChannel channel = (SocketChannel) ch;
    channel.config().setKeepAlive(true);
    channel.config().setTcpNoDelay(true);
    channel.config().setAutoClose(false);
    channel
        .pipeline()
        .addLast(new IdleStateHandler(0, 0, NtConstant.INSTANCE_KEEP_ALIVE, TimeUnit.MINUTES))
        .addLast(new ChunkedWriteHandler())
        .addLast(new NettyDataEntryDecoder())
        .addLast(new NettyDataEntryEncoder())
        .addLast(
            new NettyTcpInstanceClientHandler(
                clientInstance, serverHandlerPool, nettyRpcClient, rpcResponseFutureHandler));
  }
}
