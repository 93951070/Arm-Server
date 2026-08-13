package armadillo.admin.config;

import armadillo.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;

public class NettyServerStarter {
    private static final Logger logger = LoggerFactory.getLogger(NettyServerStarter.class);
    
    public void startNettyServer() {
        Thread thread = new Thread(() -> {
            try {
                String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
                logger.info("netty-starter PID:{}", pid);
                logger.info("netty-starter Armadillo -> 1.3.2");
                logger.info("netty-starter Copyright @ 2020 Armadillo Systems Incorporated. All rights reserved.");
                logger.info("netty-starter Server Bind Port:({} - {})", armadillo.Constant.start, armadillo.Constant.end);
                logger.info("netty-starter IO Handler Thread Pool:{}", armadillo.Constant.handleCount);
                logger.info("netty-starter Task Thread Pool:0");
                logger.info("netty-starter Youpk Bind Port:{}", armadillo.Constant.youpkShell);
                logger.info("netty-starter Xposed Bind Port:{}", armadillo.Constant.xposedShell);
                Application.main(new String[]{});
            } catch (Exception e) {
                logger.error("netty-starter Netty Server Start Failed", e);
            }
        }, "netty-starter");
        thread.setDaemon(true);
        thread.start();
    }
}
