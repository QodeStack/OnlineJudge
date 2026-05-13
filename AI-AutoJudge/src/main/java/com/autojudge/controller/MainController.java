package com.autojudge.controller;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import com.autojudge.model.JudgeResult;
import com.autojudge.model.SubmissionRecord;
import com.autojudge.model.TestCase;
import com.autojudge.service.AIService;
import com.autojudge.service.CompilerService;
import com.autojudge.service.DatabaseService;
import com.autojudge.service.JudgeService;
import com.autojudge.utils.FileUtils;
import com.google.gson.Gson;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    // ═══════════════════════════════════════════════════════════════
    // FXML FIELDS — LEFT PANEL
    // ═══════════════════════════════════════════════════════════════
    @FXML
    private TextArea problemDescriptionArea;
    @FXML
    private Button btnUploadImage;
    @FXML
    private Button btnAnalyze;
    @FXML
    private TextArea systemLogArea;

    // ═══════════════════════════════════════════════════════════════
    // FXML FIELDS — RIGHT PANEL (EDITOR)
    // THAY ĐỔI: codeEditorArea (TextArea) → codeWebView (WebView)
    // ═══════════════════════════════════════════════════════════════
    @FXML
    private WebView codeWebView; // <-- MỚI
    @FXML
    private Button btnSubmit;
    @FXML
    private ComboBox<String> cbLanguage;
    @FXML
    private Label scoreLabel;

    // ═══════════════════════════════════════════════════════════════
    // FXML FIELDS — TAB PANE
    // ═══════════════════════════════════════════════════════════════
    @FXML
    private TabPane resultTabPane;
    @FXML
    private Tab tabResult;
    @FXML
    private Tab tabHistory;

    // ═══════════════════════════════════════════════════════════════
    // FXML FIELDS — RESULT TABLE (tab 1)
    // ═══════════════════════════════════════════════════════════════
    @FXML
    private TableView<JudgeResult> resultTable;
    @FXML
    private TableColumn<JudgeResult, Integer> colId;
    @FXML
    private TableColumn<JudgeResult, String> colStatus;
    @FXML
    private TableColumn<JudgeResult, String> colTime;
    @FXML
    private TableColumn<JudgeResult, String> colInput;
    @FXML
    private TableColumn<JudgeResult, String> colExpected;
    @FXML
    private TableColumn<JudgeResult, String> colOutput;

    // ═══════════════════════════════════════════════════════════════
    // FXML FIELDS — HISTORY TABLE (tab 2) — MỚI
    // ═══════════════════════════════════════════════════════════════
    @FXML
    private TableView<SubmissionRecord> historyTable;
    @FXML
    private TableColumn<SubmissionRecord, Integer> colHisId;
    @FXML
    private TableColumn<SubmissionRecord, String> colHisTime;
    @FXML
    private TableColumn<SubmissionRecord, String> colHisLang;
    @FXML
    private TableColumn<SubmissionRecord, String> colHisScore;
    @FXML
    private TableColumn<SubmissionRecord, String> colHisStatus;
    @FXML
    private TableColumn<SubmissionRecord, String> colHisCode;
    @FXML
    private Button btnClearHistory;

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════════
    private WebEngine webEngine; // Engine của WebView để gọi JS

    private List<TestCase> currentTestCases = new ArrayList<>();
    private ObservableList<JudgeResult> resultData = FXCollections.observableArrayList();
    private ObservableList<SubmissionRecord> historyData = FXCollections.observableArrayList();

    private AIService aiService;
    private CompilerService compilerService;
    private JudgeService judgeService;
    private DatabaseService databaseService; // MỚI

    private File selectedImageFile = null;

    // Formatter cho timestamp lưu vào DB
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Gson gson = new Gson();

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZE — chạy khi FXML load xong
    // ═══════════════════════════════════════════════════════════════
    @FXML
    public void initialize() {
        // Khởi tạo services
        aiService = new AIService();
        compilerService = new CompilerService();
        judgeService = new JudgeService();
        databaseService = new DatabaseService(); // MỚI — tạo/kết nối DB

        // ── Language ComboBox ──────────────────────────────────────
        cbLanguage.getItems().addAll("Java", "C++", "Python");
        cbLanguage.setValue("Java");
        cbLanguage.setOnAction(e -> onLanguageChanged());

        // ── System log ────────────────────────────────────────────
        systemLogArea.setText("[HỆ THỐNG] Sẵn sàng. Đang tải Monaco Editor...\n");

        // ── Kết quả table ─────────────────────────────────────────
        setupResultTableColumns();
        resultTable.setItems(resultData);

        // ── Lịch sử table ─────────────────────────────────────────
        setupHistoryTableColumns();
        historyTable.setItems(historyData);
        loadHistoryFromDB(); // Tải lịch sử cũ khi mở app

        // ── Button handlers ────────────────────────────────────────
        btnAnalyze.setOnAction(e -> handleAnalyzeProblem());
        btnSubmit.setOnAction(e -> handleSubmitCode());
        btnUploadImage.setOnAction(e -> handleUploadImage());
        btnClearHistory.setOnAction(e -> handleClearHistory());

        // ── Monaco WebView — QUAN TRỌNG ────────────────────────────
        setupMonacoEditor();
    }

    // ═══════════════════════════════════════════════════════════════
    // MONACO EDITOR SETUP
    // Tải file HTML vào WebView, chờ Monaco ready, rồi set template
    // ═══════════════════════════════════════════════════════════════
    private void setupMonacoEditor() {
        webEngine = codeWebView.getEngine();

        // Tải file HTML từ resources/editor/monaco_editor.html
        var htmlUrl = getClass().getResource("/editor/monaco_editor.html");
        if (htmlUrl == null) {
            systemLogArea.appendText("[LỖI] Không tìm thấy file monaco_editor.html trong resources/editor/\n");
            return;
        }
        webEngine.load(htmlUrl.toExternalForm());

        // Lắng nghe sự kiện tải trang HTML xong
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // HTML đã load xong, nhưng Monaco JS còn đang tải async từ CDN
                // → chờ thêm cho đến khi window.editorReady === true
                waitForMonacoReady(() -> {
                    // Monaco đã sẵn sàng — set code template và ngôn ngữ
                    String lang = cbLanguage.getValue();
                    setEditorLanguage(lang);
                    setCode(getDefaultTemplate(lang));
                    systemLogArea.appendText("[EDITOR] ✅ Monaco Editor đã tải xong!\n");
                });
            } else if (newState == Worker.State.FAILED) {
                systemLogArea.appendText("[LỖI] WebView không tải được editor. Kiểm tra internet?\n");
            }
        });
    }

    /**
     * Chờ Monaco Editor load xong (async từ CDN).
     * Cứ 300ms kiểm tra 1 lần, tối đa 60 lần (~18 giây).
     *
     * @param onReady Callback chạy trên JavaFX thread khi Monaco sẵn sàng
     */
    private void waitForMonacoReady(Runnable onReady) {
        waitForMonacoReady(onReady, 0);
    }

    private void waitForMonacoReady(Runnable onReady, int attempt) {
        if (attempt > 60) {
            Platform.runLater(
                    () -> systemLogArea.appendText("[CẢNH BÁO] Monaco tải quá lâu. Kiểm tra kết nối internet.\n"));
            return;
        }

        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(e -> {
            try {
                // Kiểm tra biến window.editorReady trong JS
                Object ready = webEngine.executeScript("window.editorReady === true");
                if (Boolean.TRUE.equals(ready)) {
                    onReady.run(); // Monaco đã sẵn sàng!
                } else {
                    waitForMonacoReady(onReady, attempt + 1); // Thử lại
                }
            } catch (Exception ex) {
                waitForMonacoReady(onReady, attempt + 1); // Thử lại nếu lỗi
            }
        });
        pause.play();
    }

    // ═══════════════════════════════════════════════════════════════
    // MONACO HELPER METHODS
    // Java ↔ JavaScript thông qua webEngine.executeScript()
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lấy code từ Monaco Editor.
     * Gọi hàm getCode() đã định nghĩa trong HTML.
     */
    private String getCode() {
        try {
            Object result = webEngine.executeScript("getCode()");
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            systemLogArea.appendText("[LỖI] Không đọc được code từ editor: " + e.getMessage() + "\n");
            return "";
        }
    }

    /**
     * Set code vào Monaco Editor.
     * Dùng Gson để encode string thành JSON an toàn (xử lý \n, \t, nháy
     * đơn/kép...).
     */
    private void setCode(String code) {
        try {
            // gson.toJson() trả về chuỗi đã escape đúng chuẩn JSON, VD: "hello\nworld"
            String jsonCode = gson.toJson(code);
            // Gọi hàm setCode() trong HTML, truyền chuỗi JSON
            webEngine.executeScript("setCode(" + jsonCode + ")");
        } catch (Exception e) {
            systemLogArea.appendText("[LỖI] Không set được code vào editor: " + e.getMessage() + "\n");
        }
    }

    /**
     * Đổi ngôn ngữ highlight trong Monaco.
     * Map tên hiển thị → tên Monaco language ID.
     */
    private void setEditorLanguage(String displayLang) {
        String monacoLang = switch (displayLang) {
            case "C++" -> "cpp";
            case "Python" -> "python";
            default -> "java";
        };
        try {
            webEngine.executeScript("setLanguage('" + monacoLang + "')");
        } catch (Exception e) {
            // Bỏ qua nếu editor chưa sẵn sàng
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LANGUAGE CHANGE HANDLER
    // ═══════════════════════════════════════════════════════════════
    private void onLanguageChanged() {
        String lang = cbLanguage.getValue();
        if (lang == null)
            return;
        setEditorLanguage(lang);
        setCode(getDefaultTemplate(lang));
    }

    private String getDefaultTemplate(String lang) {
        return switch (lang) {
            case "C++" -> """
                    #include <bits/stdc++.h>
                    using namespace std;

                    int main() {
                        ios_base::sync_with_stdio(false);
                        cin.tie(NULL);

                        // Viết code của bạn ở đây

                        return 0;
                    }
                    """;
            case "Python" -> """
                    import sys
                    input = sys.stdin.readline

                    # Viết code của bạn ở đây

                    """;
            default -> // Java
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                Scanner sc = new Scanner(System.in);
                                // Viết code của bạn ở đây

                            }
                        }
                        """;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER: Phân tích đề bài (không thay đổi nhiều)
    // ═══════════════════════════════════════════════════════════════
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
                                truncate(tc.getExpectedOutput(), 15)));
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

    // ═══════════════════════════════════════════════════════════════
    // HANDLER: Nộp bài
    // THAY ĐỔI: đọc code từ Monaco, lưu kết quả vào SQLite
    // ═══════════════════════════════════════════════════════════════
    private void handleSubmitCode() {
        String currentProblemText = problemDescriptionArea.getText();

        // Đọc code từ Monaco Editor (thay vì TextArea.getText())
        String codeText = getCode();

        if (codeText.trim().isEmpty()) {
            systemLogArea.appendText("[LỖI] Vui lòng nhập code trước khi submit!\n");
            return;
        }
        if (currentTestCases.isEmpty()) {
            systemLogArea.appendText("[LỖI] Vui lòng phân tích đề để tạo testcase trước!\n");
            return;
        }

        String selectedLang = cbLanguage.getValue();
        if (selectedLang == null)
            selectedLang = "Java";
        final String finalLang = selectedLang;
        final String finalCode = codeText; // cần final để dùng trong lambda

        btnSubmit.setDisable(true);
        resultData.clear();
        scoreLabel.setText("Đang chấm...");
        scoreLabel.setStyle("-fx-text-fill: #78909C; -fx-font-weight: bold;");
        systemLogArea.appendText("\n[JUDGE] Bắt đầu biên dịch và chấm bài (" + finalLang + ")...\n");

        Thread thread = new Thread(() -> {
            try {
                // 1. Xác định tên file
                String fileName = switch (finalLang) {
                    case "C++" -> "main.cpp";
                    case "Python" -> "main.py";
                    default -> "Main.java";
                };
                FileUtils.saveCodeToFile(finalCode, fileName);

                // 2. Biên dịch
                String compileError = compilerService.compileCode(finalLang);
                if (!compileError.isEmpty()) {
                    Platform.runLater(() -> {
                        scoreLabel.setText("Compile Error");
                        scoreLabel.setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
                        systemLogArea.appendText("[CE] Lỗi biên dịch:\n" + compileError + "\n");
                        btnSubmit.setDisable(false);

                        String tcJson = gson.toJson(currentTestCases);
        saveToHistory(currentProblemText, tcJson, "[]", finalCode, finalLang, 0, currentTestCases.size(), "CE");
                        loadHistoryFromDB();
                    });
                    return;
                }

                // 3. Chấm từng testcase
                List<JudgeResult> results = new ArrayList<>();
                int passedCount = 0;

                for (TestCase tc : currentTestCases) {
                    JudgeResult result = judgeService.judgeTestCase(tc, finalLang);
                    results.add(result);
                    if ("AC".equals(result.getStatus()))
                        passedCount++;
                }

                final int finalPassed = passedCount;
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

                    /// ... (Tìm chỗ xác định status tổng và lưu)
                    String finalStatus = determineFinalStatus(finalResults, finalPassed, currentTestCases.size());
                    String resJson = gson.toJson(finalResults);
                    
                    // THÊM DÒNG NÀY ĐỂ TẠO LẠI BIẾN tcJson CHO KHỐI LỆNH NÀY:
                    String tcJson = gson.toJson(currentTestCases); 
                    
                    saveToHistory(currentProblemText, tcJson, resJson, finalCode, finalLang, finalPassed, currentTestCases.size(), finalStatus);
                    loadHistoryFromDB();

                    // Chuyển sang tab lịch sử sau khi chấm xong (tùy chọn)
                    // resultTabPane.getSelectionModel().select(tabHistory);
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

    // ═══════════════════════════════════════════════════════════════
    // SQLITE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void saveToHistory(String problemText, String testCasesJson, String resultsJson, String code, String lang,
            int passed, int total, String status) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        SubmissionRecord record = new SubmissionRecord(timestamp, lang, code, problemText, testCasesJson, resultsJson,
                total, passed, status);

        Thread t = new Thread(() -> databaseService.saveSubmission(record));
        t.setDaemon(true);
        t.start();
    }

    /**
     * Đọc lịch sử từ DB và cập nhật historyData (gọi trên background, update UI
     * trên FX thread).
     */
    private void loadHistoryFromDB() {
        Thread t = new Thread(() -> {
            List<SubmissionRecord> records = databaseService.getRecentSubmissions(100);
            Platform.runLater(() -> {
                historyData.setAll(records);
                historyTable.refresh();
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Xác định trạng thái tổng từ danh sách kết quả.
     * AC > WA > TLE > RE (ưu tiên hiển thị lỗi quan trọng nhất)
     */
    private String determineFinalStatus(List<JudgeResult> results, int passed, int total) {
        if (passed == total)
            return "AC";

        // Kiểm tra từng loại lỗi theo độ ưu tiên
        boolean hasTLE = results.stream().anyMatch(r -> "TLE".equals(r.getStatus()));
        boolean hasRE = results.stream().anyMatch(r -> "RE".equals(r.getStatus()));
        boolean hasWA = results.stream().anyMatch(r -> "WA".equals(r.getStatus()));

        if (hasTLE)
            return "TLE";
        if (hasRE)
            return "RE";
        if (hasWA)
            return "WA";
        return "WA"; // fallback
    }

    /** Handler nút "Xóa lịch sử" */
    private void handleClearHistory() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn xóa toàn bộ lịch sử nộp bài?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                databaseService.clearHistory();
                historyData.clear();
                systemLogArea.appendText("[DB] Đã xóa toàn bộ lịch sử.\n");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER: Upload ảnh (không thay đổi)
    // ═══════════════════════════════════════════════════════════════
    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh chứa đề bài");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(btnUploadImage.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            systemLogArea.appendText("[UPLOAD] 📎 Đã chọn ảnh: " + file.getName() + "\n");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TABLE SETUP — KẾT QUẢ CHẤM (giữ nguyên từ code cũ)
    // ═══════════════════════════════════════════════════════════════
    private void setupResultTableColumns() {
        colId.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getTestCaseId()).asObject());
        colId.setStyle("-fx-alignment: CENTER;");

        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
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

        colTime.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getExecutionTimeMs() + " ms"));
        colTime.setStyle("-fx-alignment: CENTER;");

        colInput.setCellValueFactory(cell -> {
            int id = cell.getValue().getTestCaseId();
            String input = currentTestCases.stream()
                    .filter(tc -> tc.getId() == id)
                    .map(tc -> truncate(tc.getInput(), 22))
                    .findFirst().orElse("");
            return new SimpleStringProperty(input);
        });

        colExpected.setCellValueFactory(cell -> {
            int id = cell.getValue().getTestCaseId();
            String expected = currentTestCases.stream()
                    .filter(tc -> tc.getId() == id)
                    .map(tc -> truncate(tc.getExpectedOutput(), 22))
                    .findFirst().orElse("");
            return new SimpleStringProperty(expected);
        });

        colOutput.setCellValueFactory(cell -> new SimpleStringProperty(truncate(cell.getValue().getUserOutput(), 28)));

        resultTable.setRowFactory(tv -> new TableRow<JudgeResult>() {
            @Override
            protected void updateItem(JudgeResult item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    return;
                }
                switch (item.getStatus()) {
                    case "AC":
                        setStyle("-fx-background-color: #F1F8E9;");
                        break;
                    case "WA":
                        setStyle("-fx-background-color: #FFEBEE;");
                        break;
                    case "TLE":
                        setStyle("-fx-background-color: #FFF8E1;");
                        break;
                    case "RE":
                        setStyle("-fx-background-color: #FCE4EC;");
                        break;
                    case "CE":
                        setStyle("-fx-background-color: #FFF3E0;");
                        break;
                    default:
                        setStyle("");
                        break;
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // TABLE SETUP — LỊCH SỬ NỘP BÀI (MỚI)
    // ═══════════════════════════════════════════════════════════════
    private void setupHistoryTableColumns() {

        // Cột ID
        colHisId.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getId()).asObject());
        colHisId.setStyle("-fx-alignment: CENTER;");

        // Cột Thời gian
        colHisTime.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTimestamp()));

        // Cột Ngôn ngữ
        colHisLang.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLanguage()));
        colHisLang.setStyle("-fx-alignment: CENTER;");

        // Cột Điểm (VD: "8 / 10")
        colHisScore.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getScoreText()));
        colHisScore.setStyle("-fx-alignment: CENTER;");

        // Cột Trạng thái (có màu, giống kết quả chấm)
        colHisStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        colHisStatus.setCellFactory(col -> new TableCell<SubmissionRecord, String>() {
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

        // Cột Code preview (hiện 60 ký tự đầu)
        colHisCode.setCellValueFactory(cell -> new SimpleStringProperty(truncate(cell.getValue().getCode(), 60)));

        // Màu nền hàng theo trạng thái
        historyTable.setRowFactory(tv -> new TableRow<SubmissionRecord>() {
            @Override
            protected void updateItem(SubmissionRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    return;
                }
                switch (item.getStatus()) {
                    case "AC":
                        setStyle("-fx-background-color: #F1F8E9;");
                        break;
                    case "WA":
                        setStyle("-fx-background-color: #FFEBEE;");
                        break;
                    case "TLE":
                        setStyle("-fx-background-color: #FFF8E1;");
                        break;
                    case "RE":
                        setStyle("-fx-background-color: #FCE4EC;");
                        break;
                    case "CE":
                        setStyle("-fx-background-color: #FFF3E0;");
                        break;
                    default:
                        setStyle("");
                        break;
                }
            }
        });

        // Double-click để xem lại code, đề bài và kết quả chi tiết
        historyTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                SubmissionRecord selected = historyTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    // 1. Tải lại Code và Ngôn ngữ
                    cbLanguage.setValue(selected.getLanguage());
                    setEditorLanguage(selected.getLanguage());
                    setCode(selected.getCode());
                    
                    // 2. Tải lại Đề bài
                    problemDescriptionArea.setText(selected.getProblemText());

                    // 3. Giải mã JSON phục hồi Testcase (Cần thiết để cột Input/Expected hiển thị)
                    Type tcType = new TypeToken<List<TestCase>>(){}.getType();
                    List<TestCase> pastTCs = gson.fromJson(selected.getTestCasesJson(), tcType);
                    if (pastTCs != null) {
                        currentTestCases = pastTCs;
                    }

                    // 4. Giải mã JSON phục hồi Kết quả chấm (JudgeResult)
                    Type resType = new TypeToken<List<JudgeResult>>(){}.getType();
                    List<JudgeResult> pastResults = gson.fromJson(selected.getResultsJson(), resType);
                    if (pastResults != null) {
                        resultData.setAll(pastResults);
                    } else {
                        resultData.clear();
                    }
                    resultTable.refresh();

                    // 5. Cập nhật lại nhãn Điểm số (Score Label)
                    if ("CE".equals(selected.getStatus())) {
                        scoreLabel.setText("Compile Error");
                        scoreLabel.setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
                    } else {
                        scoreLabel.setText(selected.getScoreText() + " tests passed");
                        if (selected.getPassedTests() == selected.getTotalTests()) {
                            scoreLabel.setStyle("-fx-text-fill: #1B5E20; -fx-font-weight: bold;");
                        } else {
                            scoreLabel.setStyle("-fx-text-fill: #B71C1C; -fx-font-weight: bold;");
                        }
                    }

                    // 6. Chuyển sang tab Kết quả và thông báo
                    resultTabPane.getSelectionModel().select(tabResult);
                    systemLogArea.appendText("[LỊCH SỬ] Đã phục hồi toàn bộ dữ liệu từ lần nộp #" + selected.getId() + "\n");
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER: Rút gọn chuỗi dài
    // ═══════════════════════════════════════════════════════════════
    private String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        s = s.replace("\r\n", "↵").replace("\n", "↵").replace("\r", "");
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}