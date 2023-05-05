package net.shihome.nt.comm.exception;

public class NtException extends RuntimeException {

  private ExceptionLevelEnum level = ExceptionLevelEnum.error;

  public NtException(ExceptionLevelEnum level, String message) {
    super(message);
    this.level = level;
  }

  public NtException(Throwable cause) {
    super(cause);
    this.level = ExceptionLevelEnum.error;
  }

  public ExceptionLevelEnum getLevel() {
    return level;
  }
}
