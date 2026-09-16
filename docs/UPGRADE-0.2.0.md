# 0.2.0 签名、升级与回退

## 原则

Android 只允许相同包名、相同签名证书且版本号更高的 APK 原位升级。当前已安装的 `0.1.1` 使用旧 CI 临时 debug 证书；如果无法复现其私钥，新 APK 即使包名和版本号正确也不能 `adb install -r`。

在确认签名或完成备份前，禁止卸载、清除应用数据或覆盖迁移文件。普通升级不会丢失 SharedPreferences、受管地址记录和 Android Keystore 加密的 SSH 私钥；卸载则会同时删除应用数据和 Android Keystore 密钥。

## 稳定签名

首次建立长期签名时，在离线可信环境创建 keystore，并至少保留两份加密备份。不要把 keystore、密码或 Base64 内容提交到仓库。

本地构建设置以下环境变量：

```text
CAMPUS_SIGNING_STORE_FILE
CAMPUS_SIGNING_STORE_PASSWORD
CAMPUS_SIGNING_KEY_ALIAS
CAMPUS_SIGNING_KEY_PASSWORD
```

GitHub Actions 使用四个同名 Secrets，并用 `CAMPUS_SIGNING_KEYSTORE_BASE64` 保存 keystore 的 Base64 内容。配置后，debug 和 release build type 都使用稳定证书；未配置时 debug 构建仍使用临时测试证书。

CI artifact 中的 `signing-report.txt` 是本次 APK 证书，`campus-direct-debug.apk.sha256` 是文件摘要。安装前将报告中的 SHA-256 证书摘要与当前安装包的摘要比较；只有完全相同才可执行覆盖安装。

## 旧 debug 安装的一次性迁移

这是签名不一致时的保底方案，不会自动执行。已确认电脑保留原始 `campus_direct` SSH 私钥，且其公钥与 R5C 授权公钥一致，因此不解密旧应用的 Android Keystore 文件，也不把明文私钥写入手机公共存储。

迁移分为四阶段：

1. 在旧版设置页逐项记录非秘密配置：校园网 CIDR、网关、R5C Tailscale 地址、SSH 端口与用户名、helper 路径、SSH 主机 SHA-256 指纹和认证模式。不要把实际地址写入仓库、CI 日志或公开截图。
2. 在电脑本地对 `%USERPROFILE%\.ssh\campus_direct` 运行 `ssh-keygen -y`，只比较生成公钥的指纹与已授权公钥；不要打印、上传或复制私钥正文。确认 Windows ACL 只允许当前用户、SYSTEM 和管理员。
3. 只有配置记录和原始私钥都验证完成后，才卸载旧 APK并安装稳定签名的 `0.2.0`。首次启动后手工恢复配置，通过系统文件选择器直接选择原始私钥；应用会立即用新实例的 Android Keystore 密钥加密保存。
4. 保存设置，先执行“读取并检查当前 Wi-Fi”，再通过受限 SSH“读取状态”。确认主机指纹校验、配置、私钥认证及 helper 状态均正常后，迁移才算完成。

### 执行前必须发送并确认的清单

- 当前安装包版本、包名、`DEBUGGABLE` 状态和证书摘要。
- 新 APK 的版本、SHA-256 和证书摘要。
- 已记录配置字段的清单，但报告中对实际地址脱敏。
- 原始私钥路径、其公钥指纹匹配结果和 Windows ACL 检查结果。
- 每条安装/卸载命令、预期退出码和失败时停止点。
- 恢复后验证项：配置字段存在、私钥已导入、SSH 主机指纹校验成功、读取状态成功。

### 必须停止的失败点

- 当前包名、版本或目标设备与预期不一致。
- 配置记录不完整、原始私钥缺失、私钥公钥指纹不匹配或 ACL 过宽。
- 新 APK 摘要或证书与批准值不同。
- 系统文件选择器无法读取原始私钥，或应用导入失败。
- SSH 主机指纹、认证或读取状态验证失败。

任何阶段失败都不要继续卸载旧版。若已卸载但新实例尚未验证，保留原始私钥和配置记录；恢复旧 APK 也无法恢复已删除的 AndroidKeyStore 密钥，只能再次从原始私钥导入。

## 回退

同证书条件下，Android 默认不允许降版本；测试回退可能需要允许降级参数，但仍不能跨签名。优先修复并发布更高版本号。若确需卸载回退，必须先按上面的迁移流程导出配置和私钥，且确认旧 APK 来源、摘要和签名可信。禁止用“卸载重装”作为普通故障排查手段。
