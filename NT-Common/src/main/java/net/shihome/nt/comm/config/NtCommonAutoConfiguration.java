package net.shihome.nt.comm.config;

import com.caucho.hessian.io.Hessian2Input;
import net.shihome.nt.comm.service.RpcSerializer;
import net.shihome.nt.comm.service.impl.HessianSerializer;
import net.shihome.nt.comm.utils.IdUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.IdGenerator;
import org.springframework.util.SimpleIdGenerator;

@Configuration(proxyBeanMethods = false)
public class NtCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator() {
        return new SimpleIdGenerator();
    }

    @Bean
    IdUtil idUtil() {
        return new IdUtil() {
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(value = Hessian2Input.class)
    @ConditionalOnMissingBean(RpcSerializer.class)
    public static class CommonHessianAutoConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public RpcSerializer rpcSerializer() {
            return new HessianSerializer();
        }
    }
}
