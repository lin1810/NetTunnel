package net.shihome.nt.client.service;

import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;

public interface ClientService {

  RpcResponse invoke(RpcRequest request);
}
