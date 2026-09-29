package miao.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class MiaoProxyPlugin extends JavaPlugin {
    private Process hy2Process;

    private int resolvePort() {
        String[] envKeys = {"HY2_PORT", "BEDROCK_PORT", "UDP_PORT", "SERVER_PORT", "PORT"};
        for (String key : envKeys) {
            String val = System.getenv(key);
            if (val != null && !val.isEmpty()) {
                try {
                    int p = Integer.parseInt(val.trim());
                    getLogger().info("[喵酱] 从环境变量 " + key + "=" + p + " 读取到 HY2 监听端口喵~");
                    return p;
                } catch (NumberFormatException ignored) {}
            }
        }
        int serverPort = getServer().getPort();
        getLogger().warning("[喵酱] 未找到 UDP 端口环境变量，回退使用服务器 TCP 端口 " + serverPort
                + "。HY2 依赖 UDP，该端口可能起不来！请设置 HY2_PORT 环境变量指向 Bedrock 空闲 UDP 端口喵。");
        return serverPort;
    }

    @Override
    public void onEnable() {
        getLogger().info("[喵酱] 插件已加载，准备在后台启动 HY2 喵~ 🐾");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        int port = resolvePort();

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // 1. 下载核心
                File hy2File = new File(getDataFolder(), "hy2_core");
                if (!hy2File.exists()) {
                    getLogger().info("[喵酱] 未找到核心，正在从 Github 下载最新版 HY2... 🐾");
                    URL website = new URL("https://github.com/apernet/hysteria/releases/latest/download/hysteria-linux-amd64");
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(hy2File);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    hy2File.setExecutable(true, false);
                }

                // 2. 签发指定的 SNI 自签证书
                File certFile = new File(getDataFolder(), "cert.crt");
                File keyFile = new File(getDataFolder(), "private.key");
                if (!certFile.exists() || !keyFile.exists()) {
                    getLogger().info("[喵酱] 正在签发最新的 SNI 证书 (apps.apple.com) 喵...");
                    ProcessBuilder sslPb = new ProcessBuilder("openssl", "req", "-x509", "-nodes", "-newkey", "rsa:2048", "-keyout", keyFile.getAbsolutePath(), "-out", certFile.getAbsolutePath(), "-days", "3650", "-subj", "/CN=apps.apple.com");
                    sslPb.start().waitFor();
                }

                // 3. 生成配置文件
                File configFile = new File(getDataFolder(), "config.yaml");
                String defaultConfig = "listen: :" + port + "\n" +
                                       "tls:\n" +
                                       "  cert: cert.crt\n" +
                                       "  key: private.key\n" +
                                       "auth:\n" +
                                       "  type: password\n" +
                                       "  password: e3a5bb40be52de65\n";
                Files.write(configFile.toPath(), defaultConfig.getBytes());
                getLogger().info("[喵酱] 配置文件已更新，绑定端口: " + port + " 喵！");

                // 4. 获取当前服务器的公网 IP 和 国家
                String publicIp = "127.0.0.1";
                String country = "未知节点";
                try {
                    URL ipUrl = new URL("http://ip-api.com/json/?lang=zh-CN");
                    HttpURLConnection con = (HttpURLConnection) ipUrl.openConnection();
                    con.setRequestMethod("GET");
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                    String json = content.toString();
                    if (json.contains("\"query\":\"")) publicIp = json.split("\"query\":\"")[1].split("\"")[0];
                    if (json.contains("\"country\":\"")) country = json.split("\"country\":\"")[1].split("\"")[0];
                } catch(Exception e) {
                    getLogger().warning("[喵酱] 获取公网IP失败了喵。");
                }

                // 5. 打印直连链接
                getLogger().info("========================================================");
                getLogger().info("[喵酱] 主人，你的插件版节点链接生成完毕喵！");
                getLogger().info("hysteria2://e3a5bb40be52de65@" + publicIp + ":" + port + "/?sni=apps.apple.com&insecure=1#" + country);
                getLogger().info("========================================================");

                // 6. 启动 HY2
                ProcessBuilder pb = new ProcessBuilder(hy2File.getAbsolutePath(), "server", "-c", configFile.getAbsolutePath());
                pb.directory(getDataFolder());
                pb.redirectErrorStream(true);
                hy2Process = pb.start();

                // 过滤日志，防止把控制台刷屏
                BufferedReader reader = new BufferedReader(new InputStreamReader(hy2Process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("server up and running") || line.contains("ERROR")) {
                        getLogger().info("[HY2] " + line);
                    }
                }

            } catch (Exception e) {
                getLogger().severe("[喵酱] 启动代理核心时发生了错误喵: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        if (hy2Process != null && hy2Process.isAlive()) {
            hy2Process.destroy();
            getLogger().info("[喵酱] 插件卸载，已成功清理后台 HY2 进程喵！");
        }
    }
}
