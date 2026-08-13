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
                logger.info("netty-starter 进程ID:{}", pid);
                logger.info("netty-starter Armadillo -> 1.3.2");
                logger.info("netty-starter 版权所有 @ 2020 Armadillo Systems Incorporated. 保留所有权利。");
                logger.info("netty-starter 服务端绑定端口:({} - {})", armadillo.Constant.start, armadillo.Constant.end);
                logger.info("netty-starter IO处理线程池:{}", armadillo.Constant.handleCount);
                logger.info("netty-starter 任务线程池:0");
                logger.info("netty-starter Youpk绑定端口:{}", armadillo.Constant.youpkShell);
                logger.info("netty-starter Xposed绑定端口:{}", armadillo.Constant.xposedShell);
                Application.main(new String[]{});
            } catch (Exception e) {
                logger.error("netty-starter Netty服务端启动失败", e);
            }
        }, "netty-starter");
        thread.setDaemon(true);
        thread.start();
    }
}
