package com.autojudge.controller;

import com.autojudge.model.JudgeResult;
import com.autojudge.model.TestCase;
import com.autojudge.service.AIService;
import com.autojudge.service.CompilerService;
import com.autojudge.service.JudgeService;
import com.autojudge.utils.FileUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    // LEFT PANEL
    @FXML private TextArea problemDescriptionArea;
    @FXML private Button   btnUploadImage;
    @FXML private Button   btnAnalyze;
    @FXML private TextArea systemLogArea;

    // RIGHT PANEL
    @FXML private TextArea        codeEditorArea;
    @FXML private Button          btnSubmit;
    @FXML private ComboBox<String> cbLanguage;
    @FXML private Label            scoreLabel;

    // RESULTS TABLE
    @FXML private TableView<JudgeResult>           resultTable;
    @FXML private TableColumn<JudgeResult, Integer> colId;
    @FXML private TableColumn<JudgeResult, String>  colStatus;
    @FXML private TableColumn<JudgeResult, String>  colTime;
    @FXML private TableColumn<JudgeResult, String>  colInput;
    @FXML private TableColumn<JudgeResult, String>  colExpected;
    @FXML private TableColumn<JudgeResult, String>  colOutput;

    // State
    private List<TestCase>              currentTestCases = new ArrayList<>();
    private ObservableList<JudgeResult> resultData       = FXCollections.observableArrayList();
    private AIService       aiService;
    private CompilerService compilerService;
    private JudgeService    judgeService;
    private File            selectedImageFile = null;

    // ─────────────────────────────────────────
    //  INITIALIZE
    // ─────────────────────────────────────────
    @FXML
    public void initialize() {
        aiService       = new AIService();
        compilerService = new CompilerService();
        judgeService    = new JudgeService();

        // Language combo
        cbLanguage.getItems().addAll("Java", "C++", "Python");
        cbLanguage.setValue("Java");
        cbLanguage.setOnAction(e -> updateCodeTemplate());

        // Default log message
        systemLogArea.setText("[HỆ THỐNG] Sẵn sàng. Hãy nhập đề bài và nhấn Phân tích.\n");

        // Default Java template
        codeEditorArea.setText(
            "import java.util.Scanner;\n\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        Scanner sc = new Scanner(System.in);\n" +
            "        // Viết code của bạn ở đây\n" +
            "        \n" +
            "    }\n" +
            "}"
        );

        // Setup table columns
        setupTableColumns();
        resultTable.setItems(resultData);

        // Button handlers
        btnAnalyze.setOnAction(e -> handleAnalyzeProblem());
        btnSubmit.setOnAction(e -> handleSubmitCode());
        btnUploadImage.setOnAction(e -> handleUploadImage());
    }

    // ─────────────────────────────────────────
    //  TABLE SETUP
    // ─────────────────────────────────────────
    private void setupTableColumns() {
        // Test ID
        colId.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getTestCaseId()).asObject());
        colId.setStyle("-fx-alignment: CENTER;");

        // Status (màu sắc)
        colStatus.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<JudgeResult, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                switch (status) {
                    case "AC":
                        setText("✅  Accepted");
                        setStyle("-fx-text-fill: #1B5E20; -fx-font-weight: bold;");
                        break;
                    case "WA":
                        setText("❌  Wrong Answer");
                        setStyle("-fx-text-fill: #B71C1C; -fx-font-weight: bold;");
                        break;
                    case "TLE":
                        setText("⏱  Time Limit");
                        setStyle("-fx-text-fill: #BF360C; -fx-font-weight: bold;");
                        break;
                    case "RE":
                        setText("💥  Runtime Error");
                        setStyle("-fx-text-fill: #880E4F; -fx-font-weight: bold;");
                        break;
                    case "CE":
                        setText("⚠  Compile Error");
                        setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
                        break;
                    default:
                        setText("⚙  " + status);
                        setStyle("-fx-text-fill: #546E7A; -fx-font-weight: bold;");
                        break;
                }
            }
        });

        // Time
        colTime.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getExecutionTimeMs() + " ms"));
        colTime.setStyle("-fx-alignment: CENTER;");

        // Input (lấy từ currentTestCases)
        colInput.setCellValueFactory(cell -> {
            int id = cell.getValue().getTestCaseId();
            String input = currentTestCases.stream()
                .filter(tc -> tc.getId() == id)
                .map(tc -> truncate(tc.getInput(), 22))
                .findFirst().orElse("");
            return new SimpleStringProperty(input);
        });

        // Expected output
        colExpected.setCellValueFactory(cell -> {
            int id = cell.getValue().getTestCaseId();
            String expected = currentTestCases.stream()
                .filter(tc -> tc.getId() == id)
                .map(tc -> truncate(tc.getExpectedOutput(), 22))
                .findFirst().orElse("");
            return new SimpleStringProperty(expected);
        });

        // User output
        colOutput.setCellValueFactory(cell ->
            new SimpleStringProperty(truncate(cell.getValue().getUserOutput(), 28)));

        // Màu nền từng hàng theo kết quả
        resultTable.setRowFactory(tv -> new TableRow<JudgeResult>() {
            @Override
            protected void updateItem(JudgeResult item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    return;
                }
                switch (item.getStatus()) {
                    case "AC":  setStyle("-fx-background-color: #F1F8E9;"); break;
                    case "WA":  setStyle("-fx-background-color: #FFEBEE;"); break;
                    case "TLE": setStyle("-fx-background-color: #FFF8E1;"); break;
                    case "RE":  setStyle("-fx-background-color: #FCE4EC;"); break;
                    case "CE":  setStyle("-fx-background-color: #FFF3E0;"); break;
                    default:    setStyle(""); break;
                }
            }
        });
    }

    // ─────────────────────────────────────────
    //  HANDLER: Phân tích đề bài
    // ─────────────────────────────────────────
    private void handleAnalyzeProblem() {
        String problemText = problemDescriptionArea.getText();
        if (problemText.trim().isEmpty() && selectedImageFile == null) {
            systemLogArea.appendText("[LỖI] Bạn phải nhập đề bài hoặc tải ảnh lên!\n");
            return;
        }

        systemLogArea.appendText("\n[AI] Đang gửi dữ liệu cho AI phân tích...\n");
        btnAnalyze.setDisable(true);
        resultData.clear();
        scoreLabel.setText("");

        Thread thread = new Thread(() -> {
            try {
                List<TestCase> generatedCases = aiService.generateTestCases(problemText, selectedImageFile);
                Platform.runLater(() -> {
                    currentTestCases = generatedCases;
                    systemLogArea.appendText("[AI] ✅ Đã sinh xong " + currentTestCases.size() + " testcases!\n");
                    for (TestCase tc : currentTestCases) {
                        systemLogArea.appendText(String.format(
                            "  Test %2d │ Input: %-15s │ Expected: %s\n",
                            tc.getId(),
                            truncate(tc.getInput(), 15),
                            truncate(tc.getExpectedOutput(), 15)
                        ));
                    }
                    btnAnalyze.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    systemLogArea.appendText("[LỖI AI] " + e.getMessage() + "\n");
                    btnAnalyze.setDisable(false);
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // ─────────────────────────────────────────
    //  HANDLER: Submit code
    // ─────────────────────────────────────────
    private void handleSubmitCode() {
        String codeText = codeEditorArea.getText();
        if (codeText.trim().isEmpty()) {
            systemLogArea.appendText("[LỖI] Vui lòng nhập code trước khi submit!\n");
            return;
        }
        if (currentTestCases.isEmpty()) {
            systemLogArea.appendText("[LỖI] Vui lòng phân tích đề để tạo testcase trước!\n");
            return;
        }

        String selectedLang = cbLanguage.getValue();
        if (selectedLang == null) selectedLang = "Java";
        final String finalLang = selectedLang;

        btnSubmit.setDisable(true);
        resultData.clear();
        scoreLabel.setText("Đang chấm...");
        scoreLabel.setStyle("-fx-text-fill: #78909C; -fx-font-weight: bold;");
        systemLogArea.appendText("\n[JUDGE] Bắt đầu biên dịch và chấm bài (" + finalLang + ")...\n");

        Thread thread = new Thread(() -> {
            try {
                // 1. Xác định tên file
                String fileName;
                switch (finalLang) {
                    case "C++":    fileName = "main.cpp";  break;
                    case "Python": fileName = "main.py";   break;
                    default:       fileName = "Main.java"; break;
                }

                FileUtils.saveCodeToFile(codeText, fileName);

                // 2. Biên dịch
                String compileError = compilerService.compileCode(finalLang);
                if (!compileError.isEmpty()) {
                    Platform.runLater(() -> {
                        scoreLabel.setText("Compile Error");
                        scoreLabel.setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
                        systemLogArea.appendText("[CE] Lỗi biên dịch:\n" + compileError + "\n");
                        btnSubmit.setDisable(false);
                    });
                    return;
                }

                // 3. Chấm từng testcase
                List<JudgeResult> results = new ArrayList<>();
                int passedCount = 0;

                for (TestCase tc : currentTestCases) {
                    JudgeResult result = judgeService.judgeTestCase(tc, finalLang);
                    results.add(result);
                    if ("AC".equals(result.getStatus())) {
                        passedCount++;
                    }
                }

                final int              finalPassed  = passedCount;
                final List<JudgeResult> finalResults = results;

                Platform.runLater(() -> {
                    resultData.setAll(finalResults);
                    resultTable.refresh();

                    String scoreText = finalPassed + " / " + currentTestCases.size() + " tests passed";
                    scoreLabel.setText(scoreText);

                    if (finalPassed == currentTestCases.size()) {
                        scoreLabel.setStyle("-fx-text-fill: #1B5E20; -fx-font-weight: bold;");
                        systemLogArea.appendText("[JUDGE] 🎉 " + scoreText + " — Accepted!\n");
                    } else {
                        scoreLabel.setStyle("-fx-text-fill: #B71C1C; -fx-font-weight: bold;");
                        systemLogArea.appendText("[JUDGE] ❌ " + scoreText + "\n");
                    }

                    btnSubmit.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    scoreLabel.setText("System Error");
                    scoreLabel.setStyle("-fx-text-fill: #880E4F;");
                    systemLogArea.appendText("[LỖI HỆ THỐNG] " + e.getMessage() + "\n");
                    btnSubmit.setDisable(false);
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // ─────────────────────────────────────────
    //  HANDLER: Upload ảnh
    // ─────────────────────────────────────────
    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh chứa đề bài");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg")
        );
        File file = fileChooser.showOpenDialog(btnUploadImage.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            systemLogArea.appendText("[UPLOAD] 📎 Đã chọn ảnh: " + file.getName() + "\n");
        }
    }

    // ─────────────────────────────────────────
    //  HELPER: Đổi template theo ngôn ngữ
    // ─────────────────────────────────────────
    private void updateCodeTemplate() {
        String lang = cbLanguage.getValue();
        if (lang == null) return;
        switch (lang) {
            case "C++":
                codeEditorArea.setText(
                    "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    // Viết code của bạn ở đây\n    \n    return 0;\n}"
                );
                break;
            case "Python":
                codeEditorArea.setText(
                    "import sys\ninput = sys.stdin.readline\n\n# Viết code của bạn ở đây\n"
                );
                break;
            default: // Java
                codeEditorArea.setText(
                    "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // Viết code của bạn ở đây\n        \n    }\n}"
                );
                break;
        }
    }

    // ─────────────────────────────────────────
    //  HELPER: Rút gọn chuỗi dài
    // ─────────────────────────────────────────
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        s = s.replace("\r\n", "↵").replace("\n", "↵").replace("\r", "");
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}