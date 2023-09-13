package net.shihome.nt.comm.utils;

import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import net.shihome.nt.comm.exception.ExceptionLevelEnum;
import net.shihome.nt.comm.exception.NtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

public class ExceptionUtil {
  private static final Logger logger = LoggerFactory.getLogger(ExceptionUtil.class);

  public static Throwable unwrapThrowable(Throwable wrappedThrowable) {
    Throwable unwrapped = wrappedThrowable;

    while (true) {
      while (!(unwrapped instanceof InvocationTargetException)) {
        if (!(unwrapped instanceof UndeclaredThrowableException)) {
          return unwrapped;
        }

        unwrapped = ((UndeclaredThrowableException) unwrapped).getUndeclaredThrowable();
      }
      unwrapped = ((InvocationTargetException) unwrapped).getTargetException();
    }
  }

  public static Throwable getRootCause(@Nullable Throwable original) {
    if (original == null) {
      return null;
    }
    Throwable rootCause = null;
    Throwable cause = unwrapThrowable(original);
    while (cause != null && cause != rootCause) {
      rootCause = unwrapThrowable(cause);
      cause = cause.getCause();
    }
    return rootCause;
  }

  public static String getErrorInfo(Throwable t) {
    try {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      getRootCause(t).printStackTrace(pw);
      String str = sw.toString();
      sw.close();
      pw.close();
      return str;
    } catch (Exception ex) {
      String errorMessage = "get error info failed, " + ex;
      logger.error(errorMessage, ex);
      return errorMessage;
    }
  }

  public static void printException(
      Logger logger1, String message, Object[] param, Throwable throwable) {
    printException(logger1, message, param, throwable, ExceptionLevelEnum.warn);
  }

  public static void printException(
      Logger logger1,
      String message,
      Object[] param,
      Throwable throwable,
      ExceptionLevelEnum defaultLevel) {
    ExceptionLevelEnum level = defaultLevel;
    if (throwable instanceof NtException) {
      NtException throwable1 = (NtException) throwable;
      level = throwable1.getLevel();
    }
    switch (level) {
      case info:
        logger1.info(message + ", cause " + throwable, param);
        break;
      case warn:
        logger1.warn(message + ", cause " + throwable, param);
        break;
      case error:
        logger1.error(message + ", cause " + throwable, param);
        break;
    }
    handleException(logger1, throwable);
  }

  public static void handleException(Logger logger1, Throwable throwable) {
    if (throwable instanceof NullPointerException) {
      logger1.warn("null pointer exception caught", throwable);
    } else if (throwable instanceof DecoderException) {
      DecoderException decoderException = (DecoderException) throwable;
      Throwable cause = decoderException.getCause();
      if (cause instanceof NotSslRecordException) {
        NotSslRecordException notSslException = (NotSslRecordException) cause;
        String message1 = notSslException.getMessage();
        message1 = StringUtils.delete(message1, "not an SSL/TLS record: ");
        logger1.info(
            "received unknown message form client, request message: {}",
            new String(ByteBufUtil.decodeHexDump(message1)));
      }
    }
  }
}
