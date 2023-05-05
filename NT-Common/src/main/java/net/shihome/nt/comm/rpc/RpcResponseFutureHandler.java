package net.shihome.nt.comm.rpc;

import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import net.shihome.nt.comm.model.RpcRequest;
import net.shihome.nt.comm.model.RpcResponse;
import net.shihome.nt.comm.utils.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadPoolExecutor;

public class RpcResponseFutureHandler {

  private static final Logger logger = LoggerFactory.getLogger(RpcResponseFutureHandler.class);

  private final ConcurrentMap<String, RpcFutureResponse> futureResponsePool =
      new ConcurrentHashMap<>();

  private ThreadPoolExecutor responseCallbackThreadPool = null;

  public RpcFutureResponse createResponseFuture(RpcRequest request, RpcFutureCallback callback) {
    return createResponseFuture(request, null, callback);
  }

  public RpcFutureResponse createResponseFuture(
      RpcRequest request, String requestId, RpcFutureCallback callback) {
    return new RpcFutureResponse(this, request, requestId, callback);
  }

  protected void setInvokerFuture(String requestId, RpcFutureResponse futureResponse) {
    futureResponsePool.put(requestId, futureResponse);
  }

  public void removeInvokerFuture(String requestId) {
    futureResponsePool.remove(requestId);
  }

  public void notifyInvokerFuture(String requestId, final RpcResponse jobsRpcResponse) {

    // get
    final RpcFutureResponse futureResponse = futureResponsePool.get(requestId);
    if (futureResponse == null) {
      return;
    }
    // notify
    if (futureResponse.getInvokeCallback() != null) {
      // callback type
      try {
        executeResponseCallback(
            () -> {
              if (jobsRpcResponse.getErrorMsg() != null) {
                futureResponse
                    .getInvokeCallback()
                    .onFailure(
                        new NtException(ExceptionLevelEnum.warn, jobsRpcResponse.getErrorMsg()));
              } else {
                futureResponse.getInvokeCallback().onSuccess(jobsRpcResponse);
              }
            });
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    } else {
      // other nomal type
      futureResponse.setResponse(jobsRpcResponse);
    }

    // do remove
    futureResponsePool.remove(requestId);
  }

  public void executeResponseCallback(Runnable runnable) {
    if (responseCallbackThreadPool == null) {
      synchronized (this) {
        if (responseCallbackThreadPool == null) {
          responseCallbackThreadPool =
              ThreadPoolUtil.makeServerThreadPool(
                  "JobsRpcInvokerFactory-responseCallbackThreadPool");
        }
      }
    }
    responseCallbackThreadPool.execute(runnable);
  }

  public void stopCallbackThreadPool() {
    if (responseCallbackThreadPool != null) {
      responseCallbackThreadPool.shutdown();
    }
  }
}
