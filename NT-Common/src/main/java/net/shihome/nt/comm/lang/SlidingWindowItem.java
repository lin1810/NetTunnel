package net.shihome.nt.comm.lang;

public class SlidingWindowItem {
  private boolean sendFlag = false;
  private boolean ackFlag = false;
  private Long spanId = null;

  public Long getSpanId() {
    return spanId;
  }

  public void setSpanId(Long spanId) {
    this.spanId = spanId;
  }

  public boolean isSendFlag() {
    return sendFlag;
  }

  public void setSendFlag(boolean sendFlag) {
    this.sendFlag = sendFlag;
  }

  public boolean isAckFlag() {
    return ackFlag;
  }

  public void setAckFlag(boolean ackFlag) {
    this.ackFlag = ackFlag;
  }

  public void reset() {
    setSendFlag(false);
    setAckFlag(false);
    setSpanId(null);
  }
}
