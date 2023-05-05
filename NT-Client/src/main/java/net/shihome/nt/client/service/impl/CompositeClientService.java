package net.shihome.nt.client.service.impl;

import net.shihome.nt.client.service.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;

import java.util.Map;

public class CompositeClientService implements ClientService {
  private static final Logger logger = LoggerFactory.getLogger(CompositeClientService.class);

  private final Map<String, ClientService> serviceMap;

  public CompositeClientService(Map<String, ClientService> serviceMap) {
    this.serviceMap = serviceMap;
  }

  @Override
  public RpcResponse invoke(RpcRequest rpcRequest) {
    String typeName = rpcRequest.getRequestType().getTypeName();
    ClientService serverService = serviceMap.get(typeName);
    if (serverService == null) {
      throw new NtException(ExceptionLevelEnum.error, "unknown request type: " + typeName);
    }
    return serverService.invoke(rpcRequest);
  }
}
