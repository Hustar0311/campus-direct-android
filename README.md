# Campus Direct for Android

一个面向未 Root Android 设备的校园网路由辅助应用。应用读取当前 Wi-Fi 网络信息，验证用户填写的校园网规则，通过已有 Tailscale VPN 上的受限 SSH helper 管理手机地址对应的远端临时路由，并指导用户在系统 Wi-Fi 设置中手动切换前缀。

## 隐私与安全

- 仓库、GitHub Actions 日志和 APK 名称不包含部署环境的 IP 地址。
- 所有地址、SSH 用户、helper 路径和主机指纹均在安装后填写。
- SSH 私钥通过系统文件选择器导入，并由 Android Keystore 密钥加密保存在应用私有目录。
- 不自动信任首次见到的 SSH 主机密钥；必须填写 SHA-256 主机指纹。
- 不修改 Android Wi-Fi 配置，不需要 Root，不包含遥测、广告或第三方崩溃上报。
- 远端 SSH 身份必须被 forced command 限制到专用 helper。

## SSH 模式

应用提供两种模式：

1. Dropbear/OpenSSH 公钥认证 over Tailscale，适用于受限专用账号。
2. Tailscale 内置 SSH，优先尝试 `none` 认证，并提供官方兼容的 password 方式。

服务端未启用 Tailscale 内置 SSH 时，应选择第一种模式。

## 构建

```text
./gradlew lint testDebugUnitTest assembleDebug
```

GitHub Actions 在 `main` 分支 push、pull request 或手动触发时执行相同检查，并上传保留七天的 `campus-direct-debug` artifact。artifact 同时包含 APK 的 SHA-256 和签名证书报告。

长期使用必须配置稳定签名。构建支持 `CAMPUS_SIGNING_STORE_FILE`、`CAMPUS_SIGNING_STORE_PASSWORD`、`CAMPUS_SIGNING_KEY_ALIAS` 和 `CAMPUS_SIGNING_KEY_PASSWORD` 四个环境变量；CI 对应读取同名 GitHub Secrets，并额外用 `CAMPUS_SIGNING_KEYSTORE_BASE64` 保存 Base64 编码的 keystore。Secrets 不完整时会退回一次性 debug 签名，产物不能保证覆盖安装上一轮 CI APK。

现有 `0.1.1` 是由已丢失私钥的临时 debug 证书签名。升级前必须比较证书；不一致时禁止直接卸载或清除数据。签名方案和一次性 ADB 迁移流程见 [`docs/UPGRADE-0.2.0.md`](docs/UPGRADE-0.2.0.md)。

## R5C 前置条件

`r5c/` 提供独立于其他客户端状态的 helper 参考实现和部署说明。它不由 GitHub Actions 自动部署；必须由设备管理员在本地审查、填写环境配置并安装到受限 SSH dispatcher 后，APK 的远程修改功能才可使用。`0.2.0` 的 Apply 始终只把当前物理 Wi-Fi IPv4 交给 helper；地址替换、原子更新和失败回滚均由 helper 完成，APK 不会先清理旧地址。

Verify 可兼容旧版仅返回 `reachable` 的 JSON，也会解析新版的 `tailnet_reachable`、`direct`、`direct_endpoint_ip`、`endpoint_matches_peer`、`campus_ping_reachable`、`lease_fresh`、`snat_consistent`、`verified_owner`、Watchdog 与租约剩余时间。校园网 ICMP 可达只代表连通性，不能单独证明 Direct 路径或路由所有权。

应用不使用 WorkManager、后台 Service 或心跳。网络读取和 SSH 操作仅在用户点击按钮时执行：物理校园 Wi-Fi 数据从非 VPN 网络读取，R5C SSH 仍走系统默认路径（通常为 Tailscale VPN）。

## 当前版本

`0.2.0`：适配 helper 的原子 peer 替换和综合验证契约；Apply 失败保留旧受管地址；加入稳定签名入口及旧 debug 安装的一次性无损迁移方案。目标 API 36，最低 Android 8.0。
