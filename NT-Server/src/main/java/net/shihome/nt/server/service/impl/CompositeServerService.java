package net.shihome.nt.server.service.impl;

import net.shihome.nt.server.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;

import java.util.Map;

public class CompositeServerService implements ServerService {

  private static final Logger logger = LoggerFactory.getLogger(CompositeServerService.class);

  private final Map<String, ServerService> serviceMap;

  public CompositeServerService(Map<String, ServerService> serviceMap) {
    this.serviceMap = serviceMap;
  }

  @Override
  public RpcResponse invoke(RpcRequest rpcRequest) {
    String typeName = rpcRequest.getRequestType().getTypeName();
    ServerService serverService = serviceMap.get(typeName);
    if (serverService == null) {
      throw new NtException(ExceptionLevelEnum.error, "unknown request type: " + typeName);
    }
    return serverService.invoke(rpcRequest);
  }
}
