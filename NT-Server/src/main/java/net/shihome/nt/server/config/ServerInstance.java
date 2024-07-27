package net.shihome.nt.server.config;

public class ServerInstance {
  private boolean enableConnectionLog = true;
  private String instanceName;
  private int port;
  private int workGroupThreads = -1;
  private String address;
  private int permits = -1;
  private int permitsTimeoutInSecond = 15;
  private int slidingWindowSize = 10;
  private String accessIpRegion;

  public int getPermitsTimeoutInSecond() {
    return permitsTimeoutInSecond;
  }

  public void setPermitsTimeoutInSecond(int permitsTimeoutInSecond) {
    this.permitsTimeoutInSecond = permitsTimeoutInSecond;
  }

  public int getPermits() {
    return permits;
  }

  public void setPermits(int permits) {
    this.permits = permits;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public int getWorkGroupThreads() {
    return workGroupThreads;
  }

  public void setWorkGroupThreads(int workGroupThreads) {
    this.workGroupThreads = workGroupThreads;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public void setInstanceName(String instanceName) {
    this.instanceName = instanceName;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public boolean isEnableConnectionLog() {
    return enableConnectionLog;
  }

  public String getAccessIpRegion() {
    return accessIpRegion;
  }

  public void setAccessIpRegion(String accessIpRegion) {
    this.accessIpRegion = accessIpRegion;
  }

  public void setEnableConnectionLog(boolean enableConnectionLog) {
    this.enableConnectionLog = enableConnectionLog;
  }

  public int getSlidingWindowSize() {
    return slidingWindowSize;
  }

  public void setSlidingWindowSize(int slidingWindowSize) {
    this.slidingWindowSize = slidingWindowSize;
  }
}
