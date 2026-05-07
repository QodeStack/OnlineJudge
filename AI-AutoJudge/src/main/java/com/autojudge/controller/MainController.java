package com.autojudge.controller;

import com.autojudge.model.JudgeResult;
import com.autojudge.model.TestCase;
import com.autojudge.service.AIService;
import com.autojudge.service.CompilerService;
import com.autojudge.service.JudgeService;
import com.autojudge.utils.FileUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    @FXML private TextArea problemDescriptionArea;
    @FXML private Button btnUploadImage;
    @FXML private Button btnAnalyze;
    @FXML private TextArea systemLogArea;

    @FXML private TextArea codeEditorArea;
    @FXML private Button btnSubmit;
    @FXML private TextArea resultArea;
    @FXML private ComboBox<String> cbLanguage;

    private List<TestCase> currentTestCases = new ArrayList<>();
    private AIService aiService;
    private CompilerService compilerService;
    private JudgeService judgeService;
    private File selectedImageFile = null;

    @FXML
    public void initialize() {
        aiService = new AIService();
        compilerService = new CompilerService();
        judgeService = new JudgeService();
        cbLanguage.getItems().addAll("Java", "C++", "Python");
        cbLanguage.setValue("Java"); // Mặc định là Java

        systemLogArea.setText("Hệ thống đã sẵn sàng. Vui lòng nhập đề bài.\n");
        
        // Mặc định tạo khung code Java cho người dùng dễ hình dung
        codeEditorArea.setText("import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // Viết code của bạn ở đây\n        \n    }\n}");

        btnAnalyze.setOnAction(event -> handleAnalyzeProblem());
        btnSubmit.setOnAction(event -> handleSubmitCode());
        btnUploadImage.setOnAction(event -> handleUploadImage());
    }

    private void handleAnalyzeProblem() {
        String problemText = problemDescriptionArea.getText();
        
        // Nếu text trống mà cũng CHƯA chọn ảnh thì mới báo lỗi
        if (problemText.trim().isEmpty() && selectedImageFile == null) {
            systemLogArea.appendText("[LỖI] Bạn phải nhập đề bài hoặc tải ảnh lên!\n");
            return;
        }

        systemLogArea.appendText("\nĐang gửi dữ liệu (Text/Ảnh) cho AI phân tích. Vui lòng đợi...\n");
        btnAnalyze.setDisable(true);

        new Thread(() -> {
            try {
                // TRUYỀN THÊM selectedImageFile VÀO ĐÂY:
                List<TestCase> generatedCases = aiService.generateTestCases(problemText, selectedImageFile);
                
                Platform.runLater(() -> {
                    currentTestCases = generatedCases;
                    systemLogArea.appendText("[THÀNH CÔNG] Đã sinh xong " + currentTestCases.size() + " testcases từ đề bài!\n");
                    for (TestCase tc : currentTestCases) {
                        systemLogArea.appendText(String.format("Test %d: Input [%s] -> Output [%s]\n", 
                                tc.getId(), tc.getInput(), tc.getExpectedOutput()));
                    }
                    btnAnalyze.setDisable(false);
                    // Có thể reset ảnh nếu muốn: selectedImageFile = null;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    systemLogArea.appendText("[LỖI AI] " + e.getMessage() + "\n");
                    btnAnalyze.setDisable(false);
                });
            }
        }).start();
    }

    private void handleSubmitCode() {
        String codeText = codeEditorArea.getText();
        if (codeText.trim().isEmpty()) {
            resultArea.setText("Vui lòng nhập code trước khi submit!");
            return;
        }
        if (currentTestCases.isEmpty()) {
            resultArea.setText("Vui lòng phân tích đề để tạo testcase trước khi submit!");
            return;
        }

        // Lấy ngôn ngữ người dùng đang chọn từ ComboBox
        String selectedLang = cbLanguage.getValue();
        if (selectedLang == null) {
            selectedLang = "Java"; // Đề phòng lỗi null, mặc định gán là Java
        }

        btnSubmit.setDisable(true);
        resultArea.setText("Đang biên dịch và chấm bài bằng " + selectedLang + "...\n");

        // Tạo biến final copy để dùng bên trong Thread
        final String finalLang = selectedLang;

        // Chạy tiến trình chấm bài trên luồng riêng
        new Thread(() -> {
            try {
                // 1. Xác định tên file để lưu mã nguồn tùy theo ngôn ngữ
                String fileName = "Main.java"; // Mặc định cho Java
                if (finalLang.equals("C++")) {
                    fileName = "main.cpp";
                } else if (finalLang.equals("Python")) {
                    fileName = "main.py";
                }

                // Lưu code vào ổ cứng
                FileUtils.saveCodeToFile(codeText, fileName);

                // 2. Biên dịch mã nguồn (Python sẽ tự bỏ qua bước này trong CompilerService)
                String compileError = compilerService.compileCode(finalLang);
                if (!compileError.isEmpty()) {
                    Platform.runLater(() -> {
                        resultArea.setText("Kết quả: CE (Compile Error - Lỗi cú pháp)\nChi tiết lỗi:\n" + compileError);
                        btnSubmit.setDisable(false);
                    });
                    return;
                }

                // 3. Tiến hành chạy code và chấm từng testcase
                StringBuilder finalResultLog = new StringBuilder();
                int passedCount = 0;

                for (TestCase tc : currentTestCases) {
                    // Truyền ngôn ngữ vào Máy chấm (JudgeService)
                    JudgeResult result = judgeService.judgeTestCase(tc, finalLang);
                    
                    finalResultLog.append(String.format("Test %d: %s | %d ms\n", 
                            result.getTestCaseId(), result.getStatus(), result.getExecutionTimeMs()));

                    if (result.getStatus().equals("AC")) {
                        passedCount++;
                    } else {
                        // In chi tiết lỗi nếu testcase sai hoặc chạy quá giờ
                        finalResultLog.append(String.format("   -> Input: %s\n   -> Expected: %s\n   -> Your Output: %s\n",
                                tc.getInput(), tc.getExpectedOutput(), result.getUserOutput()));
                    }
                }

                // 4. Tổng kết số lượng testcase Pass
                finalResultLog.append(String.format("\n=== TỔNG KẾT: %d/%d Testcases Passed ===", passedCount, currentTestCases.size()));

                // Cập nhật lên giao diện
                Platform.runLater(() -> {
                    resultArea.setText(finalResultLog.toString());
                    btnSubmit.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.setText("Lỗi hệ thống chấm: " + e.getMessage());
                    btnSubmit.setDisable(false);
                });
            }
        }).start();
    }

    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh chứa đề bài");
        // Chỉ cho phép chọn ảnh
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg")
        );
        
        // Mở cửa sổ chọn file
        File file = fileChooser.showOpenDialog(btnUploadImage.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            systemLogArea.appendText("[THÀNH CÔNG] Đã tải ảnh lên: " + file.getName() + " (Sẵn sàng phân tích)\n");
        }
    }
}