package net.shihome.nt.client.config;

public class ClientInstance {

  private String instanceName;

  private String address;

  private int permits = -1;
  private int permitsTimeoutInSecond = 15;
  private int slidingWindowSize = 30;

  public int getPermits() {
    return permits;
  }

  public void setPermits(int permits) {
    this.permits = permits;
  }

  public int getPermitsTimeoutInSecond() {
    return permitsTimeoutInSecond;
  }

  public void setPermitsTimeoutInSecond(int permitsTimeoutInSecond) {
    this.permitsTimeoutInSecond = permitsTimeoutInSecond;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public void setInstanceName(String instanceName) {
    this.instanceName = instanceName;
  }

  public int getSlidingWindowSize() {
    return slidingWindowSize;
  }

  public void setSlidingWindowSize(int slidingWindowSize) {
    this.slidingWindowSize = slidingWindowSize;
  }
}
