package net.shihome.nt.comm.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.List;
import java.util.stream.Collectors;

public class ApplicationContextHolder implements ApplicationContextAware {

  private static ApplicationContextHolder INSTANCE;
  ApplicationContext applicationContext;

  protected ApplicationContextHolder() {
    INSTANCE = this;
  }

  public static ApplicationContext getApplicationContext() {
    return INSTANCE.applicationContext;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  public static <T> T getBean(Class<T> requiredType) throws BeansException {
    return getApplicationContext().getBean(requiredType);
  }

  public static <T> List<T> getBeans(Class<T> requiredType) {
    return getApplicationContext().getBeansOfType(requiredType).values().stream()
        .sorted(AnnotationAwareOrderComparator.INSTANCE)
        .collect(Collectors.toList());
  }
}
