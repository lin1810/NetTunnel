package net.shihome.nt.server;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ServerApplication {

  public static void main(String[] args) throws InterruptedException {
    SpringApplication springApplication = new SpringApplication(ServerApplication.class);
    springApplication.setBannerMode(Banner.Mode.CONSOLE);
    ConfigurableApplicationContext run = springApplication.run(args);
  }
}
