# MiaoProxy 🐾

喵酱多协议伪装启动器 —— 把免费 MC 主机的 `server.jar` 换成本项目产物，后台静默跑 sing-box 多协议代理，面板看到的是一个正常的 Minecraft 服务器。

## 快速部署

1. 下载 `server.jar`：https://github.com/weikkadd/MiaoProxy/releases/download/latest/server.jar
2. 上传到面板，替换原有的 `server.jar`
3. 启动命令改成 `java -jar server.jar`（不走 mcsleepingserverstarter）
4. 启动，控制台日志里复制节点链接

**CF 隧道模式默认开启，代码已内置 Token + 域名，不用设任何变量**，开机自动出 `vless://` 节点链接。

## 三种部署模式

### 模式一：Cloudflare 隧道（默认，容器只有域名没端口）

代码默认值已预填，零配置开箱即用：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `CF_QUICK` | `1` | 自动启用隧道 |
| `CF_TOKEN` | 已内置 | Cloudflare 隧道 Token |
| `CF_DOMAIN` | 已内置 | 隧道域名 |

启动后：
- VLESS+WS 监听 `127.0.0.1:10000`
- cloudflared 隧道转发流量到 `localhost:10000`
- 节点链接：`vless://uuid@域名:443/?type=ws&security=tls&host=域名&path=/&fp=chrome#节点-WS`

> **前提**：Cloudflare Zero Trust 面板配好隧道路由（域名 → `http://localhost:10000`）
> **SERVER_PORT**：面板没有可不填，CF 隧道模式不依赖该端口

### 模式二：容器域名直连（有域名 + 端口反代）

设一个变量：
```
DOMAIN=你的容器域名
```
VLESS+WS 监听 `SERVER_PORT`，通过域名访问，节点链接用域名:443。

### 模式三：IP + 端口直连（有独立端口）

设协议端口变量（至少一个）：
```
HY2_PORT=25875
REALITY_PORT=25876
```
sing-box 直接监听对应端口，节点链接用 IP:端口。

## 全部环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `CF_QUICK` | `1` | `1` 启动 Cloudflare 隧道 |
| `CF_TOKEN` | 已内置 | 隧道 Token |
| `CF_DOMAIN` | 已内置 | 隧道域名 |
| `CF_NAME` | `vmess` | 隧道名称 |
| `DOMAIN` | 空 | 容器域名（模式二），设了走 VLESS+WS 域名直连 |
| `HY2_PORT` | 空 | Hysteria2 端口（UDP），留空不启动 |
| `TUIC_PORT` | 空 | TUIC 端口（UDP），留空不启动 |
| `REALITY_PORT` | 空 | VLESS Reality 端口（TCP），留空不启动 |
| `SOCKS_PORT` | 空 | SOCKS5 端口，留空不启动 |
| `SERVER_PORT` | 25748 | MC ping 保活端口（面板自动注入；没有可不填，CF 隧道模式不需要） |
| `NEZHA_SERVER` | 136.67.94.3:443 | 哪吒探针地址 |
| `NEZHA_KEY` | 已内置 | 哪吒客户端密钥 |
| `NEZHA_TLS` | false | 哪吒是否走 TLS |

## 工作原理

- **伪装**：假 MOTD（Paper 1.20.4）+ 假玩家进出日志 + ServerSocket 响应 MC ping，面板认为服务器正常运行
- **sing-box 统一核心**：根据非空端口变量动态生成 config.json，一个二进制跑全部协议
- **CF 隧道**：sing-box 监听 127.0.0.1 本地端口，cloudflared 转发，适合只有域名没端口的容器
- **自动签发**：openssl 自签 SNI 证书（apps.apple.com），Reality 密钥对运行时生成
- **节点链接**：启动后直接 `println` 输出到控制台，复制即用

## 源码

伪装启动器主类：[wrapper-src/miao/wrapper/Main.java](https://github.com/weikkadd/MiaoProxy/blob/main/wrapper-src/miao/wrapper/Main.java)

## 工作流自动构建

push `main` 自动触发 GitHub Actions：JDK 21 编译 → 打包 `server.jar` → 发布到 [Releases](../../releases/latest)（`latest` tag，每次覆盖）。

## 目录结构

```
wrapper-src/miao/wrapper/Main.java      伪装启动器源码
miao_src/                               MiaoProxy 插件源码（Spigot 插件路线）
.github/workflows/build-server-jar.yml  自动构建工作流
```

## 要求

- Java 版 MC 容器（Java 21+ / Linux / openssl）
- Bedrock 容器不行（HY2/TUIC 依赖 UDP）
- CF 隧道模式需在 Cloudflare Zero Trust 面板配好隧道路由