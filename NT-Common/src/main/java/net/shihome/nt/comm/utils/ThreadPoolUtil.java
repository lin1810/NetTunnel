package net.shihome.nt.comm.utils;

import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolUtil {
  public static ThreadPoolExecutor makeServerThreadPool(String threadName) {
    AtomicInteger atomicInteger = new AtomicInteger(0);
    return new ThreadPoolExecutor(
        0,
        512,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        r -> new Thread(r, threadName + "-" + atomicInteger.getAndIncrement()),
        (r, executor) -> {
          throw new NtException(ExceptionLevelEnum.warn, threadName + " Thread pool is EXHAUSTED!");
        });
  }

  public static ScheduledExecutorService makeScheduledExecutorService(
      int corePoolSize, String threadName) {
    AtomicInteger atomicInteger = new AtomicInteger(0);
    if (corePoolSize <= 0) {
      corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
    }
    return new ScheduledThreadPoolExecutor(
        corePoolSize,
        r -> new Thread(r, threadName + "-" + atomicInteger.getAndIncrement()),
        (r, executor) -> {
          throw new NtException(ExceptionLevelEnum.warn, threadName + " Thread pool is EXHAUSTED!");
        });
  }

  public static ScheduledExecutorService makeScheduledExecutorService(String threadName) {
    return makeScheduledExecutorService(0, threadName);
  }
}
