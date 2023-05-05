package net.shihome.nt.server.service.impl;

import net.shihome.nt.server.service.ServerService;
import org.springframework.stereotype.Component;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.model.RpcResponseTypeEnum;

@Component
public class HeartbeatService implements ServerService {
  @Override
  public RpcResponse invoke(RpcRequest rpcRequest) {
    RpcResponse rpcResponse = new RpcResponse();
    rpcResponse.setRequestId(rpcRequest.getRequestId());
    rpcResponse.setType(RpcResponseTypeEnum.heartbeat);
    return rpcResponse;
  }
}
