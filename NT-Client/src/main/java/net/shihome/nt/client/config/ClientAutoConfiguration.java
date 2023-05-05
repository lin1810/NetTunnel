package net.shihome.nt.client.config;

import net.shihome.nt.client.rpc.NettyRpcClient;
import net.shihome.nt.client.service.ClientService;
import net.shihome.nt.client.service.impl.CompositeClientService;
import net.shihome.nt.client.tcp.NettyTcpInstanceClient;
import net.shihome.nt.comm.rpc.RpcResponseFutureHandler;
import net.shihome.nt.comm.service.RpcSerializer;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ClientProperties.class})
public class ClientAutoConfiguration {

  @Bean(name = "scheduledExecutorService", destroyMethod = "shutdown")
  ScheduledExecutorService scheduledExecutorService() {
    return ThreadPoolUtil.makeScheduledExecutorService(1, "Global-scheduled-");
  }

  @Bean
  @Primary
  ClientService compositeClientService(ObjectProvider<ClientService> serverServiceObjectProvider) {
    Map<String, ClientService> collect =
        serverServiceObjectProvider.stream()
            .sorted(AnnotationAwareOrderComparator.INSTANCE)
            .collect(
                () -> (Map<String, ClientService>) new HashMap<String, ClientService>(),
                (map, service) ->
                    map.put(ClassUtils.getShortName(service.getClass().getName()), service),
                (map1, map2) -> map1.putAll(map2));
    return new CompositeClientService(collect);
  }

  @Configuration(proxyBeanMethods = false)
  protected class NettyClientStarterAutoConfiguration {

    @Bean(destroyMethod = "stopCallbackThreadPool")
    RpcResponseFutureHandler rpcResponseFutureHandler() {
      return new RpcResponseFutureHandler();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    NettyRpcClient nettyClientStarter(
        RpcSerializer rpcSerializer,
        RpcResponseFutureHandler rpcResponseFutureHandler,
        ClientProperties clientProperties,
        ClientService clientService) {
      return new NettyRpcClient(
          rpcSerializer, rpcResponseFutureHandler, clientProperties, clientService);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    NettyTcpInstanceClient nettyTcpInstanceClient(
        ClientProperties clientProperties,
        NettyRpcClient nettyRpcClient,
        RpcResponseFutureHandler rpcResponseFutureHandler) {
      return new NettyTcpInstanceClient(
          clientProperties, nettyRpcClient, rpcResponseFutureHandler) {};
    }
  }
}
