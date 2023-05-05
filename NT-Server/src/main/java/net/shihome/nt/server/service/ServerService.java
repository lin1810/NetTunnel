package net.shihome.nt.server.service;

import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;

public interface ServerService {

  RpcResponse invoke(RpcRequest rpcRequest);
}
