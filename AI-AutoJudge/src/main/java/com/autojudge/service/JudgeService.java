package com.autojudge.service;

import com.autojudge.model.JudgeResult;
import com.autojudge.model.TestCase;
import com.autojudge.utils.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class JudgeService {

    private static final long TIME_LIMIT_MS = 2000; // Giới hạn thời gian: 2 giây (2000ms)

    public JudgeResult judgeTestCase(TestCase testCase, String language) {
        long startTime = System.currentTimeMillis();
        
        try {
            ProcessBuilder pb = null;
            if (language.equals("Java")) {
                pb = new ProcessBuilder("java", "Main");
            } else if (language.equals("C++")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "main.exe"); // Chạy file exe trên Windows
            } else if (language.equals("Python")) {
                pb = new ProcessBuilder("python", "main.py");
            }

            pb.directory(new File(FileUtils.WORK_DIR));
            Process process = pb.start();

            // 1. "Bơm" Input của testcase vào System.in của tiến trình đang chạy
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(testCase.getInput());
                writer.flush();
            }

            // 2. Chờ tiến trình chạy xong với thời gian giới hạn (bắt lỗi TLE)
            boolean finished = process.waitFor(TIME_LIMIT_MS, TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly(); // Ép buộc dừng nếu chạy quá lâu (bị lặp vô hạn)
                return new JudgeResult(testCase.getId(), "TLE", executionTime, "Quá thời gian thực thi (Time Limit Exceeded)");
            }

            // 3. Đọc Output mà code của người dùng in ra (System.out)
            String userOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            // Đọc thêm luồng lỗi (nếu có lỗi Runtime Error như Exception, chia cho 0...)
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!errorOutput.isEmpty() && process.exitValue() != 0) {
                return new JudgeResult(testCase.getId(), "RE", executionTime, "Runtime Error: " + errorOutput);
            }

            // 4. So sánh kết quả của User Output với Expected Output từ AI
            String expectedOutput = testCase.getExpectedOutput().trim();
            
            // Xử lý chuẩn hóa khoảng trắng/dấu xuống dòng để so sánh chính xác hơn
            userOutput = userOutput.replace("\r\n", "\n");
            expectedOutput = expectedOutput.replace("\r\n", "\n");

            if (userOutput.equals(expectedOutput)) {
                return new JudgeResult(testCase.getId(), "AC", executionTime, userOutput); // Accepted
            } else {
                return new JudgeResult(testCase.getId(), "WA", executionTime, userOutput); // Wrong Answer
            }

        } catch (Exception e) {
            return new JudgeResult(testCase.getId(), "SYS_ERR", 0, "Lỗi hệ thống chấm: " + e.getMessage());
        }
    }
}