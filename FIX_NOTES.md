# Arm-Server 修复说明（2026-08-13）

## 本次修复

1. `src/main/resources/production/redis.properties`
   - 补齐 `redis.maxWaitMillis=1000`
   - 补齐 `redis.testOnBorrow=true`
   - 补齐 `redis.testOnReturn=true`

2. `base/src/main/java/armadillo/utils/RedisUtil.java`
   - Redis 连接池参数改为带默认值读取。
   - 即使某个可选参数漏配，也不会因为 Fastjson 返回 `null` 自动拆箱而在 `RedisUtil.java` 初始化阶段 NPE。
   - 保留原有 Jedis/Redisson 使用方式和 Redis 端口/密码配置逻辑。

3. `src/main/java/armadillo/controller/SocketController.java`
   - 修复 `Statistics` 中 `tryLock()` 未获得锁时仍执行 `unlock()` 的问题。
   - `InterruptedException` 恢复线程中断标记。
   - Socket 业务异常现在记录 `client/type/异常类型` 的完整日志。
   - 业务异常时尽量回写标准 `UNKNOWNFAIL` 响应，不再直接让客户端只收到 EOF/0 字节。
   - 移除 `finally` 中无条件 `ctx.close()`；正常响应由 `writeAndFlush(...).addListener(CLOSE)` 在发送完成后关闭，避免异步发送与立即关闭之间的竞态。
   - 统一语言和响应字符集解析逻辑。

4. `src/main/java/armadillo/Application.java`
   - 修复测试用 `TestLock()` 同类的无条件 `unlock()` 问题，避免以后启用测试代码时产生 `IllegalMonitorStateException`。

## RSA / 交互密钥检查

服务端：
- `src/main/resources/static/rsa/en_private.txt`：合法 PKCS#8 RSA 私钥，1024 bit。
- `src/main/resources/static/rsa/sign_public.txt`：合法 X.509 RSA 公钥，1024 bit。
- 从 `en_private.txt` 推导出的公钥与 `sign_public.txt` 字节级完全一致。
- 推导公钥与 `sign_public.txt` 的 SHA-256 均为：
  `eda77749d66439b2a6ed1fda15bc7c7c6573d28fa056a715caad8f2b78e4e929`

结合本次对真实 RSA 签名/加解密请求的端到端测试，服务端密钥文件本身没有错配。
当前协议使用 `SHA1WithRSA` + 1024-bit RSA，属于较老的安全设计；本次为了不破坏现有 App 协议没有更换密钥或算法。若后续升级到 2048-bit + SHA-256，需要 App 与 Server 同时修改。

## 本地回归测试结果

### Java 编译
修改后的以下源文件已使用 Java 11 source/target 编译通过：
- `RedisUtil.java`
- `SocketController.java`
- `Application.java`

### 服务启动
修复后的 `build/libs/Ultima-1.0-SNAPSHOT.jar` 在 Java 21 下启动成功，Netty `10000-10020`/`8000` 可绑定，NIO 回退正常。

### 2003 登录异常路径
使用真实协议构造：
- SHA1WithRSA 请求签名
- RSA 私钥分段加密请求 JSON
- 服务端 `sign_public.txt` 验签/解密
- 服务端 RSA 私钥加密返回
- 客户端公钥解密返回

在本地故意不启动 MySQL 的情况下：
- 修复前：服务端异常后直接关闭连接，客户端收到 0 bytes / EOF。
- 修复后：客户端正常收到 `Armadillo` 响应包并解密为 `{"code":404}`，服务端日志明确记录 MySQL `PersistenceException`。

### 9000 Redis 异常路径
在本地故意不启动 Redis 的情况下：
- 修复前：缺少 Redis 参数时在 `RedisUtil.java:31` 触发 `NullPointerException`，客户端 0 bytes / EOF。
- 修复后：不再出现配置 NPE；真正的错误变成明确的 `RedisConnectionException: Unable to connect to Redis server`，客户端仍能收到 `{"code":404}`。

## 部署前需要修改

`src/main/resources/production/redis.properties` 中请按服务器实际 Redis 设置：

```properties
redis.host=127.0.0.1
redis.port=6379
redis.password=你的Redis密码
redis.maxTotal=1000
redis.maxIdle=10
redis.maxWaitMillis=1000
redis.testOnBorrow=true
redis.testOnReturn=true
```

