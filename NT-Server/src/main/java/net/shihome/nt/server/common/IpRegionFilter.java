package net.shihome.nt.server.common;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ipfilter.AbstractRemoteAddressFilter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class IpRegionFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private static final Logger logger = LoggerFactory.getLogger(IpRegionFilter.class);

    private final IpRegionUtils regionUtils;
    private final String[] allowList;

    public IpRegionFilter(IpRegionUtils regionUtils, String allowList) {
        this.regionUtils = regionUtils;
        String[] split = null;
        if (StringUtils.isNotEmpty(allowList))
        {
            split = StringUtils.split(allowList, "|");
            Arrays.sort(split, String::compareTo);
        }
        this.allowList = split;
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) throws Exception {
        if (allowList == null || allowList.length == 0) {
            logger.debug("region list is empty, allow all region to access");
            return true;
        }
        return regionUtils.match(remoteAddress, allowList);
    }
}
