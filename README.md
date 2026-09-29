# MiaoProxy 🐾

喵酱多协议伪装启动器 —— 把免费 MC 主机的 `server.jar` 换成本项目产物，后台静默跑 sing-box 多协议代理，面板看到的是一个正常的 Minecraft 服务器。

## 工作流自动构建

push `main` 自动触发 GitHub Actions：JDK 21 编译 `wrapper-src/miao/wrapper/Main.java` → 打包 `server.jar` → 发布到 [Releases](../../releases/latest)（`latest` tag，每次覆盖）。

直接下载：
```
https://github.com/weikkadd/MiaoProxy/releases/download/latest/server.jar
```

## 使用方法

1. 从 Releases 下载 `server.jar`
2. 上传到面板，替换原有的 `server.jar`
3. 在面板 Startup 变量里设置协议端口（见下表）
4. 启动服务器，节点链接打印在控制台日志里

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `HY2_PORT` | 空 | Hysteria2 端口，留空不启动 |
| `TUIC_PORT` | 空 | TUIC 端口，留空不启动 |
| `REALITY_PORT` | 空 | VLESS Reality 端口，留空不启动 |
| `SOCKS_PORT` | 空 | SOCKS5 端口，留空不启动 |
| `SERVER_PORT` | 25748 | MC ping 保活端口（面板分配的 TCP 端口） |
| `CF_QUICK` | 0 | 设为 `1` 启动 Cloudflare 隧道 |
| `CF_TOKEN` | 空 | Cloudflare 隧道 Token |
| `CF_DOMAIN` | 空 | 隧道域名 |
| `CF_NAME` | vmess | 隧道名称 |
| `NEZHA_SERVER` | 136.67.94.3:443 | 哪吒探针地址 |
| `NEZHA_KEY` | 内置 | 哪吒客户端密钥 |
| `NEZHA_TLS` | false | 哪吒是否走 TLS |

## 工作原理

- **伪装**：假 MOTD（Paper 1.20.4）+ 假玩家进出日志 + ServerSocket 响应 MC ping，面板认为服务器正常运行
- **sing-box 统一核心**：根据非空端口变量动态生成 config.json，一个二进制跑全部协议
- **自动签发**：openssl 自签 SNI 证书（apps.apple.com），Reality 密钥对运行时生成
- **节点链接**：启动后直接 `println` 输出到控制台，复制即用

## 源码

伪装启动器主类：[wrapper-src/miao/wrapper/Main.java](https://github.com/weikkadd/MiaoProxy/blob/main/wrapper-src/miao/wrapper/Main.java)

## 目录结构

```
wrapper-src/miao/wrapper/Main.java   伪装启动器源码
miao_src/                            MiaoProxy 插件源码（Spigot 插件路线）
.github/workflows/build-server-jar.yml  自动构建工作流
```

## 要求

- Java 版 MC 容器（Java 21+ / Linux / openssl）
- Bedrock 容器不行（HY2/TUIC 依赖 UDP）