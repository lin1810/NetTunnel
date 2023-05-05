package net.shihome.nt.comm.netty.codec;

import java.util.Arrays;
import java.util.Objects;

public enum NettyCodecTypeEnum {
  server((byte) 0x0, "server"),
  client((byte) 0x1, "client");
  private byte type;
  private String name;

  NettyCodecTypeEnum(byte type, String name) {
    this.type = type;
    this.name = name;
  }

  public byte getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public static NettyCodecTypeEnum match(byte bt) {
    return Arrays.stream(NettyCodecTypeEnum.values())
        .filter(e1 -> Objects.equals(bt, e1.getType()))
        .findFirst()
        .orElse(null);
  }
}
