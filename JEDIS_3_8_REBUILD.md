# Jedis 3.8.0 一致性修复

本版本在上一版 Arm-Server 修复基础上继续完成：

- 源码 `base/build.gradle`：`jedis 3.3.0 -> 3.8.0`
- 交付 fat JAR：确认包含 `BOOT-INF/lib/jedis-3.8.0.jar`
- Redis production 参数缺失/NPE 修复继续保留
- RLock 安全 unlock 修复继续保留
- Socket 异常统一错误响应与 write-and-close 修复继续保留
- `kang` 数据库配置不做修改
- RSA 密钥不做更换

回归：数据库成功注册/登录、错误密码、重复注册、真实 RSA 验签/请求解密/响应解密、Jedis 基本 RESP 读写均通过。
