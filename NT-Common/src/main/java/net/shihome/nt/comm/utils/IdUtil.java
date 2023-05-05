package net.shihome.nt.comm.utils;

import org.springframework.util.IdGenerator;

import javax.annotation.Resource;

public class IdUtil {

  @Resource private IdGenerator idGenerator;

  private static IdUtil INSTANCE;

  protected IdUtil() {
    INSTANCE = this;
  }

  public static String getNextId() {
    return INSTANCE.idGenerator.generateId().toString();
  }
}
