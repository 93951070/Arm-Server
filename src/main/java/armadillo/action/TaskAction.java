package armadillo.action;

import armadillo.Application;
import armadillo.Arm;
import armadillo.Constant;
import armadillo.dao.ShellTask;
import armadillo.enums.LanguageEnums;
import armadillo.enums.TaskStatus;
import armadillo.model.SysUser;
import armadillo.plugin.PluginClassloader;
import armadillo.result.TaskInfo;
import armadillo.shell.ShellHelper;
import armadillo.transformers.base.BaseTransformer;
import armadillo.utils.*;
import com.alibaba.fastjson.JSONArray;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TaskAction implements Runnable, Comparable<TaskAction> {
    private final Logger logger = LoggerFactory.getLogger(TaskAction.class);

    // OkHttpClient 全局单例 (内部自带连接池和线程池, 线程安全)
    private static final OkHttpClient sharedHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final long flags;
    private final String rule;
    private final List<TaskInfo> task;
    private final String uuid;
    private final LanguageEnums languageEnums;
    private final String md5;
    private final SysUser sysUser;
    private final long start = System.currentTimeMillis();
    private final long timeout = SysConfigUtil.getIntConfig("task.timeout") * 1000;
    private String dow_url = null;
    private ZipFile zipFile = null;
    private boolean is_delete = true;
    private volatile TaskStatus status = TaskStatus.Wait;
    private volatile Thread task_thread;
    private final Object statusLock = new Object();

    public TaskAction(
            long flags,
            String rule,
            List<TaskInfo> task,
            String uuid,
            LanguageEnums languageEnums,
            String md5,
            SysUser sysUser) {
        this.flags = flags;
        this.rule = rule != null ? new String(Base64.getDecoder().decode(rule.getBytes()), StandardCharsets.UTF_8) : null;
        this.task = task;
        this.uuid = uuid;
        this.languageEnums = languageEnums;
        this.md5 = md5;
        this.sysUser = sysUser;
    }

    private Integer priority;

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    @Override
    public int compareTo(TaskAction o) {
        if (this.getPriority() < o.priority) {
            return 1;
        } else if (this.getPriority() > o.priority) {
            return -1;
        }
        return 0;
    }

    @Override
    public void run() {
        try {
            if (status == TaskStatus.Stop) {
                File cache = new File(Constant.getTmp(), uuid);
                if (cache.exists()) {
                    if (cache.delete()) {
                        if (Constant.isDevelopment())
                            logger.info(String.format("任务状态:%s,删除临时文件:%s,成功", status, cache.getAbsolutePath()));
                    } else {
                        if (Constant.isDevelopment())
                            logger.info(String.format("任务状态:%s,删除临时文件:%s,失败", status, cache.getAbsolutePath()));
                    }
                }
                if (QiniuUtils.getInstance().deleteQiniuRes(uuid)) {
                    if (Constant.isDevelopment())
                        logger.info(String.format("任务状态:%s,删除OSS资源ID:%s,成功", status, uuid));
                } else {
                    if (Constant.isDevelopment())
                        logger.info(String.format("任务状态:%s,删除OSS资源ID:%s,失败", status, uuid));
                }
                return;
            }
            task_thread = new Thread(() -> {
                try {
                    File cacheFile = new File(Constant.getCache(), md5);
                    dow_url = QiniuUtils.getInstance().getTaskAddress(uuid);
                    ActionUtils actionUtils = new ActionUtils(languageEnums);
                    /**
                     * 脱壳任务
                     */
                    if (actionUtils.isSet(Long.parseLong(SysConfigUtil.getStringConfig("shell.flags")), flags)
                            || actionUtils.isSet(Long.parseLong(SysConfigUtil.getStringConfig("xposed.flags")), flags)) {
                        is_delete = !is_delete;
                        RedisUtil redisUtil = RedisUtil.getRedisUtil();
                        while (true) {
                            try {
                                if ((actionUtils.isSet(Long.parseLong(SysConfigUtil.getStringConfig("shell.flags")), flags) ? Application.getYoupkSet().size() : Application.getXposedSet().size()) == 0) {
                                    task.add(new TaskInfo(404, SysConfigUtil.getLanguageConfigUtil(languageEnums, "shell.fail")));
                                    if (redisUtil.exists(uuid))
                                        redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                                    task.clear();
                                    status = TaskStatus.Success;
                                    synchronized (statusLock) { statusLock.notifyAll(); }
                                    return;
                                } else {
                                    for (ShellHelper helper : (actionUtils.isSet(Long.parseLong(SysConfigUtil.getStringConfig("shell.flags")), flags) ? Application.getYoupkSet() : Application.getXposedSet())) {
                                        if (!helper.is_handle) {
                                            if (helper.SendShell(new ShellTask(uuid, md5, dow_url, QiniuUtils.getInstance().createTask("shell_" + uuid)), uuid, cacheFile)) {
                                                String startMsg = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.start");
                                                task.add(new TaskInfo(100, startMsg != null ? startMsg : "开始处理..."));
                                                redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                                                helper.setState(task);
                                                status = TaskStatus.Success;
                                                synchronized (statusLock) { statusLock.notifyAll(); }
                                                return;
                                            }
                                        }
                                    }
                                }
                                Thread.sleep(1000 * 10);
                            } catch (Exception e) {
                                is_delete = true;
                                String failTpl = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.fail");
                                task.add(new TaskInfo(404, String.format(failTpl != null ? failTpl : "处理失败: %s", e.getMessage())));
                                if (redisUtil.exists(uuid))
                                    redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                                task.clear();
                                status = TaskStatus.Success;
                                synchronized (statusLock) { statusLock.notifyAll(); }
                                return;
                            }
                        }
                    }
                    /**
                     * 其他任务
                     */
                    else {
                        if (new File(Constant.getTmp(), uuid).exists()) {
                            handle(actionUtils, cacheFile, start);
                        } else {
                            OkHttpClient okHttpClient = sharedHttpClient;
                            Request request = new Request.Builder()
                                    .url(dow_url)
                                    .addHeader("Connection", "close")
                                    .build();
                            okhttp3.Response response = okHttpClient.newCall(request).execute();
                            if (response.isSuccessful()) {
                                FileOutputStream fileOutputStream = new FileOutputStream(new File(Constant.getTmp(), uuid));
                                fileOutputStream.write(Objects.requireNonNull(response.body()).bytes());
                                fileOutputStream.close();
                                handle(actionUtils, cacheFile, start);
                            } else {
                                logger.info("******************************************************************");
                                logger.error(String.format("任务ID %s 发生异常:%s", uuid, response.message()));
                                logger.info("******************************************************************");
                                String failTpl = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.fail");
                                task.add(new TaskInfo(404, String.format(failTpl != null ? failTpl : "处理失败: %s", response.message())));
                                RedisUtil redisUtil = RedisUtil.getRedisUtil();
                                if (redisUtil.exists(uuid))
                                    redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                                task.clear();
                            }
                        }
                    }
                    status = TaskStatus.Success;
                    synchronized (statusLock) { statusLock.notifyAll(); }
                } catch (Throwable e) {
                    is_delete = true;
                    status = TaskStatus.Fail;
                    synchronized (statusLock) { statusLock.notifyAll(); }
                    logger.info("******************************************************************");
                    logger.error(String.format("任务ID %s 发生异常", uuid), e);
                    logger.info("******************************************************************");
                    String failTpl = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.fail");
                    task.add(new TaskInfo(404, String.format(failTpl != null ? failTpl : "处理失败: %s", e.getMessage() == null ? e.toString() : e.getMessage())));
                    RedisUtil redisUtil = RedisUtil.getRedisUtil();
                    if (redisUtil.exists(uuid))
                        redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                    task.clear();
                    if (new File(Constant.getTask(), uuid).exists()) {
                        if (new File(Constant.getTask(), uuid).delete()) {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除任务资源:%s,成功", status, uuid));
                        } else {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除任务资源:%s,失败", status, uuid));
                        }
                    }
                } finally {
                    if (zipFile != null)
                        try {
                            zipFile.close();
                        } catch (IOException ignored) {
                        }
                    File cache = new File(Constant.getTmp(), uuid);
                    if (cache.exists()) {
                        if (cache.delete()) {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除临时文件:%s,成功", status, cache.getAbsolutePath()));
                        } else {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除临时文件:%s,失败", status, cache.getAbsolutePath()));
                        }
                    }
                    if (is_delete) {
                        if (QiniuUtils.getInstance().deleteQiniuRes(uuid)) {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除OSS资源ID:%s,成功", status, uuid));
                        } else {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除OSS资源ID:%s,失败", status, uuid));
                        }
                    }
                }
            });
            task_thread.setUncaughtExceptionHandler((t, e) -> {
                logger.error(String.format("任务线程未捕获异常, 任务ID:%s", uuid), e);
                is_delete = true;
                status = TaskStatus.Fail;
                synchronized (statusLock) { statusLock.notifyAll(); }
                String failTpl = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.fail");
                task.add(new TaskInfo(404, String.format(failTpl != null ? failTpl : "处理失败: %s", e.getMessage() == null ? e.toString() : e.getMessage())));
                RedisUtil redisUtil = RedisUtil.getRedisUtil();
                if (redisUtil.exists(uuid))
                    redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                task.clear();
            });
            task_thread.start();
            while (true) {
                if (Constant.isDevelopment())
                    logger.info(String.format("读取任务状态:%s", uuid));
                if (status == TaskStatus.Stop) {
                    if (Constant.isDevelopment())
                        logger.info(String.format("任务:%s终止", uuid));
                    status = TaskStatus.Stop;
                    task_thread.interrupt();
                    break;
                } else if (status == TaskStatus.Success || status == TaskStatus.Fail) {
                    break;
                } else if (System.currentTimeMillis() - start > timeout || status == TaskStatus.TimeOut) {
                    status = TaskStatus.TimeOut;
                    logger.info(String.format("任务超时:%s", uuid));
                    task_thread.interrupt();
                    String timeoutMsg = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.timeout");
                    task.add(new TaskInfo(404, timeoutMsg != null ? timeoutMsg : "处理超时"));
                    RedisUtil redisUtil = RedisUtil.getRedisUtil();
                    if (redisUtil.exists(uuid))
                        redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
                    task.clear();
                    if (new File(Constant.getTask(), uuid).exists()) {
                        if (new File(Constant.getTask(), uuid).delete()) {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除任务资源:%s,成功", status, uuid));
                        } else {
                            if (Constant.isDevelopment())
                                logger.info(String.format("任务状态:%s,删除任务资源:%s,失败", status, uuid));
                        }
                    }
                    break;
                } else {
                    // wait/notify 替代 sleep(1000), 任务完成时立即响应
                    long remaining = timeout - (System.currentTimeMillis() - start);
                    if (remaining <= 0) continue;
                    synchronized (statusLock) {
                        statusLock.wait(Math.min(remaining, 1000));
                    }
                }
            }
        } catch (InterruptedException interruptedException) {
            logger.error("线程中断异常", interruptedException);
        } finally {
            if (Constant.isDevelopment())
                logger.info(String.format("任务:%s移除", uuid));
            Constant.getTask_map().remove(uuid);
        }
    }

    private void handle(ActionUtils actionUtils, File cacheFile, long start) throws Exception {
        zipFile = new ZipFile(new File(Constant.getTmp(), uuid));
        String startMsg = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.start");
        task.add(new TaskInfo(100, startMsg != null ? startMsg : "开始处理..."));
        RedisUtil redisUtil = RedisUtil.getRedisUtil();
        if (redisUtil.exists(uuid))
            redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
        List<byte[]> dexs = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry zipEntry = enumeration.nextElement();
            if (zipEntry.isDirectory())
                continue;
            if (zipEntry.getName().startsWith("classes") && zipEntry.getName().endsWith("dex"))
                dexs.add(StreamUtil.readBytes(zipFile.getInputStream(new ZipEntry(zipEntry.getName()))));
        }
        Arm arm = new Arm(zipFile, task, dexs, uuid, languageEnums);
        arm.setConfig(rule);
        arm.setSysUser(sysUser);
        for (ActionUtils.ActionFlag actionFlag : actionUtils.getFlags(flags)) {
            PluginClassloader pluginClassloader = new PluginClassloader(Thread.currentThread().getContextClassLoader(), Constant.getPlugin().getAbsolutePath() + File.separator + actionFlag.getJarPath());
            Class<?> loadClass = pluginClassloader.loadClass(actionFlag.getJarCls());
            Object newInstance = loadClass.newInstance();
            arm.addTransformer((BaseTransformer) newInstance);
        }
        arm.Run();
        String successTpl = SysConfigUtil.getLanguageConfigUtil(languageEnums, "processing.success");
        task.add(new TaskInfo(200, String.format(successTpl != null ? successTpl : "处理完成, 耗时 %.2f 秒", (float) (System.currentTimeMillis() - start) / 1000)));
        if (redisUtil.exists(uuid))
            redisUtil.setex(uuid, 60 * 60 * 24, JSONArray.toJSONString(task));
        task.clear();
        /**
         * 写缓存
         */
        if (!actionUtils.isSet(flags, Long.parseLong(SysConfigUtil.getStringConfig("verify.flags")))) {
            byte[] readBytes = StreamUtil.readBytes(new FileInputStream(new File(Constant.getTask(), uuid)));
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            outputStream.write(readBytes);
            outputStream.close();
        }
    }

    public void Cancel() {
        if (Constant.isDevelopment())
            logger.info(String.format("强制终止任务ID:%s", uuid));
        status = TaskStatus.Stop;
        synchronized (statusLock) { statusLock.notifyAll(); }
    }
}
