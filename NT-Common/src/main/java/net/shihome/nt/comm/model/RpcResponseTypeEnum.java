package net.shihome.nt.comm.model;

import java.io.Serializable;

public enum RpcResponseTypeEnum implements Serializable {
  data((byte) 0x0, "data"),
  ack((byte) 0x1, "ack"),
  heartbeat((byte) 0x2, "heartbeat");
  private byte type;
  private String name;

  RpcResponseTypeEnum(byte type, String name) {
    this.type = type;
    this.name = name;
  }

  public byte getType() {
    return type;
  }

  public String getName() {
    return name;
  }
}
