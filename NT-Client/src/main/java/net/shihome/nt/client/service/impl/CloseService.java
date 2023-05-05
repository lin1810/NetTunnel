package net.shihome.nt.client.service.impl;

import io.netty.channel.Channel;
import net.shihome.nt.client.service.ClientService;
import net.shihome.nt.client.tcp.NettyTcpInstanceClient;
import net.shihome.nt.client.tcp.NettyTcpInstanceClientContext;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CloseService implements ClientService {
  private static final Logger logger = LoggerFactory.getLogger(CloseService.class);

  @Resource ScheduledExecutorService scheduledExecutorService;

  @Override
  public RpcResponse invoke(RpcRequest rpcRequest) {
    String channelId = rpcRequest.getChannelId();
    if (StringUtils.hasText(channelId)) {
      closeChannel(channelId);
    } else {
      logger.warn("pending to close channel id is empty");
    }
    return null;
  }

  private void closeChannel(String channelId) {
    Channel channel = NettyTcpInstanceClient.getInstance().getChannel(channelId);
    if (channel != null) {
      NettyTcpInstanceClientContext nettyTcpInstanceClientContext =
          channel.attr(NettyTcpInstanceClient.CONTEXT_ATTRIBUTE_KEY).get();
      if (nettyTcpInstanceClientContext != null
          && nettyTcpInstanceClientContext.getPendingToSendSet().size() > 0) {
        scheduledExecutorService.schedule(() -> closeChannel(channelId), 5, TimeUnit.SECONDS);
      } else {
        logger.info("close channel({})", channel);
        channel.close();
      }
    }
  }
}
