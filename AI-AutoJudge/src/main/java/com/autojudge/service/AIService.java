package com.autojudge.service;

import com.autojudge.model.TestCase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AIService {
    
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY") != null ? dotenv.get("GEMINI_API_KEY").trim() : ""; 
    
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
    
    private final OkHttpClient client;
    private final Gson gson;

    public AIService() {

        if (API_KEY.isEmpty()) {
            System.err.println("[LỖI BẢO MẬT] Không tìm thấy API Key hoặc file .env bị rỗng! Hãy kiểm tra lại thư mục của bạn.");
        }

        this.client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }
    public String generateReferenceCode(String problemText, String language) throws Exception {
    // 1. Chuẩn bị Prompt
    String prompt = String.format(
        "Bạn là một chuyên gia thuật toán. Hãy giải bài toán sau bằng ngôn ngữ %s.\n" +
        "Yêu cầu:\n" +
        "- Sử dụng thuật toán tối ưu nhất (về thời gian và bộ nhớ).\n" +
        "- Chỉ trả về duy nhất mã nguồn, không giải thích gì thêm.\n" +
        "- Đảm bảo code có thể biên dịch và chạy được ngay.\n\n" +
        "ĐỀ BÀI:\n%s", 
        language, problemText
    );

    // 2. Đóng gói JSON gửi đi (Tương tự như hàm sinh testcase nhưng đơn giản hơn)
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", prompt);
    JsonArray parts = new JsonArray();
    parts.add(textPart);

    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonArray contents = new JsonArray();
    contents.add(content);

    JsonObject requestBodyJson = new JsonObject();
    requestBodyJson.add("contents", contents);

    RequestBody body = RequestBody.create(
            requestBodyJson.toString(),
            MediaType.parse("application/json; charset=utf-8")
    );

    Request request = new Request.Builder().url(API_URL).post(body).build();

    try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) throw new IOException("Lỗi kết nối AI: " + response.code());
        
        String responseString = response.body().string();
        JsonObject jsonResponse = gson.fromJson(responseString, JsonObject.class);
        String code = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();
        
        // Xóa các ký tự markdown như ```cpp hay ```java
        return code.replaceAll("(?s)```.*?\\n", "").replace("```", "").trim();
    }
}

    public List<TestCase> generateTestCases(String problemText, File imageFile) throws Exception {
String systemPrompt = 
    "Bạn là một hệ thống sinh testcase chuyên nghiệp cấp độ ICPC/Codeforces. Nhiệm vụ của bạn là đọc đề bài và tạo ra CHÍNH XÁC 10 testcase CỰC KỲ CHẤT LƯỢNG.\n\n" +

    "=== QUY TRÌNH BẮT BUỘC ===\n" +
    "BƯỚC 1 - ĐỌC KỸ ĐỀ BÀI: Xác định rõ logic, định dạng và các RÀNG BUỘC (constraints).\n\n" +

    "BƯỚC 2 - LẬP KẾ HOẠCH 10 TESTCASE: Từ Trivial, Basic đến Edge cases và Stress cases (max ràng buộc).\n\n" +

    "BƯỚC 3 - KIỂM CHỨNG BẰNG CODE THAM CHIẾU (QUAN TRỌNG NHẤT):\n" +
    "  - Với mỗi testcase, bạn PHẢI tự viết và chạy ngầm một đoạn 'code trâu' (thuật toán chuẩn xác 99.9%) để tính toán kết quả.\n" +
    "  - TUYỆT ĐỐI KHÔNG đoán mò kết quả. Dùng code này để đối chiếu và đảm bảo 'expectedOutput' khớp hoàn toàn với logic bài toán.\n\n" +

    "BƯỚC 4 - KIỂM TRA ĐỊNH DẠNG:\n" +
    "  ✓ Input tuân thủ đúng số dòng và định dạng đề bài.\n" +
    "  ✓ Các giá trị NẰM TRONG ràng buộc cho phép.\n\n" +

    "=== YÊU CẦU OUTPUT ===\n" +
    "CHỈ TRẢ VỀ DUY NHẤT MỘT MẢNG JSON, TUYỆT ĐỐI KHÔNG có text/markdown thừa.\n" +
    "Định dạng: [{\"id\": 1, \"input\": \"...\", \"expectedOutput\": \"...\", \"explanation\": \"...\"}]\n\n" +
    "LƯU Ý: Trường 'explanation' hãy ghi vắn tắt thuật toán bạn đã dùng để kiểm chứng (VD: 'Dùng mảng đánh dấu', 'Dùng phép chia nguyên').\n\n" +

    "=== ĐỀ BÀI ===\n" + problemText;

        JsonArray parts = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", systemPrompt);
        parts.add(textPart);

        if (imageFile != null && imageFile.exists()) {
            // Đọc file ảnh và mã hóa sang dạng Base64
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            String base64Encoded = Base64.getEncoder().encodeToString(fileContent);

            String mimeType = "image/jpeg";
            if (imageFile.getName().toLowerCase().endsWith(".png")) {
                mimeType = "image/png";
            }

            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mimeType", mimeType);
            inlineData.addProperty("data", base64Encoded);

            JsonObject imagePart = new JsonObject();
            imagePart.add("inlineData", inlineData);
            parts.add(imagePart);
        }


        JsonObject content = new JsonObject();
        content.add("parts", parts);
        
        JsonArray contents = new JsonArray();
        contents.add(content);
        
        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.add("contents", contents);

        RequestBody body = RequestBody.create(
                requestBodyJson.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorDetails = response.body() != null ? response.body().string() : "Không có chi tiết lỗi";
                throw new IOException("HTTP " + response.code() + " - Chi tiết từ Google: " + errorDetails);
            }

            String responseString = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseString, JsonObject.class);
            String aiGeneratedText = jsonResponse
                    .getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();

            aiGeneratedText = aiGeneratedText.replace("```json", "").replace("```", "").trim();

            Type listType = new TypeToken<List<TestCase>>(){}.getType();
            return gson.fromJson(aiGeneratedText, listType);
        }
    }

}