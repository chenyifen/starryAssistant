# 阿里云 SSL 证书完整链配置指南

## 一、问题诊断

### 检查当前服务器证书链

```bash
# 连接服务器查看证书链
openssl s_client -connect namingyou.com:443 -showcerts 2>&1 | grep -A2 "Certificate chain"

# 查看证书验证深度
openssl s_client -connect namingyou.com:443 2>&1 | grep "depth="
```

**期望输出（完整链）:**
```
depth=2 C = US, O = Internet Security Research Group, CN = ISRG Root X1
depth=1 C = US, O = Let's Encrypt, CN = R13
depth=0 CN = namingyou.com
```

**问题输出（缺少根证书）:**
```
depth=1 C = US, O = Let's Encrypt, CN = R13  ← 只到中间证书
depth=0 CN = namingyou.com
```

---

## 二、获取完整证书链

### 方法1: 使用 Certbot (Let's Encrypt)

```bash
# 查看证书文件位置
sudo ls -lh /etc/letsencrypt/live/namingyou.com/

# 输出示例：
# cert.pem       -> ../../archive/namingyou.com/cert1.pem
# chain.pem      -> ../../archive/namingyou.com/chain1.pem
# fullchain.pem  -> ../../archive/namingyou.com/fullchain1.pem  ← 使用这个
# privkey.pem    -> ../../archive/namingyou.com/privkey1.pem
```

**文件说明:**
- `cert.pem` - 网站证书（只包含 namingyou.com）
- `chain.pem` - 中间证书链（R13 + ISRG Root X1）
- `fullchain.pem` - **完整链** = cert.pem + chain.pem（推荐使用）
- `privkey.pem` - 私钥

### 方法2: 手动组装证书链

如果只有 `cert.pem` 和 `chain.pem`:

```bash
# 创建完整证书链
cat cert.pem chain.pem > fullchain.pem

# 或者下载中间证书
curl https://letsencrypt.org/certs/letsencrypt-r13.pem >> fullchain.pem
```

### 方法3: 从现有证书扩展

```bash
# 下载 Let's Encrypt 中间证书
wget https://letsencrypt.org/certs/letsencrypt-r13.pem

# 下载 ISRG Root X1
wget https://letsencrypt.org/certs/isrgrootx1.pem

# 组装完整链（顺序很重要）
cat cert.pem letsencrypt-r13.pem isrgrootx1.pem > fullchain.pem
```

---

## 三、阿里云配置

### A. 使用 SLB (负载均衡) + ECS

#### 1. 上传证书到阿里云

**控制台路径:**
```
阿里云控制台 → 产品与服务 → 安全 → SSL证书（应用安全）
→ 数字证书管理服务 → 上传证书
```

**填写信息:**
```yaml
证书名称: namingyou-fullchain-2024
证书内容: 
  # 粘贴 fullchain.pem 的全部内容
  -----BEGIN CERTIFICATE-----
  [网站证书内容]
  -----END CERTIFICATE-----
  -----BEGIN CERTIFICATE-----
  [R13 中间证书内容]
  -----END CERTIFICATE-----
  -----BEGIN CERTIFICATE-----
  [ISRG Root X1 根证书内容]
  -----END CERTIFICATE-----

私钥内容:
  # 粘贴 privkey.pem 的全部内容
  -----BEGIN PRIVATE KEY-----
  [私钥内容]
  -----END PRIVATE KEY-----
```

#### 2. 配置 SLB 监听

**控制台路径:**
```
负载均衡 → 实例管理 → 选择实例 → 监听 → 添加监听/修改监听
```

**HTTPS 监听配置:**
```yaml
前端协议: HTTPS
前端端口: 443
后端协议: HTTP 或 HTTPS
后端端口: 80 或 443

SSL证书:
  ├─ 服务器证书: namingyou-fullchain-2024  ← 选择刚上传的证书
  └─ TLS安全策略: tls_cipher_policy_1_2_strict
     (推荐，支持 TLS 1.2/1.3)

高级配置:
  ├─ 获取真实IP: 开启
  ├─ 获取监听协议: 开启 (X-Forwarded-Proto)
  └─ 会话保持: 根据需要
```

#### 3. 验证 SLB 配置

```bash
# 通过 SLB 公网IP测试
openssl s_client -connect <SLB公网IP>:443 -servername namingyou.com -showcerts 2>&1 | grep "depth="

# 应该看到完整的3层证书链
```

### B. 使用 ALB (应用型负载均衡)

**控制台路径:**
```
应用型负载均衡 ALB → 实例列表 → 选择实例 → 监听管理 → 创建监听
```

**监听配置:**
```yaml
监听协议: HTTPS
监听端口: 443

SSL证书:
  └─ 服务器证书
     ├─ 选择已有证书: namingyou-fullchain-2024
     └─ SSL策略: tls_cipher_policy_1_2_strict

高级设置:
  ├─ 空闲连接超时: 60秒
  ├─ 连接请求超时: 60秒
  └─ HTTP/2: 开启
```

### C. 直接配置 ECS Nginx (不使用 SLB/ALB)

#### 1. 上传证书到 ECS

```bash
# SSH 登录 ECS
ssh root@your-ecs-ip

# 创建证书目录
mkdir -p /etc/nginx/ssl
cd /etc/nginx/ssl

# 上传证书文件（使用 scp 或 在服务器上创建）
# 方式1: 从本地上传
scp fullchain.pem root@your-ecs-ip:/etc/nginx/ssl/
scp privkey.pem root@your-ecs-ip:/etc/nginx/ssl/

# 方式2: 在服务器上创建
vim fullchain.pem  # 粘贴完整证书链
vim privkey.pem    # 粘贴私钥

# 设置权限
chmod 600 /etc/nginx/ssl/privkey.pem
chmod 644 /etc/nginx/ssl/fullchain.pem
```

