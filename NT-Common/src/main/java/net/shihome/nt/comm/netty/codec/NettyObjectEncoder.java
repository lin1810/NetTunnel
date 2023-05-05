package net.shihome.nt.comm.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.stream.ChunkedStream;
import net.shihome.nt.comm.service.RpcSerializer;

import java.util.List;

public class NettyObjectEncoder extends MessageToMessageEncoder<Object> {
  private final Class<?> genericClass;
  private final RpcSerializer serializer;
  private final NettyCodecTypeEnum codecType;

  public NettyObjectEncoder(
      Class<?> genericClass, final RpcSerializer serializer, NettyCodecTypeEnum codecType) {
    this.genericClass = genericClass;
    this.serializer = serializer;
    this.codecType = codecType;
  }

  @Override
  public boolean acceptOutboundMessage(Object msg) throws Exception {
    return super.acceptOutboundMessage(msg) && genericClass.isInstance(msg);
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
    byte[] data = serializer.serialize(msg);
    ByteBuf buffer = ctx.alloc().ioBuffer();
    buffer.writeByte(codecType.getType());
    buffer.writeInt(data.length);
    buffer.writeBytes(data);
    ChunkedStream chunkedStream = new ChunkedStream(new ByteBufInputStream(buffer, true));
    out.add(chunkedStream);
  }
}
