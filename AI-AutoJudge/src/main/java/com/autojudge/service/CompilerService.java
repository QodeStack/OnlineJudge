package com.autojudge.service;

import com.autojudge.utils.FileUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class CompilerService {

    public String compileCode(String language) {
        try {
            ProcessBuilder pb = null;
            
            if (language.equals("Java")) {
                pb = new ProcessBuilder("javac", "Main.java");
            } else if (language.equals("C++")) {
                // Yêu cầu máy tính phải cài sẵn MinGW (g++)
                pb = new ProcessBuilder("g++", "main.cpp", "-o", "main.exe");
            } else if (language.equals("Python")) {
                // Python là ngôn ngữ thông dịch, không cần biên dịch
                return ""; 
            }

            if (pb != null) {
                pb.directory(new File(FileUtils.WORK_DIR));
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }
            return ""; // Không có lỗi
        } catch (Exception e) {
            return "Lỗi gọi trình biên dịch: " + e.getMessage();
        }
    }
}