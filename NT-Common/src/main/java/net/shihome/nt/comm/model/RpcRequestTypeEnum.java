package net.shihome.nt.comm.model;

import java.io.Serializable;

public enum RpcRequestTypeEnum implements Serializable {
  data(1, "DataService"),
  heartbeat(2, "HeartbeatService"),
  close(3, "CloseService");
  private final int type;
  private final String typeName;

  RpcRequestTypeEnum(int type, String typeName) {
    this.type = type;
    this.typeName = typeName;
  }

  public int getType() {
    return type;
  }

  public String getTypeName() {
    return typeName;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("RpcRequestTypeEnum{");
    sb.append("type=").append(type);
    sb.append(", typeName='").append(typeName).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
