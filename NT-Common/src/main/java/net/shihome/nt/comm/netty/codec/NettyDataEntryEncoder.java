package net.shihome.nt.comm.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.stream.ChunkedStream;
import net.shihome.nt.comm.model.data.DataEntry;

import java.io.ByteArrayInputStream;
import java.util.List;

public class NettyDataEntryEncoder extends MessageToMessageEncoder<DataEntry> {

  @Override
  protected void encode(ChannelHandlerContext ctx, DataEntry msg, List<Object> out) {
    if (msg != null) {
      byte[] bytes = msg.getBytes();
      if (bytes == null || bytes.length == 0) {
        return;
      }

      ByteArrayInputStream in = new ByteArrayInputStream(bytes);
      ChunkedStream chunkedStream = new ChunkedStream(in);
      out.add(chunkedStream);
    }
  }
}
