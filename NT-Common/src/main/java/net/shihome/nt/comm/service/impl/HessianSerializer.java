package net.shihome.nt.comm.service.impl;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.service.RpcSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HessianSerializer implements RpcSerializer {
  @Override
  public <T> byte[] serialize(T obj) {

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    Hessian2Output hessianOutput = new Hessian2Output(byteArrayOutputStream);
    try {
      hessianOutput.writeObject(obj);
      hessianOutput.close();
      hessianOutput = null;
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new NtException(e);
    } finally {
      try {
        byteArrayOutputStream.close();
      } catch (IOException e) {
        throw new NtException(e);
      }
      if (hessianOutput != null) {
        try {
          hessianOutput.close();
        } catch (IOException e) {
          throw new NtException(e);
        }
      }
    }
  }

  @Override
  public <T> Object deserialize(byte[] bytes, Class<T> clazz) {
    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
    Hessian2Input hessian2Input = new Hessian2Input(byteArrayInputStream);
    try {
      return hessian2Input.readObject(clazz);
    } catch (IOException e) {
      throw new NtException(e);
    } finally {
      try {
        hessian2Input.close();
        byteArrayInputStream.close();
      } catch (IOException e) {
        throw new NtException(e);
      }
    }
  }
}