#### 2. 配置 Nginx

```bash
# 编辑 Nginx 配置
vim /etc/nginx/conf.d/namingyou.conf
```

```nginx
server {
    listen 80;
    server_name namingyou.com www.namingyou.com;
    
    # HTTP 自动跳转 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name namingyou.com www.namingyou.com;

    # SSL 证书配置（关键：使用 fullchain.pem）
    ssl_certificate /etc/nginx/ssl/fullchain.pem;      # ← 完整证书链
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    # SSL 协议和加密套件（推荐配置）
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers off;

    # SSL 会话缓存
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_session_tickets off;

    # OCSP Stapling（可选，提升性能）
    ssl_stapling on;
    ssl_stapling_verify on;
    ssl_trusted_certificate /etc/nginx/ssl/fullchain.pem;
    resolver 8.8.8.8 8.8.4.4 valid=300s;
    resolver_timeout 5s;

    # 安全头（推荐）
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # 应用配置
    location / {
        proxy_pass http://localhost:3000;  # 根据实际情况修改
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # API 路径（如果有）
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### 3. 测试并重载 Nginx

```bash
# 测试配置语法
nginx -t

# 期望输出：
# nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
# nginx: configuration file /etc/nginx/nginx.conf test is successful

# 重载配置
systemctl reload nginx

# 或者
nginx -s reload
```

---

## 四、验证配置

### 1. 命令行验证

```bash
# 方法1: 检查证书链深度
openssl s_client -connect namingyou.com:443 -servername namingyou.com 2>&1 | grep "depth="

# 期望输出（3层）：
# depth=2 C = US, O = Internet Security Research Group, CN = ISRG Root X1
# depth=1 C = US, O = Let's Encrypt, CN = R13
# depth=0 CN = namingyou.com

# 方法2: 查看返回的证书数量
openssl s_client -connect namingyou.com:443 -showcerts 2>&1 | grep "BEGIN CERTIFICATE" | wc -l

# 期望输出: 3（网站证书 + R13 + ISRG Root X1）

# 方法3: 完整验证
curl -vI https://namingyou.com 2>&1 | grep -E "(SSL|TLS|subject|issuer)"
```

### 2. 在线工具验证

**SSL Labs（最权威）:**
```
https://www.ssllabs.com/ssltest/analyze.html?d=namingyou.com
```

检查项:
- Certificate: 应显示完整证书链
- Chain issues: 应为 "None"
- Rating: 应达到 A 或 A+

**MySSL（中文界面）:**
```
https://myssl.com/namingyou.com
```

### 3. Android 设备测试

```bash
# 在 Android 10 设备上测试
adb install -r app/build/outputs/apk/normal/debug/VoiceAssistant-xxx-Debug.apk

# 查看日志
adb logcat | grep "LicenseActivation"

# 期望输出：
# ✅ MAC地址验证请求成功
# 响应内容: {"valid":true,...}
```

---

## 五、自动续期配置

### Certbot 自动续期

```bash
# 查看续期配置
cat /etc/letsencrypt/renewal/namingyou.com.conf

# 确保配置正确：
[renewalparams]
authenticator = nginx
installer = nginx
account = xxxxxxxx

# 续期钩子（自动重载服务）
renew_hook = systemctl reload nginx
```

**测试续期:**
```bash
# 模拟续期（不会真正续期）
certbot renew --dry-run

# 期望输出：
# Congratulations, all simulated renewals succeeded
```

**手动续期:**
```bash
# 强制续期（距离到期不足30天时）
certbot renew --force-renewal

# 续期后检查证书链
openssl x509 -in /etc/letsencrypt/live/namingyou.com/fullchain.pem -text -noout
```

---

## 六、常见问题

### Q1: 证书上传失败

**错误:** "证书格式不正确"

**解决:**
```bash
# 检查证书格式
openssl x509 -in fullchain.pem -text -noout

# 确保证书是 PEM 格式，每个证书块完整：
-----BEGIN CERTIFICATE-----
[Base64编码内容]
-----END CERTIFICATE-----
```

### Q2: 证书链不完整

**症状:** 浏览器正常，Android 老设备报错

**解决:**
```bash
# 验证证书链完整性
openssl verify -CAfile <(cat chain.pem) cert.pem

# 重新生成完整链
cat cert.pem chain.pem isrgrootx1.pem > fullchain-complete.pem
```

### Q3: Nginx 配置后仍然报错

**检查:**
```bash
# 1. 确认使用的是 fullchain.pem
grep ssl_certificate /etc/nginx/conf.d/*.conf

# 应该是：
# ssl_certificate /path/to/fullchain.pem;

# 2. 检查证书权限
ls -l /etc/nginx/ssl/

# 3. 查看 Nginx 错误日志
tail -f /var/log/nginx/error.log
```

---

## 七、总结检查清单

配置完成后，逐项检查：

- [ ] 证书文件使用 `fullchain.pem`（不是 `cert.pem`）
- [ ] 证书包含3个证书块（网站 + R13 + ISRG Root X1）
- [ ] Nginx/SLB 配置引用正确的证书路径
- [ ] 命令行验证显示 depth=2（3层证书链）
- [ ] SSL Labs 评分 A 或 A+，无 Chain issues
- [ ] Android 设备测试连接成功
- [ ] 配置了自动续期钩子

**预期效果:**
- 所有浏览器和客户端都能正常访问
- Android 7.0+ 设备无需特殊配置
- SSL Labs 评分达到 A+
- 证书自动续期无需人工干预

