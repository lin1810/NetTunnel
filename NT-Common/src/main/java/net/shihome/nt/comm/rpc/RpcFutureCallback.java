package net.shihome.nt.comm.rpc;

import net.shihome.nt.comm.exception.NtException;

import java.util.function.Consumer;

public interface RpcFutureCallback<T> extends Consumer<T> {

  default void onSuccess(T result) {
    accept(result);
  }

  default void onFailure(Throwable throwable) {
    throw new NtException(throwable);
  }
}
