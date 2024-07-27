package net.shihome.nt.comm.model;

import net.shihome.nt.comm.model.data.DataEntry;

import java.io.Serializable;

public class RpcRequest implements Serializable {
  private String requestId;
  private RpcRequestTypeEnum requestType;
  private DataEntry data;
  private String channelId;
  private String sourceIp;
  private boolean needAck;

  public String getSourceIp() {
    return sourceIp;
  }

  public void setSourceIp(String sourceIp) {
    this.sourceIp = sourceIp;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public boolean isNeedAck() {
    return needAck;
  }

  public void setNeedAck(boolean needAck) {
    this.needAck = needAck;
  }

  public DataEntry getData() {
    return data;
  }

  public void setData(DataEntry data) {
    this.data = data;
  }

  public RpcRequestTypeEnum getRequestType() {
    return requestType;
  }

  public void setRequestType(RpcRequestTypeEnum requestType) {
    this.requestType = requestType;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }
}
