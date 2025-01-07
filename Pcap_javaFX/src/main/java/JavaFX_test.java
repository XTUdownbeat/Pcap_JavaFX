import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class JavaFX_test extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 创建一个文本控件
        Text text = new Text("Hello, JavaFX!");

        // 创建一个按钮
        Button btn = new Button("Click Me!");

        // 设置按钮点击事件
        btn.setOnAction(event -> text.setText("You clicked the button!"));

        // 使用 VBox 布局管理器，将按钮和文本垂直排列
        VBox vbox = new VBox(20); // 20 是控件之间的间距
        vbox.getChildren().addAll(text, btn); // 添加文本和按钮

        // 创建一个场景，并设置布局
        Scene scene = new Scene(vbox, 300, 250);

        // 设置舞台（窗口）标题
        primaryStage.setTitle("JavaFX Test");
        primaryStage.setScene(scene);

        // 显示窗口
        primaryStage.show();
    }

    public static void main(String[] args) {
        // 启动 JavaFX 应用
        launch(args);
    }
}
