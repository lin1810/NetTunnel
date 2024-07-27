package net.shihome.nt.server.service.impl;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.server.service.ServerService;
import net.shihome.nt.server.tcp.NettyTcpInstanceChannelHolder;
import net.shihome.nt.server.tcp.NettyTcpInstanceContext;
import net.shihome.nt.server.tcp.NettyTcpInstanceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CloseService implements ServerService {
  private static final Logger logger = LoggerFactory.getLogger(CloseService.class);

  @Resource private ScheduledExecutorService scheduledExecutorService;

  @Override
  public RpcResponse invoke(RpcRequest rpcRequest) {
    String channelId = rpcRequest.getChannelId();
    logger.debug("received remote close request, channel id[{}]", channelId);
    if (StringUtils.hasText(channelId)) {
      closeChannel(channelId);
    } else {
      logger.warn("pending to close channel id is empty");
    }
    return null;
  }

  private void closeChannel(String channelId) {
    Future<Channel> channel = NettyTcpInstanceChannelHolder.getInstance().getChannel(channelId);
    channel.addListener(
        (FutureListener<Channel>)
            f1 -> {
              if (f1.isDone()) {
                if (f1.isSuccess()) {
                  logger.debug("close channel from remote request channel id [{}]", channelId);
                  Channel channel1 = f1.get();
                  NettyTcpInstanceContext nettyTcpInstanceContext =
                      channel1.attr(NettyTcpInstanceHandler.CONTEXT_ATTRIBUTE_KEY).get();
                  if (nettyTcpInstanceContext != null
                      && !nettyTcpInstanceContext.getPendingToSendSet().isEmpty()) {
                    scheduledExecutorService.schedule(
                        () -> closeChannel(channelId), 5, TimeUnit.SECONDS);
                  } else {
                    scheduledExecutorService.schedule(() -> {
                      logger.info("close channel({})", channelId);
                      channel1.close();
                    }, 5, TimeUnit.SECONDS);
                  }
                } else {
                  logger.debug(
                      "get channel[{}] is closed, cause:{}", channelId, f1.cause().toString());
                }
              }
            });
  }
}
