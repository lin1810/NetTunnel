package net.shihome.nt.comm.rpc;

import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RpcFutureResponse implements Future<RpcResponse> {

  private final RpcFutureCallback callback;
  private final RpcResponseFutureHandler futureHandler;
  private final RpcRequest request;
  private final Object lock = new Object();
  private final String requestId;
  private RpcResponse response;
  private boolean done = false;

  public RpcFutureResponse(
      final RpcResponseFutureHandler futureHandler,
      RpcRequest request,
      String requestId,
      RpcFutureCallback callback) {
    this.futureHandler = futureHandler;
    this.request = request;
    this.requestId = Optional.ofNullable(requestId).orElseGet(() -> request.getRequestId());
    this.callback = callback;

    // set-InvokerFuture
    setInvokerFuture();
  }

  public void setInvokerFuture() {
    this.futureHandler.setInvokerFuture(requestId, this);
  }

  public void removeInvokerFuture() {
    this.futureHandler.removeInvokerFuture(requestId);
  }

  public RpcRequest getRequest() {
    return request;
  }

  public RpcFutureCallback getInvokeCallback() {
    return callback;
  }

  public void setResponse(RpcResponse response) {
    this.response = response;
    synchronized (lock) {
      done = true;
      lock.notifyAll();
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return done;
  }

  @Override
  public RpcResponse get() throws InterruptedException, ExecutionException {
    try {
      return get(-1, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new NtException(e);
    }
  }

  @Override
  public RpcResponse get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (!done) {
      synchronized (lock) {
        try {
          if (timeout < 0) {
            lock.wait();
          } else {
            long timeoutMillis =
                (TimeUnit.MILLISECONDS == unit)
                    ? timeout
                    : TimeUnit.MILLISECONDS.convert(timeout, unit);
            lock.wait(timeoutMillis);
          }
        } catch (InterruptedException e) {
          throw e;
        }
      }
    }

    if (!done) {
      throw new NtException(
          ExceptionLevelEnum.warn,
          "rpc, request timeout at:"
              + System.currentTimeMillis()
              + ", request:"
              + request.toString());
    }
    return response;
  }
}
