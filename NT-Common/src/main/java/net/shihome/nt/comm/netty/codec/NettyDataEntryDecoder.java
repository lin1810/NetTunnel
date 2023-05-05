package net.shihome.nt.comm.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.shihome.nt.comm.model.data.DataEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NettyDataEntryDecoder extends ByteToMessageDecoder {

  private static final Logger logger = LoggerFactory.getLogger(NettyDataEntryDecoder.class);

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    try {
      int dataLength = in.readableBytes();
      if (dataLength == 0) {
        return;
      }
      byte[] data = new byte[dataLength];
      in.readBytes(data);
      DataEntry dataEntry = new DataEntry();
      dataEntry.setBytes(data);
      out.add(dataEntry);
    } catch (Throwable throwable) {
      logger.error("exception caught", throwable);
      ctx.close();
    }
  }
}
