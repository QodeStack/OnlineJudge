package com.autojudge.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {
    // Thư mục tạm để lưu code và file thực thi
    public static final String WORK_DIR = "temp_judge";

    public static void saveCodeToFile(String code, String fileName) throws IOException {
        File dir = new File(WORK_DIR);
        if (!dir.exists()) {
            dir.mkdirs(); // Tạo thư mục nếu chưa có
        }
        Path path = Paths.get(WORK_DIR, fileName);
        Files.writeString(path, code);
    }
}