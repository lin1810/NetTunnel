package net.shihome.nt.server.config;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import net.shihome.nt.server.common.IpRegionUtils;
import net.shihome.nt.server.rpc.NettyManageServerStarter;
import net.shihome.nt.server.service.ServerService;
import net.shihome.nt.server.service.impl.CompositeServerService;
import net.shihome.nt.server.tcp.NettyTcpInstanceChannelHolder;
import net.shihome.nt.server.tcp.NettyTcpInstanceStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ServerProperties.class})
public class ServerAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ServerAutoConfiguration.class);

  @Bean(destroyMethod = "stopCallbackThreadPool")
  RpcResponseFutureHandler rpcResponseFutureHandler() {
    return new RpcResponseFutureHandler();
  }

  @Bean(name = "scheduledExecutorService", destroyMethod = "shutdown")
  ScheduledExecutorService scheduledExecutorService() {
    return ThreadPoolUtil.makeScheduledExecutorService(1, "Global-Traffic-");
  }

  @Bean
  GlobalTrafficShapingHandler globalTrafficShapingHandler(
      @Qualifier("scheduledExecutorService") ScheduledExecutorService scheduledExecutorService,
      ServerProperties serverProperties) {

    long writeLimit = (long) (serverProperties.getWriteLimit() * 1024 * 1024);
    long readLimit = (long) (serverProperties.getReadLimit() * 1024 * 1024);

    logger.info(
        "create rpc GlobalTrafficShapingHandler, writeLimit:{}, readLimit:{}",
        writeLimit,
        readLimit);

    GlobalTrafficShapingHandler globalTrafficShapingHandler =
        new GlobalTrafficShapingHandler(scheduledExecutorService, writeLimit, readLimit);
    if (serverProperties.isEnableTrafficMonitor()) {
      scheduledExecutorService.scheduleAtFixedRate(
          () -> {
            TrafficCounter trafficCounter = globalTrafficShapingHandler.trafficCounter();
            final long totalRead = trafficCounter.cumulativeReadBytes();
            final long totalWrite = trafficCounter.cumulativeWrittenBytes();
            logger.info(
                "tcp instance total read:{}, total write:{}, counter:{}",
                (totalRead >> 10) + " KB",
                (totalWrite >> 10) + " KB",
                trafficCounter);
          },
          serverProperties.getTrafficMonitorInSeconds(),
          serverProperties.getTrafficMonitorInSeconds(),
          TimeUnit.SECONDS);
    }
    return globalTrafficShapingHandler;
  }

  @Bean
  EventLoopGroup bossLoopGroup() {
    return new NioEventLoopGroup(1, new CustomizableThreadFactory("BOSS_"));
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(NettyManageServerStarter.class)
  protected static class ServerStarterAutoConfiguration {

    @Bean
    @Primary
    ServerService compositeServerService(
        ObjectProvider<ServerService> serverServiceObjectProvider) {
      Map<String, ServerService> collect =
          serverServiceObjectProvider.stream()
              .sorted(AnnotationAwareOrderComparator.INSTANCE)
              .collect(
                  () -> (Map<String, ServerService>) new HashMap<String, ServerService>(),
                  (map, service) ->
                      map.put(ClassUtils.getShortName(service.getClass().getName()), service),
                  Map::putAll);
      return new CompositeServerService(collect);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    NettyManageServerStarter nettyServerStarter(
        ServerService serverService,
        RpcResponseFutureHandler rpcResponseFutureHandler,
        EventLoopGroup bossLoopGroup) {
      return new NettyManageServerStarter(serverService, rpcResponseFutureHandler, bossLoopGroup);
    }
  }

  @Configuration(proxyBeanMethods = false)
  protected static class ServerInstanceAutoConfiguration {

    @Bean(destroyMethod = "stop", initMethod = "start")
    NettyTcpInstanceChannelHolder nettyTcpInstanceChannelHolder() {
      return new NettyTcpInstanceChannelHolder() {};
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    NettyTcpInstanceStarter tcpInstanceStarter(
        EventLoopGroup bossLoopGroup,
        ServerProperties serverProperties,
        NettyManageServerStarter nettyServerStarter,
        GlobalTrafficShapingHandler globalTrafficShapingHandler,
        RpcResponseFutureHandler rpcResponseFutureHandler,
        IpRegionUtils ipRegionUtils) {
      return new NettyTcpInstanceStarter(
          bossLoopGroup,
          serverProperties,
          nettyServerStarter,
          globalTrafficShapingHandler,
          rpcResponseFutureHandler,
          ipRegionUtils);
    }
  }
}
