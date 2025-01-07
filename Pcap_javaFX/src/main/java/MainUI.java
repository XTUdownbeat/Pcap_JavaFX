import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.pcap4j.core.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainUI extends Application {
    private Stage primaryStage;
    private Scene firstScene, secondScene;
    private VBox firstLayout, secondLayout;
    private ListView<String> packetListView;  // 保持 ListView<String> 类型
    private Button startButton;
    private TextField packetLimitField;
    private Button backButton;
    private Button freshButton;
    private int selectedInterfaceIndex = -1;
    private Main packetCapture;

    // 用于存储简要信息与原始数据的映射关系
    private Map<String, String> briefInfoToRawDataMap = new HashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        packetCapture = new Main(this);

        // 第一个页面: 设备选择界面
        firstLayout = new VBox(10);
        firstLayout.setPadding(new Insets(10));
        firstLayout.setAlignment(Pos.CENTER);

        loadNetworkInterfaces();

        firstScene = new Scene(firstLayout, 1000, 700);

        // 为 firstLayout 设置背景图片
        firstLayout.setStyle("-fx-background-image: url('./main.png');"
                + "-fx-background-repeat: no-repeat;"
                + "-fx-background-size: cover;"
                + "-fx-background-position: center;");

        // 加载css文件，添加背景图片
        firstScene.getStylesheets().add(getClass().getResource("style1.css").toExternalForm());

        primaryStage.setTitle("选择网络接口");
        primaryStage.setScene(firstScene);
        primaryStage.show();
    }

    // 加载所有网络接口并动态创建按钮
    private void loadNetworkInterfaces() {
        List<PcapNetworkInterface> devices = packetCapture.getNetworkInterfaces();
        if (!devices.isEmpty()) {
            for (int i = 0; i < devices.size(); i++) {
                PcapNetworkInterface device = devices.get(i);

                // 创建按钮，前面加上序号
                Button interfaceButton = new Button((i) + ". " + device.getName() + " - " + (device.getDescription() != null ? device.getDescription() : "无描述"));
                interfaceButton.setMaxWidth(Double.MAX_VALUE); // 设置按钮宽度最大
                interfaceButton.setMaxHeight(Double.MAX_VALUE); // 设置按钮高度最大
                interfaceButton.setStyle("-fx-font-size: 14px;"); // 设置字体大小，确保显示清晰

                // 设置按钮的高度和宽度填充布局
                VBox.setVgrow(interfaceButton, Priority.ALWAYS); // 按钮垂直填充
                int finalI = i;
                interfaceButton.setOnAction(e -> {
                    selectedInterfaceIndex = finalI; // 保存所选接口的索引
                    showSecondPage();  // 点击按钮后跳转到第二个页面
                });

                firstLayout.getChildren().add(interfaceButton);
            }
        } else {
            Label noDevicesLabel = new Label("没有找到可用的网络接口。");
            firstLayout.getChildren().add(noDevicesLabel);
        }
    }

    // 跳转到第二个页面: 显示抓包数量和抓包数据区域
    private void showSecondPage() {
        // 创建StackPane布局，它允许不同的组件层叠显示
        StackPane stackPane = new StackPane();
        stackPane.setPadding(new Insets(10));

        // 创建第二个页面的根布局
        secondLayout = new VBox(10);
        secondLayout.setPadding(new Insets(10));

        // 显示当前选择的网络接口
        Label selectedLabel = new Label("当前选择接口: " + selectedInterfaceIndex);

        // 输入抓包数量
        Label packetLimitLabel = new Label("请输入抓包的数量：");
        packetLimitField = new TextField();
        packetLimitField.setPromptText("抓包数量");

        // 开始抓包按钮
        startButton = new Button("开始抓包");
        startButton.setOnAction(e -> {
            String packetLimitStr = packetLimitField.getText();
            try {
                int packetLimit = Integer.parseInt(packetLimitStr);
                // 调用 Main 类中的 startPacketCapture 方法
                packetCapture.capturePackets(selectedInterfaceIndex, packetLimit);
            } catch (NumberFormatException | PcapNativeException | NotOpenException | InterruptedException ex) {
                System.out.println("请输入有效的数字！");
            }
        });

        // 输出显示区域
        packetListView = new ListView<>();  // 使用 ListView<String> 显示数据包列表
        packetListView.setOnMouseClicked(e -> {
            String selectedPacketBrief = packetListView.getSelectionModel().getSelectedItem();
            if (selectedPacketBrief != null) {
                String rawData = briefInfoToRawDataMap.get(selectedPacketBrief); // 获取对应的 rawData
                if (rawData != null) {
                    showPacketDetails(rawData);  // 显示对应的 rawData
                }
            }
        });

        // 定义第二个页面简短的抓包信息的字体颜色
        packetListView.setCellFactory(param -> {
            ListCell<String> cell = new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null && !empty) {
                        setText(item);
                        setStyle("-fx-text-fill: black;" +
                                "-fx-font-weight: bold");  // 设置字体颜色为黑色
                    } else {
                        setText(null);
                    }
                }
            };
            return cell;
        });

        // 刷新按钮，用来刷新抓到的数据包
        freshButton = new Button("刷新");
        freshButton.setOnAction(e -> {
            try {
                refreshPacketData();
            } catch (NotOpenException | PcapNativeException | InterruptedException ex) {
                System.out.println("刷新失败");
            }
        });

        // 创建一个HBox使得“开始抓包”和“刷新”按钮在同一行
        HBox buttonBox = new HBox(10, startButton, freshButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10));

        // 返回按钮
        backButton = new Button("返回");
        backButton.setOnAction(e -> {
            primaryStage.setTitle("选择网络接口"); // 返回第一个页面标题
            primaryStage.setScene(firstScene); // 返回第一个页面
        });

        // 将所有组件添加到第二个页面
        secondLayout.getChildren().addAll(
                selectedLabel,
                packetLimitLabel,
                packetLimitField,
                buttonBox,
                packetListView,  // 使用 ListView 显示数据包列表
                backButton
        );

        // 使得 ListView 填充剩余空间
        VBox.setVgrow(packetListView, Priority.ALWAYS);

        // 将整个布局放入StackPane中
        stackPane.getChildren().addAll(secondLayout);

        // 添加背景图片
        stackPane.setStyle("-fx-background-image: url('./packet.png');"
                + "-fx-background-repeat: no-repeat;"
                + "-fx-background-size: cover;"
                + "-fx-background-position: center;");

        // 创建Scene
        secondScene = new Scene(stackPane, 1000, 700);

        // 同样加载CSS文件，添加背景图片
        secondScene.getStylesheets().add(getClass().getResource("style2.css").toExternalForm());
        primaryStage.setScene(secondScene);
    }


    // 定义一个刷新Text区域的方法
    private void refreshPacketData() throws NotOpenException, PcapNativeException, InterruptedException {
        packetListView.getItems().clear();
        packetListView.getItems().add("刷新数据完成！");
    }

    // 数据包详细信息
    private void showPacketDetails(String packetData) {
        // 在此处可以显示选择的数据包详细内容，可以是一个新的窗口或者TextArea显示
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("数据包详细信息");
        alert.setHeaderText("详细信息");

        // 创建TextArea显示数据包内容，允许滚动
        TextArea textArea = new TextArea(packetData);
        textArea.setEditable(false); // 不允许编辑
        textArea.setWrapText(true);  // 自动换行

        // 设置TextArea的样式，确保它不会完全遮盖背景图片
        textArea.setStyle("-fx-control-inner-background: transparent;" // 背景透明
                + "-fx-background-color: transparent;"       // 移除边框背景
                + "-fx-font-family: 'Monospaced';"           // 字体设置
                + "-fx-font-size: 14px;"                     // 字号
                + "-fx-font-weight: bold;"                   // 字体加粗
                + "-fx-text-fill: black;");

        // 创建StackPane，确保背景图在底层，TextArea在上面
        StackPane stackPane = new StackPane();
        stackPane.setStyle("-fx-background-image: url('./Monterey-light.jpg');"
                + "-fx-background-repeat: no-repeat;"
                + "-fx-background-size: cover;"
                + "-fx-background-position: center;");

        stackPane.getChildren().add(textArea); // 将TextArea放在StackPane上

        // 将StackPane设置为DialogPane的内容
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setContent(stackPane); // 使用StackPane作为内容容器

        // 设置对话框可调整大小
        alert.setResizable(true);

        // 设置最小宽高
        dialogPane.setMinWidth(1000);
        dialogPane.setMinHeight(500);

        // 显示对话框
        alert.showAndWait();
    }


    // 在捕获数据包后更新 UI 显示简要信息
    public void updatePacketOutput(String packetData, String rawData) {
        // 生成简要信息
        String[] packetDetails = packetData.split("\\|");

        String info0 = padRight(packetDetails[0].trim(), 30); // 长度为30
        String info1 = padRight(packetDetails[1].trim(), 30); // 长度为20
        String info2 = padRight(packetDetails[2].trim(), 30); // 长度为30

        String briefInfo = String.format("%s 协议：%s IP:%s", info0, info1, info2);

        // 将简要信息添加到 ListView
        Label briefInfoLabel = new Label(briefInfo);
        briefInfoLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 18px; -fx-font-weight: bold;" +
                "-fx-text-fill: black;");

        packetListView.getItems().add(briefInfoLabel.getText());

        // 将简要信息与原始数据映射关系存入 map
        briefInfoToRawDataMap.put(briefInfo, rawData);
    }


    private String padRight(String input, int length) {
        if (input.length() >= length) {
            return input.substring(0, length); // 截取多余部分
        }
        return String.format("%-" + length + "s", input); // 不足补空格
    }
}
