package armadillo.admin;

import armadillo.admin.config.NettyServerStarter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArmadilloWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArmadilloWebApplication.class, args);
        new NettyServerStarter().startNettyServer();
    }
}
