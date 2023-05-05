package net.shihome.nt.comm.lang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SlidingWindowList {

  private static final Logger logger = LoggerFactory.getLogger(SlidingWindowList.class);

  private final List<SlidingWindowItem> list;
  private Long lastSpanId = 0L;
  private int index = 0;
  private ReentrantReadWriteLock.WriteLock writeLock = null;
  private ReentrantReadWriteLock.ReadLock readLock = null;

  public SlidingWindowList(int size) {
    this.list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(new SlidingWindowItem());
    }
    ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    readLock = readWriteLock.readLock();
    writeLock = readWriteLock.writeLock();
  }

  public boolean add(Long spanId) {
    readLock.lock();
    try {
      logger.debug("add span id[{}]", spanId);
      long min = lastSpanId;
      int index = getIndex();
      SlidingWindowItem lastItem = list.get(index);
      int size = list.size();
      if (lastItem.getSpanId() != null) min = lastItem.getSpanId();
      if (spanId >= min && spanId < (min + size)) {
        SlidingWindowItem currentItem = list.get((int) Math.floorMod(spanId, size));
        if (currentItem.isSendFlag()) {
          logger.warn(
              "current spanId is send, spanId:{}, item spanId:{}", spanId, currentItem.getSpanId());
          return false;
        }
        currentItem.setSendFlag(true);
        currentItem.setSpanId(spanId);
        logger.debug("add success, index[{}], span id[{}]", index, spanId);
        return true;
      } else {
        logger.debug("add failed, index[{}], span id[{}]", index, spanId);
        return false;
      }
    } finally {
      readLock.unlock();
    }
  }

  public boolean remove(Long spanId) {
    readLock.lock();
    try {
      logger.debug("remove span id[{}]", spanId);
      int size = list.size();
      SlidingWindowItem currentItem = list.get((int) Math.floorMod(spanId, size));
      if (!Objects.equals(spanId, currentItem.getSpanId())) {
        logger.warn("unexpect spanId:{}, spanId in list:{}", spanId, currentItem.getSpanId());
        return false;
      }
      if (currentItem.isAckFlag() || !currentItem.isSendFlag()) {
        logger.warn(
            "invalid flag, ack:{}, send:{}, spanId:{}",
            currentItem.isAckFlag(),
            currentItem.isSendFlag(),
            spanId);
        return false;
      }
      currentItem.setAckFlag(true);
    } finally {
      readLock.unlock();
    }
    return resetIndex();
  }

  public boolean resetIndex() {
    writeLock.lock();
    try {
      SlidingWindowItem currentItem;
      boolean result = false;
      while ((currentItem = list.get(index)).isAckFlag() && currentItem.isSendFlag()) {
        if (!result) result = true;
        if (logger.isDebugEnabled()) {
          logger.debug("reset item, index[{}], span id[{}]", getIndex(), currentItem.getSpanId());
        }
        currentItem.reset();
        index = Math.floorMod(index + 1, list.size());
        lastSpanId++;
      }
      if (logger.isDebugEnabled()) {
        logger.debug("reset index-> index[{}], result[{}]", getIndex(), result);
      }
      return result;
    } finally {
      writeLock.unlock();
    }
  }

  public int getIndex() {
    return index;
  }

  public int getSize() {
    return list.size();
  }
}
