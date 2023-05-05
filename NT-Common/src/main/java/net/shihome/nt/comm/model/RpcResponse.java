package net.shihome.nt.comm.model;

import java.io.Serializable;

public class RpcResponse implements Serializable {
  private String requestId;
  private String errorMsg;
  private RpcResponseTypeEnum type;
  private Object result;
  private String channelId;

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public Object getResult() {
    return result;
  }

  public void setResult(Object result) {
    this.result = result;
  }

  public RpcResponseTypeEnum getType() {
    return type;
  }

  public void setType(RpcResponseTypeEnum type) {
    this.type = type;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }
}
