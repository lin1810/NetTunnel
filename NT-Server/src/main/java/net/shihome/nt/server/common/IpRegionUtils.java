package net.shihome.nt.server.common;

import jakarta.annotation.Resource;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.utils.ExceptionUtil;
import net.shihome.nt.server.config.ServerProperties;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.StringTokenizer;

@Component
public class IpRegionUtils implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(IpRegionUtils.class);

    @Resource
    private ServerProperties serverProperties;

    private Searcher searcher;

    @Override
    public void afterPropertiesSet() throws Exception {
        File ipRegionPath = serverProperties.getIpRegionPath();

        byte[] cBuff;
        try (RandomAccessFile file = new RandomAccessFile(ipRegionPath, "r")) {
            cBuff = Searcher.loadContent(file);
        } catch (Exception throwable) {
            ExceptionUtil.printException(logger, "failed to load content from [{}]", new Object[]{ipRegionPath}, throwable);
            return;
        }

        // 2、使用上述的 cBuff 创建一个完全基于内存的查询对象。
        try {
            searcher = Searcher.newWithBuffer(cBuff);
        } catch (Exception throwable) {
            ExceptionUtil.printException(logger, "failed to create content cached searcher", null, throwable);
        }
    }

    public boolean match(InetSocketAddress remoteAddress, String[] allowList) {
        String hostAddress = remoteAddress.getAddress().getHostAddress();
        if (ArrayUtils.isEmpty(allowList)) {
            return false;
        }
        try {
            if (searcher == null) {
                throw new NtException(ExceptionLevelEnum.error, "ip region searcher is null");
            }
            String search = searcher.search(hostAddress);
            if (StringUtils.isNotEmpty(search)) {
                StringTokenizer stringTokenizer = new StringTokenizer(search, "|");
                String region = stringTokenizer.hasMoreTokens() ? stringTokenizer.nextToken() : "";
                int index = Arrays.binarySearch(allowList, region, String::compareTo);
                boolean found = (index >= 0);
                logger.info("ip:{}, region:{}, found:{}", hostAddress, search, found);
                return found;
            }
            logger.warn("ip:{}, search ip region is empty", hostAddress);
            return false;
        } catch (Exception e) {
            logger.error("can not search address:{}", hostAddress, e);
            return false;
        }
    }


    @Override
    public void destroy() throws Exception {
        if (searcher != null) {
            searcher.close();
        }

    }
}
