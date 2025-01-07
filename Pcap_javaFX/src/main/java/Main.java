import javafx.concurrent.Task;
import javafx.scene.control.Label;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;

import javafx.scene.paint.Color;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Main {
    private MainUI mainUI;  // 用于更新 UI
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // 构造方法接收 MainUI 实例
    public Main(MainUI mainUI) {
        this.mainUI = mainUI;
    }


    // 获取所有网络接口
    public List<PcapNetworkInterface> getNetworkInterfaces() {
        try {
            return Pcaps.findAllDevs();
        } catch (PcapNativeException e) {
            System.out.println("获取网络接口时发生错误: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // 捕获数据包
    public void capturePackets(int interfaceIndex, int packetLimit) throws PcapNativeException, NotOpenException, InterruptedException {
        List<PcapNetworkInterface> devices = Pcaps.findAllDevs();
        if (interfaceIndex < 0 || interfaceIndex >= devices.size()) {
            System.out.println("无效的接口索引");
            return;
        }

        PcapNetworkInterface nif = devices.get(interfaceIndex);
        System.out.println("选择的网络接口: " + nif.getName() + " - " + nif.getDescription());

        // 创建一个后台任务来进行数据包捕获
        Task<Void> captureTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (PcapHandle handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10000)) {
                    int packetCount = 0;
                    String startCapture = "叮叮咚，正在抓取数据包......" + "\n" + "可能耗时较长请耐心等待" + "\n";
                    System.out.println(startCapture);

                    /* 设置“开始抓包”的字体颜色
                    Platform.runLater(() -> {
                        Label startCaptureLabel = new Label(startCapture);
                        startCaptureLabel.setTextFill(Color.BLUE);
                        startCaptureLabel.setStyle("-fx-font-size: 16px;");
                        mainUI.updatePacketOutput(startCapture);
                    });*/

                    // 捕获数据包并显示
                    while (packetCount < packetLimit) {
                        Packet packet = handle.getNextPacket();
                        if (packet != null) {
                            byte[] rawData = packet.getRawData();
                            String timestamp = getTimestamp();
                            String protocolType = getProtocolType(packet);
                            String ipAddress = getIPaddress(packet);

                            // 生成简要信息
                            String briefInfo = String.format("%-10s | %-6s | %-30s ", timestamp, protocolType, ipAddress);

                            // 格式化数据包信息
                            String finalFormattedPacketData = formatPacketData(rawData, timestamp, protocolType, ipAddress);
                            final int finalPacketCount = packetCount;

                            // 更新简要信息到 UI
                            int finalPacketCount1 = packetCount;
                            Platform.runLater(() -> {
                                mainUI.updatePacketOutput("第" + (finalPacketCount1+1) + "个: "+ briefInfo , "数据包 " + (finalPacketCount + 1) + ":\n" + finalFormattedPacketData + "\n");
                            });

                            logger.info("第" + (packetCount + 1) + "个数据包：\n" + finalFormattedPacketData);
                            packetCount++;
                        }
                    }

                    System.out.println("数据包捕获完成。");
                }
                return null;
            }
        };

        // 在后台线程中启动任务
        new Thread(captureTask).start();
    }


    // 获取当前时间戳
    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        return sdf.format(new Date());
    }

    // 获取协议类型
    private static String getProtocolType(Packet packet) {
        if (packet.contains(EthernetPacket.class)) {
            EthernetPacket ethernetPacket = packet.get(EthernetPacket.class);

            // 检查是否包含 IPv4 层
            if (ethernetPacket.contains(IpV4Packet.class)) {
                IpV4Packet ipv4Packet = ethernetPacket.get(IpV4Packet.class);

                // 检查是否包含 TCP 层
                if (ipv4Packet.contains(TcpPacket.class)) {
                    TcpPacket tcpPacket = ipv4Packet.get(TcpPacket.class);

                    // 判断是否是 HTTP 或 HTTPS 流量
                    if (tcpPacket.getHeader().getDstPort().value() == 80 || tcpPacket.getHeader().getSrcPort().value() == 80) {
                        return "HTTP";
                    } else if (tcpPacket.getHeader().getDstPort().value() == 443 || tcpPacket.getHeader().getSrcPort().value() == 443) {
                        return "HTTPS";
                    }
                    return "TCP";  // 其他 TCP 流量
                }
                // 检查是否包含 UDP 层
                else if (ipv4Packet.contains(UdpPacket.class)) {
                    return "UDP";
                } else {
                    return "IP (Other)";
                }
            }
            // 检查是否包含 IPv6 层
            else if (ethernetPacket.contains(IpV6Packet.class)) {
                return "IPv6";
            } else {
                return "以太网 (未知IP层)";
            }
        }
        return "Unknown";
    }

    // 获取 IP 地址
    private static String getIPaddress(Packet packet) {
        if (packet.contains(EthernetPacket.class)) {
            EthernetPacket ethernetPacket = packet.get(EthernetPacket.class);
            if (ethernetPacket.contains(IpV4Packet.class)) {
                IpV4Packet ipv4Packet = ethernetPacket.get(IpV4Packet.class);
                String srcIP = ipv4Packet.getHeader().getSrcAddr().getHostAddress();
                String desIP = ipv4Packet.getHeader().getDstAddr().getHostAddress();
                String s = srcIP + " --> " + desIP;
                return s; // 获取源 IP 地址
            } else if (ethernetPacket.contains(IpV6Packet.class)) {
                IpV6Packet ipv6Packet = ethernetPacket.get(IpV6Packet.class);
                String srcIP = ipv6Packet.getHeader().getSrcAddr().getHostAddress();
                String desIP = ipv6Packet.getHeader().getDstAddr().getHostAddress();
                String s = srcIP + " --> " + desIP;
                return s; // 获取源 IPv6 地址
            }
        }
        return null;
    }

    // 格式化数据包内容为16进制和ASCII
    private static String formatPacketData(byte[] rawData, String timestamp, String protocolType, String ipAddress) {
        StringBuilder formattedOutput = new StringBuilder();
        int length = rawData.length;
        int lineSize = 25;  // 每行显示25个字节

        formattedOutput.append("时间: ").append(String.format("%-10s", timestamp));
        formattedOutput.append(" 长度: ").append(String.format("%-6s", length));
        formattedOutput.append(" 协议: ").append(String.format("%-8s", protocolType));
        formattedOutput.append(String.format("%-60s",ipAddress)).append("\n");
        // 定义每行输出的最大长度
        int hexWidth = lineSize * 3;  // 每个字节占用3个字符（包括空格），25个字节
        int asciiWidth = lineSize;     // ASCII列，最多25个字符

        for (int i = 0; i < length; i += lineSize) {
            StringBuilder hexLine = new StringBuilder();
            StringBuilder asciiLine = new StringBuilder();

            // 处理当前行的16个字节
            for (int j = i; j < i + lineSize && j < length; j++) {
                // 16进制输出
                hexLine.append(String.format("%02X ", rawData[j]));

                // ASCII输出
                if (rawData[j] >= 32 && rawData[j] <= 126) {
                    asciiLine.append((char) rawData[j]);
                } else {
                    asciiLine.append(".");  // 非打印字符用"."代替
                }
            }

            // 填充16进制列，使其宽度固定
            while (hexLine.length() < hexWidth) {
                hexLine.append(" ");  // 填充空格使16进制列对齐
            }

            // 填充ASCII列，使其宽度固定
            while (asciiLine.length() < asciiWidth) {
                asciiLine.append(" ");  // 填充空格使ASCII列对齐
            }

            // 输出格式：16进制部分和ASCII部分之间用空格分隔，确保"|"对齐
            formattedOutput.append(String.format("%-75s | %-16s", hexLine.toString(), asciiLine.toString()));
            formattedOutput.append("\n");
        }

        return formattedOutput.toString();
    }
}