如果宝塔 Redis 没有密码，`redis.password=` 保持空即可；如果设置了密码，必须和宝塔 Redis 一致。

数据库 `kang` 配置本次没有修改，保留你原项目：

```properties
jdbc.url=jdbc:mysql://127.0.0.1:3306/kang?...
jdbc.username=kang
jdbc.password=kang
```

请按你服务器实际密码调整，不需要因为上传的 SQL 原始库名是 `arm` 而强制改库名。

## 额外发现但未强制修改

- Jedis 源码依赖与 fat JAR 不一致的问题已在第二轮修复中处理：`base/build.gradle` 已统一为 Jedis 3.8.0。
- 项目依赖外部 `/www/arm/language/*.properties`；本地没有这些文件时会打印 FileNotFoundException，通常不会阻止 `code` 返回，但 `msg` 可能为空。服务器若已有 `/www/arm/language` 则无需处理。

## 2026-08-13 Jedis 依赖一致性修复与第二轮回归

- `base/build.gradle`：Jedis 从 `3.3.0` 明确统一为 `3.8.0`。
- 原 fat JAR 本身已经包含 `BOOT-INF/lib/jedis-3.8.0.jar`；本次把源码声明与运行产物统一，避免源码/部署依赖不一致。
- 保持 Redisson `3.14.1`、Netty `4.1.56.Final`、MySQL Connector/J `5.1.49` 不变，避免一次引入多个兼容变量。
- `base` 95 个 Java 源文件、主模块 20 个 Java 源文件均使用 Java 11 target 重新编译通过。
- 使用重新编译的 `base` 与主模块 class 重新组装 Spring Boot fat JAR，并保留 Jedis 3.8.0 为嵌套依赖；新 JAR 已实际启动成功，Tomcat、Netty 10000-10020、8000 均可绑定。
- 由于当前测试容器无法联网下载 Gradle 7.6.1 distribution，本轮不是 `./gradlew clean bootJar` 在线重建；而是使用项目 fat JAR 中已经解析好的依赖集合，对源码执行 Java 11 全量 javac 编译并重新组装 Boot JAR。启动与协议回归均针对该重新组装的交付 JAR执行。

### 数据库成功路径回归

当前容器无法联网安装 MySQL Server，因此测试库使用本机已有 HSQLDB，并按用户上传的 MySQL dump 中 `sys_user` 的字段结构建立等价测试表。仅测试环境 JDBC 驱动做了 HSQLDB 1.8 兼容包装；该测试驱动不进入交付 JAR，生产 `kang` / MySQL 配置完全保留。

使用原 `SysUserMapper`、原 MyBatis、原 `SocketController` 2003/2004、原 MD5/Token 逻辑测试：

- 2004 注册：`code=200`
- 同账号 2003 登录：`code=200`，正常返回 `token`、`username`、`loginCount` 等字段
- 错误密码：收到标准错误 Result，不再 EOF/0 字节
- 重复注册：收到标准错误 Result，不再 EOF/0 字节

### RSA / 交互协议第二轮回归

本轮没有绕过验签：测试客户端使用项目真实 `en_private.txt` 对请求执行 RSA/PKCS#1 v1.5 私钥分段加密，并使用 `SHA1WithRSA` 对 `encryptedRequest + "Armadillo1110300103"` 签名；服务端使用 `sign_public.txt` 完成真实验签与公钥解密。响应继续由服务端私钥加密，测试客户端以公钥完整解密 JSON。

- RSA 位数：1024 bit
- 从 `en_private.txt` 推导的公钥与 `sign_public.txt` 字节级一致
- 公钥 SHA-256：`eda77749d66439b2a6ed1fda15bc7c7c6573d28fa056a715caad8f2b78e4e929`
- 2004/2003 成功路径均通过真实签名、真实请求加密与真实响应解密

### Jedis 3.8.0 Smoke Test

测试容器没有 Redis Server 二进制，因此使用本地最小 RESP 测试服务器验证交付 JAR 中同一份 Jedis 3.8.0 客户端：

- `PING -> PONG`
- `SET -> OK`
- `GET -> ok`
- `EXISTS -> true`
- `DEL -> 1`

这验证了交付产物内 Jedis 3.8.0 在当前 Java 运行环境下的基本 Redis 协议读写工作正常；生产环境仍应使用真实 Redis 6.2.21 进行最终部署验证。
