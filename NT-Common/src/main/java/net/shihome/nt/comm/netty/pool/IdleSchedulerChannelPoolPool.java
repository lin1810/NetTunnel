package net.shihome.nt.comm.netty.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.utils.ExceptionUtil;
import org.apache.commons.lang.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public class IdleSchedulerChannelPoolPool extends FixedChannelPool {

  private static final Logger logger = LoggerFactory.getLogger(IdleSchedulerChannelPoolPool.class);
  private static final AttributeKey<SimpleChannelPool> POOL_KEY =
      AttributeKey.valueOf("io.netty.channel.pool.SimpleChannelPool");

  private final int minConnections;

  private final EventExecutor schedulerExecutor;
  private final Bootstrap bootstrap;
  private final Field dequeField = FieldUtils.getField(SimpleChannelPool.class, "deque", true);

  public IdleSchedulerChannelPoolPool(
      Bootstrap bootstrap,
      ChannelPoolHandler handler,
      int maxConnections,
      int minConnections,
      int schedulerMinIdleCheckPerSecond,
      int schedulerHealthcarePerSecond,
      boolean schedulerHealthCheck) {
    super(
        bootstrap,
        handler,
        ChannelHealthChecker.ACTIVE,
        null,
        -1,
        maxConnections,
        Integer.MAX_VALUE,
        true,
        false);

    this.minConnections = minConnections;
    this.schedulerExecutor = bootstrap.config().group().next();
    this.bootstrap = checkNotNull(bootstrap, "bootstrap").clone();
    this.bootstrap.handler(
        new ChannelInitializer<Channel>() {
          @Override
          protected void initChannel(Channel ch) throws Exception {
            assert ch.eventLoop().inEventLoop();
            handler.channelCreated(ch);
          }
        });
    setSchedulerMinIdleCheck(schedulerMinIdleCheckPerSecond);
    if (schedulerHealthCheck) {
      setSchedulerHealthCheck(schedulerHealthcarePerSecond);
    }
  }

  protected int getPendingAcquireQueueSize() {
    Deque<Channel> queue = null;
    try {
      queue = (Deque<Channel>) FieldUtils.readField(dequeField, this, true);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return queue == null ? 0 : queue.size();
  }

  protected int currentConnection() {
    return getPendingAcquireQueueSize();
  }

  protected void healthCheckCurrentConnection() {
    try {
      int count = getPendingAcquireQueueSize();
      while (count > 0) {
        Channel channel = super.pollChannel();
        final Future<Boolean> f = healthChecker().isHealthy(channel);
        f.addListener(
            (FutureListener<Boolean>)
                f1 -> {
                  if (f1.get()) {
                    offerChannel(channel);
                  } else {
                    if (logger.isInfoEnabled()) {
                      logger.info("close un-health channel, channel[{}]", channel);
                    }
                    channel.close();
                  }
                });
        count--;
      }
    } catch (Throwable throwable) {
      logger.error("exception caught in healthCheckCurrentConnection", throwable);
    }
  }

  protected void minIdleConnectionScheduler() {
    try {
      logger.debug("minIdleConnectionScheduler.start");
      Future<Channel> acquire = acquire();
      acquire.addListener(
          (FutureListener<Channel>)
              f1 -> {
                if (f1.isDone()) {
                  minIdleConnectionScheduler0();
                  if (f1.isSuccess()) {
                    release(f1.get());
                  }
                }
              });
    } catch (Throwable throwable) {
      logger.error("exception caught in minIdleConnectionScheduler", throwable);
    }
  }

  private void minIdleConnectionScheduler0() {
    int currentConnection = currentConnection();
    if (currentConnection < minConnections) {
      logger.info(
          "current connections({}) is less then min idle connection({}), starting connect to server",
          currentConnection,
          minConnections);
      for (int i = currentConnection; i < minConnections; i++) {
        Bootstrap bs = bootstrap.clone();
        bs.attr(POOL_KEY, this);
        ChannelFuture f = connectChannel(bs);

        f.addListener(
            (ChannelFutureListener)
                f1 -> {
                  if (f1.isSuccess()) {
                    Channel channel = f1.channel();
                    offerChannel(channel);
                    logger.info(
                        "current connections({}), new connection:{}", currentConnection(), channel);
                  } else {
                    ExceptionUtil.printException(
                        logger, "connection failed", null, f1.cause(), ExceptionLevelEnum.warn);
                  }
                });
      }
    }
  }

  protected void setSchedulerMinIdleCheck(int delay) {
    schedulerExecutor.scheduleWithFixedDelay(
        this::minIdleConnectionScheduler, 1, delay, TimeUnit.SECONDS);
  }

  protected void setSchedulerHealthCheck(int delay) {
    schedulerExecutor.scheduleWithFixedDelay(
        this::healthCheckCurrentConnection, delay / 2, delay, TimeUnit.SECONDS);
  }
}
