package net.shihome.nt.comm.model.data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class DataEntry implements Serializable {
  private String channelId;
  private byte[] bytes;
  private String instanceName;
  private Long spanId;
  private boolean close = false;

  public Long getSpanId() {
    return spanId;
  }

  public void setSpanId(Long spanId) {
    this.spanId = spanId;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public void setInstanceName(String instanceName) {
    this.instanceName = instanceName;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public void setBytes(byte[] bytes) {
    this.bytes = bytes;
  }

  public boolean isClose() {
    return close;
  }

  public void setClose(boolean close) {
    this.close = close;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DataEntry dataEntry = (DataEntry) o;
    return close == dataEntry.close
        && Objects.equals(channelId, dataEntry.channelId)
        && Arrays.equals(bytes, dataEntry.bytes)
        && Objects.equals(instanceName, dataEntry.instanceName)
        && Objects.equals(spanId, dataEntry.spanId);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(channelId, instanceName, spanId, close);
    result = 31 * result + Arrays.hashCode(bytes);
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("DataEntry{");
    sb.append("channelId='").append(channelId).append('\'');
    sb.append(", bytes=").append(Arrays.toString(bytes));
    sb.append(", instanceName='").append(instanceName).append('\'');
    sb.append(", spanId=").append(spanId);
    sb.append(", close=").append(close);
    sb.append('}');
    return sb.toString();
  }
}
