# SSL 证书管理策略

## 当前配置

### 内置证书
应用内置了 Let's Encrypt 证书链以支持旧版 Android 系统：

1. **ISRG Root X1** (根证书)
   - 路径: `app/src/main/res/raw/isrg_root_x1.pem`
   - 有效期: 2015-06-04 至 **2035-06-04**
   - 用途: 长期信任锚点

2. **Let's Encrypt R13** (中间证书)
   - 路径: `app/src/main/res/raw/letsencrypt_r13.pem`
   - 有效期: 2024-03-13 至 **2027-03-12**
   - 用途: 短期兼容性支持

### 信任策略
配置文件: `app/src/main/res/xml/network_security_config.xml`

```xml
<trust-anchors>
    <certificates src="system" />       <!-- 优先使用系统证书 -->
    <certificates src="@raw/isrg_root_x1" />
    <certificates src="@raw/letsencrypt_r13" />
</trust-anchors>
```

**工作原理:**
- Android 系统按顺序检查证书链
- 优先使用系统内置证书（自动更新）
- 系统没有时使用应用内置证书（兼容旧设备）

## 证书过期处理

### 2027年3月后（R13 过期）
**无需操作** - 原因：
1. 系统证书优先，届时大部分设备已更新
2. ISRG Root X1 仍然有效（到2035年）
3. Let's Encrypt 会签发新的中间证书

### 2035年6月后（根证书过期）
**需要更新** - 操作：
1. 获取新的根证书
2. 更新 `isrg_root_x1.pem` 文件
3. 发布应用更新

## 证书更新指南

### 1. 检查证书有效期
```bash
# 检查服务器当前证书
openssl s_client -connect namingyou.com:443 -showcerts 2>&1 | openssl x509 -noout -dates

# 检查应用内证书
openssl x509 -in app/src/main/res/raw/letsencrypt_r13.pem -noout -dates
```

### 2. 更新证书
```bash
# 下载新证书链
openssl s_client -connect namingyou.com:443 -showcerts 2>&1 | \
  sed -n '/BEGIN CERTIFICATE/,/END CERTIFICATE/p' > new_cert.pem

# 分离根证书和中间证书
# 复制到 app/src/main/res/raw/ 目录
```

### 3. 更新配置
编辑 `network_security_config.xml`，确保引用新证书文件。

### 4. 测试
```bash
# 在旧设备上测试（Android 10）
./run.sh

# 检查日志确认证书验证成功
adb logcat | grep "LicenseActivation"
```

## 最佳实践

1. **定期检查**: 每年检查证书有效期
2. **提前更新**: 证书过期前 6 个月发布更新
3. **监控告警**: 在激活 API 中添加证书有效期监控
4. **测试覆盖**: 在不同 Android 版本上测试证书验证

## 替代方案

如果不想维护内置证书，可考虑：

### 方案A: 要求最低 Android 版本
- 设置 `minSdkVersion = 24`（Android 7.0+）
- 系统自带较新的证书库

### 方案B: 使用其他 CA
- 选择支持更好的 CA（如 DigiCert）
- 证书更新频率低，系统支持更广

### 方案C: 服务器配置完整证书链
- 确保服务器返回完整证书链（包括中间证书）
- 减少客户端依赖

## 当前状态

- ✅ ISRG Root X1: 有效至 2035-06-04
- ✅ Let's Encrypt R13: 有效至 2027-03-12
- ✅ 系统证书: 优先使用
- ✅ 兼容 Android 10+

**下次检查时间: 2026年9月**

