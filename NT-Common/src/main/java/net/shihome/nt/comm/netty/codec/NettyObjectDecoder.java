package net.shihome.nt.comm.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.shihome.nt.comm.service.RpcSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NettyObjectDecoder extends ByteToMessageDecoder {
  private static final Logger logger = LoggerFactory.getLogger(NettyObjectDecoder.class);
  private final RpcSerializer serializer;

  Map<NettyCodecTypeEnum, Class<?>> genericClassMap;

  public NettyObjectDecoder(
      final RpcSerializer serializer, Map<NettyCodecTypeEnum, Class<?>> genericClassMap) {
    this.serializer = serializer;
    this.genericClassMap = genericClassMap;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (in.readableBytes() < 5) {
      return;
    }
    in.markReaderIndex();
    byte type = in.readByte();
    NettyCodecTypeEnum match = NettyCodecTypeEnum.match(type);
    if (match != null && genericClassMap.containsKey(match)) {
      int dataLength = in.readInt();
      if (dataLength < 0) {
        logger.warn("data length is empty, dataLength[{}]", dataLength);
        ctx.close();
      }
      if (in.readableBytes() < dataLength) {
        in.resetReaderIndex();
        return; // fix 1024k buffer splice limix
      }
      byte[] data = new byte[dataLength];
      in.readBytes(data);

      Object obj = serializer.deserialize(data, genericClassMap.get(match));
      out.add(obj);
    } else {
      logger.warn("un-match NettyCodecTypeEnum[{}], type[{}]", match, type);
      ctx.close();
      // fix 1024k buffer splice limix
    }
  }
}
