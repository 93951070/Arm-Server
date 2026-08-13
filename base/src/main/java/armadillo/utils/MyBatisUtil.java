package armadillo.utils;

import armadillo.Constant;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class MyBatisUtil {
    private static final Logger logger = LoggerFactory.getLogger(MyBatisUtil.class);
    private static SqlSessionFactory sqlSessionFactory = null;

    private static void initSqlSessionFactory() {
        String profile = Constant.getProfile();
        synchronized (MyBatisUtil.class) {
            if (sqlSessionFactory == null) {
                try {
                    // 优先读取外部 jdbc.properties (/www/arm/jdbc.properties)
                    File externalJdbc = new File(Constant.getRoot(), "jdbc.properties");
                    Properties properties = new Properties();
                    boolean useExternal = false;

                    if (externalJdbc.exists()) {
                        try (FileInputStream fis = new FileInputStream(externalJdbc)) {
                            properties.load(fis);
                            useExternal = true;
                            logger.info("使用外部数据库配置: {}", externalJdbc.getAbsolutePath());
                        }
                    }

                    if (useExternal) {
                        // 使用外部 jdbc.properties, mybatis-config 从 classpath 加载
                        try (InputStream is = MyBatisUtil.class.getClassLoader()
                                .getResourceAsStream(profile + "/mybatis-config.xml")) {
                            sqlSessionFactory = new SqlSessionFactoryBuilder().build(is, properties);
                        }
                    } else {
                        // 回退到 classpath 内的默认配置
                        logger.info("未找到外部 jdbc.properties, 使用内置默认配置");
                        try (InputStream is = MyBatisUtil.class.getClassLoader()
                                .getResourceAsStream(profile + "/mybatis-config.xml")) {
                            sqlSessionFactory = new SqlSessionFactoryBuilder().build(is);
                        }
                    }
                } catch (Exception e) {
                    logger.error("MyBatis 初始化失败", e);
                    throw new RuntimeException("MyBatis init failed", e);
                }
            }
        }
    }

    public static SqlSession getSqlSession() {
        if (sqlSessionFactory == null) {
            initSqlSessionFactory();
        }
        return sqlSessionFactory.openSession();
    }

    public static SqlSession getSqlSession(boolean isAutoCommit) {
        if (sqlSessionFactory == null) {
            initSqlSessionFactory();
        }
        return sqlSessionFactory.openSession(isAutoCommit);
    }
}
