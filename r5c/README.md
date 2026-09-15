# Android 专用 R5C helper

该目录提供与其他客户端状态隔离的参考 helper。它不会由 GitHub Actions 部署，也不包含任何真实网络地址。

## 安装前

1. 审查 `campus-route-helper`。
2. 将示例 UCI 文件复制为 R5C 上的 `/etc/config/campus-direct-android`，把两个 `FILL_ON_R5C` 替换为实际 CIDR 和网关。
3. 将 helper 安装为 `/usr/libexec/campus-route-helper`，所有者为 root，普通用户不可写。
4. 在现有 forced-command dispatcher 中仅增加下列精确命令：

```text
/usr/libexec/campus-route-helper status
/usr/libexec/campus-route-helper inspect
/usr/libexec/campus-route-helper apply <合法且单一的IPv4参数>
/usr/libexec/campus-route-helper verify <合法且单一的IPv4参数>
/usr/libexec/campus-route-helper cleanup <合法且单一的IPv4参数>
```

5. 在 sudoers 中只放行上述精确 helper 入口，不放行 shell、`ip`、`uci` 或通配命令。
6. 安装后分别验证 status、非法参数拒绝、幂等 apply、verify、精确 cleanup，并确认其他客户端的状态文件和路由不受影响。

## 状态与边界

- 独立状态文件只记录一个 Android peer。
- 只处理 UCI 配置 CIDR 内的纯 IPv4。
- 只删除状态匹配且带配置协议号的精确 `/32` 路由。
- 路由是临时运行时状态，重启或 WAN 重建后需由应用重新添加。
- helper 不修改防火墙、Tailscale、DNS、DHCP、默认路由或 UCI network 路由。
