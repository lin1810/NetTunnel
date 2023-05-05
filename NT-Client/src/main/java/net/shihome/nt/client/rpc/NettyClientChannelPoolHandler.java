package net.shihome.nt.client.rpc;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import net.shihome.nt.client.config.ClientProperties;
import net.shihome.nt.client.service.ClientService;
import net.shihome.nt.comm.constants.NtConstant;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.netty.codec.NettyCodecTypeEnum;
import net.shihome.nt.comm.netty.codec.NettyObjectDecoder;
import net.shihome.nt.comm.netty.codec.NettyObjectEncoder;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.service.RpcSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyClientChannelPoolHandler implements ChannelPoolHandler {

  private static final Logger logger = LoggerFactory.getLogger(NettyClientChannelPoolHandler.class);

  private final RpcSerializer rpcSerializer;
  private final RpcResponseFutureHandler rpcResponseFutureHandler;
  private final ThreadPoolExecutor serverHandlerPool;
  private final ClientService clientService;
  private final ClientProperties clientProperties;

  public NettyClientChannelPoolHandler(
      RpcSerializer rpcSerializer,
      RpcResponseFutureHandler rpcResponseFutureHandler,
      ThreadPoolExecutor serverHandlerPool,
      ClientService clientService,
      ClientProperties clientProperties) {
    this.rpcSerializer = rpcSerializer;
    this.rpcResponseFutureHandler = rpcResponseFutureHandler;
    this.serverHandlerPool = serverHandlerPool;
    this.clientService = clientService;
    this.clientProperties = clientProperties;
  }

  @Override
  public void channelReleased(Channel ch) {
    logger.debug("netty client channelReleased. Channel ID: {}", ch.id());
  }

  @Override
  public void channelAcquired(Channel ch) {
    logger.debug("netty client channelAcquired. Channel ID: {}", ch.id());
  }

  @Override
  public void channelCreated(Channel ch) throws Exception {
    SocketChannel channel = (SocketChannel) ch;
    channel.config().setKeepAlive(true);
    channel.config().setTcpNoDelay(true);
    Map<NettyCodecTypeEnum, Class<?>> decodeMap = new HashMap<>();
    decodeMap.put(NettyCodecTypeEnum.client, RpcResponse.class);
    decodeMap.put(NettyCodecTypeEnum.server, RpcRequest.class);
    channel
        .pipeline()
        .addLast(
            new IdleStateHandler(0, 0, NtConstant.BEAT_HEAT_PER_MINUTES_CLIENT, TimeUnit.MINUTES))
        .addLast(new ChunkedWriteHandler())
        .addLast(new NettyObjectEncoder(RpcRequest.class, rpcSerializer, NettyCodecTypeEnum.client))
        .addLast(
            new NettyObjectEncoder(RpcResponse.class, rpcSerializer, NettyCodecTypeEnum.server))
        .addLast(new NettyObjectDecoder(rpcSerializer, decodeMap))
        .addLast(new NettyClientHandler(rpcResponseFutureHandler))
        .addLast(new NettyClientServiceHandler(serverHandlerPool, clientService));
    File keyFile = clientProperties.getClientKeyPath();
    File certFile = clientProperties.getClientCertPath();
    File caFile = clientProperties.getCaPath();
    String clientKeyPassword = clientProperties.getClientKeyPassword();

    if (certFile != null && keyFile != null && caFile != null) {
      SslContext nettySslContext =
          SslContextBuilder.forClient()
              .keyManager(certFile, keyFile, clientKeyPassword)
              .trustManager(caFile)
              .clientAuth(ClientAuth.REQUIRE)
              .build();
      channel.pipeline().addFirst(nettySslContext.newHandler(channel.alloc()));
    } else {
      logger.warn(
          "current channel is un-safe, It is strongly recommended to enable SSL/TLS channel authentication");
    }
  }
}
