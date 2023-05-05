package net.shihome.nt.server.tcp;

import net.shihome.nt.comm.lang.SlidingWindowList;
import net.shihome.nt.comm.model.data.DataEntry;

import java.io.Serializable;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class NettyTcpInstanceContext implements Serializable {

  /** pending sent for client */
  private final ConcurrentSkipListSet<DataEntry> pendingToSendSet =
      new ConcurrentSkipListSet<>(Comparator.comparingDouble(DataEntry::getSpanId));

  private final AtomicLong currentRequestSpanId = new AtomicLong(0);

  private final AtomicLong currentResponseSpanId = new AtomicLong(0);

  /** pending sent for Manager */
  private final ConcurrentSkipListSet<DataEntry> pendingToManageSet =
      new ConcurrentSkipListSet<>(Comparator.comparingDouble(DataEntry::getSpanId));

  private SlidingWindowList slidingWindowList = null;

  private String channelId;

  private Semaphore semaphore;

  private String instanceName;

  public Semaphore getSemaphore() {
    return semaphore;
  }

  public void setSemaphore(Semaphore semaphore) {
    this.semaphore = semaphore;
  }

  public ConcurrentSkipListSet<DataEntry> getPendingToSendSet() {
    return pendingToSendSet;
  }

  public AtomicLong getCurrentRequestSpanId() {
    return currentRequestSpanId;
  }

  public AtomicLong getCurrentResponseSpanId() {
    return currentResponseSpanId;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public void setInstanceName(String instanceName) {
    this.instanceName = instanceName;
  }

  public ConcurrentSkipListSet<DataEntry> getPendingToManageSet() {
    return pendingToManageSet;
  }

  public SlidingWindowList getSlidingWindowList() {
    return slidingWindowList;
  }

  public void setSlidingWindowList(SlidingWindowList slidingWindowList) {
    this.slidingWindowList = slidingWindowList;
  }
}
