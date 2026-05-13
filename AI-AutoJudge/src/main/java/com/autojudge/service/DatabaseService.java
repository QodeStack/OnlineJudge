package com.autojudge.service;

import com.autojudge.model.SubmissionRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {

    private static final String DB_URL = "jdbc:sqlite:autojudge_history.db";

    public DatabaseService() {
        initDatabase();
    }

    private void initDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS submissions (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp    TEXT    NOT NULL,
                    language     TEXT    NOT NULL,
                    code         TEXT    NOT NULL,
                    problem_text TEXT    NOT NULL,
                    test_cases   TEXT    NOT NULL,
                    results      TEXT    NOT NULL,
                    total_tests  INTEGER NOT NULL DEFAULT 0,
                    passed_tests INTEGER NOT NULL DEFAULT 0,
                    status       TEXT    NOT NULL
                )
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement  stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Database is ready: autojudge_history.db");
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi khởi tạo database: " + e.getMessage());
        }
    }

    public void saveSubmission(SubmissionRecord record) {
        String sql = """
                INSERT INTO submissions (timestamp, language, code, problem_text, test_cases, results, total_tests, passed_tests, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, record.getTimestamp());
            pstmt.setString(2, record.getLanguage());
            pstmt.setString(3, record.getCode());
            pstmt.setString(4, record.getProblemText());
            pstmt.setString(5, record.getTestCasesJson());
            pstmt.setString(6, record.getResultsJson());
            pstmt.setInt   (7, record.getTotalTests());
            pstmt.setInt   (8, record.getPassedTests());
            pstmt.setString(9, record.getStatus());

            pstmt.executeUpdate();
            System.out.println("[DB] Đã lưu bài nộp: " + record.getStatus() + " (" + record.getScoreText() + ")");

        } catch (SQLException e) {
            System.err.println("[DB] Lỗi lưu submission: " + e.getMessage());
        }
    }

    public List<SubmissionRecord> getRecentSubmissions(int limit) {
        List<SubmissionRecord> list = new ArrayList<>();
        String sql = """
                SELECT id, timestamp, language, code, problem_text, test_cases, results, total_tests, passed_tests, status
                FROM submissions
                ORDER BY id DESC
                LIMIT ?
                """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                list.add(new SubmissionRecord(
                        rs.getInt   ("id"),
                        rs.getString("timestamp"),
                        rs.getString("language"),
                        rs.getString("code"),
                        rs.getString("problem_text"),
                        rs.getString("test_cases"),
                        rs.getString("results"),
                        rs.getInt   ("total_tests"),
                        rs.getInt   ("passed_tests"),
                        rs.getString("status")
                ));
            }

        } catch (SQLException e) {
            System.err.println("[DB] Lỗi đọc lịch sử: " + e.getMessage());
        }
        return list;
    }

    public void clearHistory() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM submissions");
            System.out.println("[DB] Đã xóa toàn bộ lịch sử.");
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi xóa lịch sử: " + e.getMessage());
        }
    }
}