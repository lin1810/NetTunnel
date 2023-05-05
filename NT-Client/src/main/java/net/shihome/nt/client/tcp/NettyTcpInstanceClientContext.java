package net.shihome.nt.client.tcp;

import net.shihome.nt.comm.lang.SlidingWindowList;
import net.shihome.nt.comm.model.data.DataEntry;

import java.io.Serializable;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class NettyTcpInstanceClientContext implements Serializable {

  /** pending sent for Manager Server */
  private final ConcurrentSkipListSet<DataEntry> pendingToManageSet =
      new ConcurrentSkipListSet<>(Comparator.comparingDouble(DataEntry::getSpanId));

  private String channelId;
  private AtomicLong currentRequestSpanId = new AtomicLong(0);
  private AtomicLong currentResponseSpanId = new AtomicLong(0);
  /** pending sent for client */
  private ConcurrentSkipListSet<DataEntry> pendingToSendSet =
      new ConcurrentSkipListSet<>(Comparator.comparingDouble(DataEntry::getSpanId));

  private Semaphore semaphore;

  private SlidingWindowList slidingWindowList = null;

  public SlidingWindowList getSlidingWindowList() {
    return slidingWindowList;
  }

  public void setSlidingWindowList(SlidingWindowList slidingWindowList) {
    this.slidingWindowList = slidingWindowList;
  }

  public Semaphore getSemaphore() {
    return semaphore;
  }

  public void setSemaphore(Semaphore semaphore) {
    this.semaphore = semaphore;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public AtomicLong getCurrentRequestSpanId() {
    return currentRequestSpanId;
  }

  public void setCurrentRequestSpanId(AtomicLong currentRequestSpanId) {
    this.currentRequestSpanId = currentRequestSpanId;
  }

  public AtomicLong getCurrentResponseSpanId() {
    return currentResponseSpanId;
  }

  public void setCurrentResponseSpanId(AtomicLong currentResponseSpanId) {
    this.currentResponseSpanId = currentResponseSpanId;
  }

  public ConcurrentSkipListSet<DataEntry> getPendingToSendSet() {
    return pendingToSendSet;
  }

  public void setPendingToSendSet(ConcurrentSkipListSet<DataEntry> pendingToSendSet) {
    this.pendingToSendSet = pendingToSendSet;
  }

  public void clear() {
    pendingToSendSet.clear();
  }

  public ConcurrentSkipListSet<DataEntry> getPendingToManageSet() {
    return pendingToManageSet;
  }
}
