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

- 你上传源码 `base/build.gradle` 写的是 Jedis 3.3.0，但上传包内原有 fat JAR 实际包含 `jedis-3.8.0.jar`，说明源码依赖声明与旧构建产物不是完全同一轮构建。本次为降低风险，没有改变 Jedis/Redisson 大版本。
- 项目依赖外部 `/www/arm/language/*.properties`；本地没有这些文件时会打印 FileNotFoundException，通常不会阻止 `code` 返回，但 `msg` 可能为空。服务器若已有 `/www/arm/language` 则无需处理。
