package net.shihome.nt.comm.service;

public interface RpcSerializer {
  <T> byte[] serialize(T obj);

  <T> Object deserialize(byte[] bytes, Class<T> clazz);
}
