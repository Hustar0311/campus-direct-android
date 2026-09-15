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

GitHub Actions 在 `main` 分支 push、pull request 或手动触发时执行相同检查，并上传保留七天的 `campus-direct-debug` artifact。该 debug APK 使用 Android 工具链自动生成的测试签名，可直接侧载，但不作为长期发布签名。

## R5C 前置条件

`r5c/` 提供独立于其他客户端状态的 helper 参考实现和部署说明。它不由 GitHub Actions 自动部署；必须由设备管理员在本地审查、填写环境配置并安装到受限 SSH dispatcher 后，APK 的远程修改功能才可使用。

## 当前版本

`0.1.0`：首次可构建 MVP。目标 API 36，最低 Android 8.0。
