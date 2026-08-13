package armadillo.utils;

import armadillo.Constant;
import armadillo.enums.LanguageEnums;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class SysConfigUtil {
    private static final Logger logger = LoggerFactory.getLogger(SysConfigUtil.class);

    // ========== 配置缓存 (TTL 30秒) ==========
    private static final long CACHE_TTL_MS = 30_000L;
    private static volatile JSONObject configCache = null;
    private static volatile long configCacheTime = 0L;
    private static volatile Properties languageCache_default = null;
    private static volatile long languageCacheTime_default = 0L;
    private static volatile Properties languageCache_zh = null;
    private static volatile long languageCacheTime_zh = 0L;
    private static volatile Properties languageCache_ru = null;
    private static volatile long languageCacheTime_ru = 0L;
    private static final ConcurrentHashMap<String, Object> configLocks = new ConcurrentHashMap<>();

    /**
     * 强制刷新缓存 (管理员可通过控制台 -R 命令触发)
     */
    public static void refreshCache() {
        configCache = null;
        configCacheTime = 0L;
        languageCache_default = null;
        languageCacheTime_default = 0L;
        languageCache_zh = null;
        languageCacheTime_zh = 0L;
        languageCache_ru = null;
        languageCacheTime_ru = 0L;
        logger.info("SysConfigUtil 配置缓存已刷新");
    }

    private static JSONObject getConfig() {
        long now = System.currentTimeMillis();
        if (configCache != null && (now - configCacheTime) < CACHE_TTL_MS) {
            return configCache;
        }
        synchronized (SysConfigUtil.class) {
            if (configCache != null && (now - configCacheTime) < CACHE_TTL_MS) {
                return configCache;
            }
            Properties pro = new Properties();
            String properties = "config.properties";
            try (FileInputStream fileInputStream = new FileInputStream(new File(Constant.getConfig(), properties));
                 InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                 BufferedReader is = new BufferedReader(inputStreamReader)) {
                pro.load(is);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            configCache = JSONObject.parseObject(JSON.toJSONString(pro));
            configCacheTime = System.currentTimeMillis();
            return configCache;
        }
    }

    private static Properties getLanguageProperties(LanguageEnums languageEnums) {
        String properties;
        Properties cached;
        long cacheTime;
        switch (languageEnums) {
            case ZH:
                properties = "zh.properties";
                cached = languageCache_zh;
                cacheTime = languageCacheTime_zh;
                break;
            case RU:
                properties = "ru.properties";
                cached = languageCache_ru;
                cacheTime = languageCacheTime_ru;
                break;
            default:
                properties = "default.properties";
                cached = languageCache_default;
                cacheTime = languageCacheTime_default;
                break;
        }
        long now = System.currentTimeMillis();
        if (cached != null && (now - cacheTime) < CACHE_TTL_MS) {
            return cached;
        }
        synchronized (SysConfigUtil.class) {
            // double-check
            switch (languageEnums) {
                case ZH:
                    if (languageCache_zh != null && (System.currentTimeMillis() - languageCacheTime_zh) < CACHE_TTL_MS)
                        return languageCache_zh;
                    break;
                case RU:
                    if (languageCache_ru != null && (System.currentTimeMillis() - languageCacheTime_ru) < CACHE_TTL_MS)
                        return languageCache_ru;
                    break;
                default:
                    if (languageCache_default != null && (System.currentTimeMillis() - languageCacheTime_default) < CACHE_TTL_MS)
                        return languageCache_default;
                    break;
            }
            Properties pro = new Properties();
            try (FileInputStream fileInputStream = new FileInputStream(new File(Constant.getLanguage(), properties));
                 InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                pro.load(bufferedReader);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            switch (languageEnums) {
                case ZH:
                    languageCache_zh = pro;
                    languageCacheTime_zh = System.currentTimeMillis();
                    break;
                case RU:
                    languageCache_ru = pro;
                    languageCacheTime_ru = System.currentTimeMillis();
                    break;
                default:
                    languageCache_default = pro;
                    languageCacheTime_default = System.currentTimeMillis();
                    break;
            }
            return pro;
        }
    }

    public static JSONObject getSysConfigUtil(String properties) {
        Properties pro = new Properties();
        try (InputStream is = SysConfigUtil.class.getClassLoader().getResourceAsStream(Constant.getProfile() + "/" + properties)) {
            pro.load(is);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return JSONObject.parseObject(JSON.toJSONString(pro));
    }

    public static String getLanguageConfigUtil(LanguageEnums languageEnums, String config) {
        Properties pro = getLanguageProperties(languageEnums);
        return pro.getProperty(config);
    }

    public static String getConfigUtil(String config) {
        return getConfig().getString(config);
    }

    /**
     * 获取系统Int配置
     */
    public static int getIntConfig(String config) {
        return getConfig().getIntValue(config);
    }

    /**
     * 获取系统String配置
     */
    public static String getStringConfig(String config) {
        return getConfig().getString(config);
    }

    public static boolean getBooleanConfig(String config) {
        return getConfig().getBooleanValue(config);
    }
}
