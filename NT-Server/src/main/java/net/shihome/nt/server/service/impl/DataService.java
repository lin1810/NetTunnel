package net.shihome.nt.server.service.impl;

import io.netty.util.concurrent.FutureListener;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.utils.ExceptionUtil;
import net.shihome.nt.server.service.ServerService;
import net.shihome.nt.server.tcp.NettyTcpInstanceChannelHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DataService implements ServerService {
  private static final Logger logger = LoggerFactory.getLogger(DataService.class);

  @Override
  public RpcResponse invoke(RpcRequest request) {
    DataEntry data = request.getData();
    String instanceName = data.getInstanceName();
    String channelId = data.getChannelId();
    Long spanId = data.getSpanId();
    byte[] dataBytes = data.getBytes();
    boolean isClose = data.isClose();
    if (!isClose && (dataBytes == null || dataBytes.length == 0)) {
      logger.warn(
          "received data message from instance:{}, channelId:{}, dataBytes size is empty",
          instanceName,
          channelId);
    }
    logger.debug("received data message from instance:{}, channelId:{}, dataBytes size:{}, spanId:{}, close:{}", instanceName, channelId,
            dataBytes == null ? null : dataBytes.length, spanId, data.isClose());
    NettyTcpInstanceChannelHolder.getInstance()
        .asyncSend(data)
        .addListener(
            (FutureListener<Void>)
                f1 -> {
                  if (f1.isDone() && !f1.isSuccess()) {
                    if (isClose) {
                      logger.debug(
                          "close data send failed:{}, channel id:{}, instance:{}",
                          f1.cause().toString(),
                          channelId,
                          instanceName);
                    } else
                      ExceptionUtil.printException(
                          logger,
                          "fall back to client failed, data instance name:{}, channelId:{}, request type:{}",
                          new Object[] {
                            instanceName, channelId, request.getRequestType().getTypeName()
                          },
                          f1.cause());
                  }
                });

    return null;
  }
}
