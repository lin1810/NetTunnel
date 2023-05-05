package net.shihome.nt.comm.model;

import net.shihome.nt.comm.constants.NtConstant;

public class RpcRequestHelper {

  public static String getAckRequestId(RpcRequest rpcRequest) {
    return rpcRequest.getRequestId() + NtConstant.ACK_REQUEST_ID;
  }
}
