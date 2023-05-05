package net.shihome.nt.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.util.List;

@ConfigurationProperties(prefix = ClientProperties.prefix)
public class ClientProperties {
  protected static final String prefix = "nt.client";

  private int maxConnectionInPool = 300;
  private int minConnectionInPool = 30;
  private int maxTcpInstanceConnections = 300;
  private boolean schedulerHealthCheck = true;
  private int schedulerMinIdleCheckPerSecond = 5;
  private int schedulerHealthcarePerSecond = 180;
  private List<ClientInstance> instanceList;
  private String serverAddress;

  private File clientCertPath;
  private File clientKeyPath;
  private String clientKeyPassword;
  private File caPath;
  private int workGroupThreads = -1;

  public int getWorkGroupThreads() {
    return workGroupThreads;
  }

  public void setWorkGroupThreads(int workGroupThreads) {
    this.workGroupThreads = workGroupThreads;
  }

  public String getClientKeyPassword() {
    return clientKeyPassword;
  }

  public void setClientKeyPassword(String clientKeyPassword) {
    this.clientKeyPassword = clientKeyPassword;
  }

  public File getClientCertPath() {
    return clientCertPath;
  }

  public void setClientCertPath(File clientCertPath) {
    this.clientCertPath = clientCertPath;
  }

  public File getClientKeyPath() {
    return clientKeyPath;
  }

  public void setClientKeyPath(File clientKeyPath) {
    this.clientKeyPath = clientKeyPath;
  }

  public File getCaPath() {
    return caPath;
  }

  public void setCaPath(File caPath) {
    this.caPath = caPath;
  }

  public int getMaxTcpInstanceConnections() {
    return maxTcpInstanceConnections;
  }

  public void setMaxTcpInstanceConnections(int maxTcpInstanceConnections) {
    this.maxTcpInstanceConnections = maxTcpInstanceConnections;
  }

  public List<ClientInstance> getInstanceList() {
    return instanceList;
  }

  public void setInstanceList(List<ClientInstance> instanceList) {
    this.instanceList = instanceList;
  }

  public int getSchedulerMinIdleCheckPerSecond() {
    return schedulerMinIdleCheckPerSecond;
  }

  public void setSchedulerMinIdleCheckPerSecond(int schedulerMinIdleCheckPerSecond) {
    this.schedulerMinIdleCheckPerSecond = schedulerMinIdleCheckPerSecond;
  }

  public int getSchedulerHealthcarePerSecond() {
    return schedulerHealthcarePerSecond;
  }

  public void setSchedulerHealthcarePerSecond(int schedulerHealthcarePerSecond) {
    this.schedulerHealthcarePerSecond = schedulerHealthcarePerSecond;
  }

  public String getServerAddress() {
    return serverAddress;
  }

  public void setServerAddress(String serverAddress) {
    this.serverAddress = serverAddress;
  }

  public int getMinConnectionInPool() {
    return minConnectionInPool;
  }

  public void setMinConnectionInPool(int minConnectionInPool) {
    this.minConnectionInPool = minConnectionInPool;
  }

  public boolean isSchedulerHealthCheck() {
    return schedulerHealthCheck;
  }

  public void setSchedulerHealthCheck(boolean schedulerHealthCheck) {
    this.schedulerHealthCheck = schedulerHealthCheck;
  }

  public int getMaxConnectionInPool() {
    return maxConnectionInPool;
  }

  public void setMaxConnectionInPool(int maxConnectionInPool) {
    this.maxConnectionInPool = maxConnectionInPool;
  }
}
