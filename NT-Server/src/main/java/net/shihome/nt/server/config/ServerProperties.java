package net.shihome.nt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.util.List;

@ConfigurationProperties(prefix = ServerProperties.prefix)
public class ServerProperties {
  protected static final String prefix = "nt.server";

  private String ip;
  private int port;

  private File serverCertPath;
  private File serverKeyPath;
  /**
   * ip2region.xdb path
   */
  private File ipRegionPath;
  private String accessIpRegion;
  private String serverKeyPassword;
  private File caPath;
  private double writeLimit = 0;
  private double readLimit = 0;
  private boolean enableTrafficMonitor = false;
  private int trafficMonitorInSeconds = 600;
  private List<ServerInstance> instanceList;

  public File getIpRegionPath() {
    return ipRegionPath;
  }

  public void setIpRegionPath(File ipRegionPath) {
    this.ipRegionPath = ipRegionPath;
  }

  public String getAccessIpRegion() {
    return accessIpRegion;
  }

  public void setAccessIpRegion(String accessIpRegion) {
    this.accessIpRegion = accessIpRegion;
  }

  public int getTrafficMonitorInSeconds() {
    return trafficMonitorInSeconds;
  }

  public void setTrafficMonitorInSeconds(int trafficMonitorInSeconds) {
    this.trafficMonitorInSeconds = trafficMonitorInSeconds;
  }

  public boolean isEnableTrafficMonitor() {
    return enableTrafficMonitor;
  }

  public void setEnableTrafficMonitor(boolean enableTrafficMonitor) {
    this.enableTrafficMonitor = enableTrafficMonitor;
  }

  public double getWriteLimit() {
    return writeLimit;
  }

  public void setWriteLimit(double writeLimit) {
    this.writeLimit = writeLimit;
  }

  public double getReadLimit() {
    return readLimit;
  }

  public void setReadLimit(double readLimit) {
    this.readLimit = readLimit;
  }

  public String getServerKeyPassword() {
    return serverKeyPassword;
  }

  public void setServerKeyPassword(String serverKeyPassword) {
    this.serverKeyPassword = serverKeyPassword;
  }

  public File getServerCertPath() {
    return serverCertPath;
  }

  public void setServerCertPath(File serverCertPath) {
    this.serverCertPath = serverCertPath;
  }

  public File getServerKeyPath() {
    return serverKeyPath;
  }

  public void setServerKeyPath(File serverKeyPath) {
    this.serverKeyPath = serverKeyPath;
  }

  public File getCaPath() {
    return caPath;
  }

  public void setCaPath(File caPath) {
    this.caPath = caPath;
  }

  public List<ServerInstance> getInstanceList() {
    return instanceList;
  }

  public void setInstanceList(List<ServerInstance> instanceList) {
    this.instanceList = instanceList;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }
}
