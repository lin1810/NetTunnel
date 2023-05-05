package net.shihome.nt.client;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {
  public static void main(String[] args) {
    SpringApplication springApplication = new SpringApplication(ClientApplication.class);
    springApplication.setBannerMode(Banner.Mode.CONSOLE);
    springApplication.run(args);
  }
}
