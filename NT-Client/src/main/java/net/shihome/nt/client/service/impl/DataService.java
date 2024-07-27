package net.shihome.nt.client.service.impl;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import net.shihome.nt.client.service.ClientService;
import net.shihome.nt.client.tcp.NettyTcpInstanceClient;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.model.data.DataEntry;
import net.shihome.nt.comm.utils.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DataService implements ClientService {
  private static final Logger logger = LoggerFactory.getLogger(DataService.class);

  @Override
  public RpcResponse invoke(RpcRequest request) {
    DataEntry data = request.getData();
    String sourceIp = request.getSourceIp();
    String instanceName = data.getInstanceName();
    String channelId = data.getChannelId();
    byte[] dataBytes = data.getBytes();
    boolean isClose = data.isClose();
    if (!isClose && (dataBytes == null || dataBytes.length == 0)) {
      logger.warn(
          "received data message from instance:{}, channelId:{}, sourceIp:{}, dataBytes size is empty",
          instanceName,
          channelId, sourceIp);
    }

    logger.debug(
        "received data message from instance:{}, channelId:{}, dataBytes size:{}, sourceIp:{}",
        instanceName,
        channelId,
        dataBytes == null ? null : dataBytes.length, sourceIp);
    Future<Void> asyncSendFuture = NettyTcpInstanceClient.getInstance().asyncSend(data);

    asyncSendFuture.addListener(
        (FutureListener<Void>)
            f1 -> {
              if (f1.isDone() && !f1.isSuccess()) {
                if (isClose) {
                  logger.debug(
                      "close data send failed:{}, channel id:{}, instance:{}, sourceIp:{}",
                      f1.cause().toString(),
                      channelId,
                      instanceName, sourceIp);
                } else {
                  ExceptionUtil.printException(
                      logger,
                      "connection to target serer failed, instance:{}, channelId:{}, request type:{}, sourceIp:{}",
                      new Object[] {
                        instanceName, channelId, request.getRequestType().getTypeName(), sourceIp
                      },
                      f1.cause(),
                      ExceptionLevelEnum.info);
                }
              }
            });

    return null;
  }
}
