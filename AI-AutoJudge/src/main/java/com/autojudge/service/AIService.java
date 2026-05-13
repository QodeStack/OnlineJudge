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
    
    // 1. Lấy Key ra và BẮT BUỘC dùng .trim() để gọt sạch khoảng trắng/ký tự ẩn
    // Nếu không đọc được file .env, biến này sẽ rỗng ("") để tránh lỗi NullPointerException
    private static final String API_KEY = dotenv.get("GEMINI_API_KEY") != null ? dotenv.get("GEMINI_API_KEY").trim() : ""; 
    
    // 2. Chú ý: Đổi lại model thành gemini-2.5-flash (vì bản -lite có thể chưa được cấp quyền chính thức)
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
    
    private final OkHttpClient client;
    private final Gson gson;

    public AIService() {
        // Cảnh báo ngay lập tức nếu Java không đọc được file .env
        if (API_KEY.isEmpty()) {
            System.err.println("[LỖI BẢO MẬT] Không tìm thấy API Key hoặc file .env bị rỗng! Hãy kiểm tra lại thư mục của bạn.");
        }

        this.client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public List<TestCase> generateTestCases(String problemText, File imageFile) throws Exception {
String systemPrompt = 
    "Bạn là một hệ thống sinh testcase chuyên nghiệp cấp độ ICPC/Codeforces. Nhiệm vụ của bạn là đọc đề bài và tạo ra CHÍNH XÁC 10 testcase CỰC KỲ CHẤT LƯỢNG.\n\n" +

    "=== QUY TRÌNH BẮT BUỘC ===\n" +
    "BƯỚC 1 - ĐỌC KỸ ĐỀ BÀI: Xác định rõ:\n" +
    "  - Bài toán yêu cầu làm gì? (logic chính)\n" +
    "  - Định dạng INPUT là gì? (số nguyên, chuỗi, mảng, nhiều dòng...)\n" +
    "  - Định dạng OUTPUT là gì?\n" +
    "  - Các RÀNG BUỘC (constraints) cụ thể: VD: 1 ≤ N ≤ 10^6, -10^9 ≤ A[i] ≤ 10^9\n\n" +

    "BƯỚC 2 - LẬP KẾ HOẠCH 10 TESTCASE theo thứ tự từ dễ đến khó:\n" +
    "  [Test 1-2]  Trivial cases: Input nhỏ nhất có thể theo ràng buộc (N=1, giá trị min)\n" +
    "  [Test 3-4]  Basic cases: Input nhỏ, kết quả dễ kiểm tra bằng tay\n" +
    "  [Test 5-6]  Middle cases: Input trung bình, bao phủ logic chính của bài\n" +
    "  [Test 7-8]  Edge cases: Các trường hợp đặc biệt quan trọng:\n" +
    "                - Tất cả phần tử giống nhau\n" +
    "                - Giá trị âm / số 0 (nếu ràng buộc cho phép)\n" +
    "                - Mảng đã được sắp xếp sẵn / sắp xếp ngược\n" +
    "                - Kết quả là chính giá trị biên (boundary)\n" +
    "  [Test 9-10] Stress cases: Input LỚN NHẤT theo ràng buộc (N=10^6, giá trị max/min)\n\n" +

    "BƯỚC 3 - VỚI MỖI TESTCASE, KIỂM TRA LẠI:\n" +
    "  ✓ Input có tuân thủ ĐÚNG định dạng đề bài không? (đúng số dòng, đúng thứ tự)\n" +
    "  ✓ Các giá trị có NẰM TRONG ràng buộc không? (không vượt quá min/max)\n" +
    "  ✓ expectedOutput có CHÍNH XÁC 100% không? (tự tính tay hoặc trace code)\n" +
    "  ✓ Nếu output là số thực, có làm tròn đúng không?\n\n" +

    "=== YÊU CẦU OUTPUT ===\n" +
    "CHỈ TRẢ VỀ DUY NHẤT MỘT MẢNG JSON, TUYỆT ĐỐI KHÔNG có text/markdown thừa.\n" +
    "Định dạng: [{\"id\": 1, \"input\": \"...\", \"expectedOutput\": \"...\", \"explanation\": \"Loại test: [tên loại] - Lý do chọn testcase này\"}]\n\n" +
    "LƯU Ý QUAN TRỌNG VỀ ĐỊNH DẠNG INPUT:\n" +
    "  - Nếu input gồm nhiều dòng, dùng \\n để ngăn cách (VD: \"5\\n1 2 3 4 5\")\n" +
    "  - Không thêm khoảng trắng thừa ở đầu/cuối\n" +
    "  - expectedOutput phải khớp CHÍNH XÁC với output chuẩn (kể cả \\n nếu cần)\n\n" +

    "=== ĐỀ BÀI ===\n" + problemText;

        JsonArray parts = new JsonArray();

        // 1. Thêm phần Text (Câu lệnh)
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", systemPrompt);
        parts.add(textPart);

        // 2. Thêm phần Ảnh (Nếu người dùng có upload)
        if (imageFile != null && imageFile.exists()) {
            // Đọc file ảnh và mã hóa sang dạng Base64
            byte[] fileContent = Files.readAllBytes(imageFile.toPath());
            String base64Encoded = Base64.getEncoder().encodeToString(fileContent);

            // Xác định định dạng ảnh
            String mimeType = "image/jpeg";
            if (imageFile.getName().toLowerCase().endsWith(".png")) {
                mimeType = "image/png";
            }

            // Đóng gói theo chuẩn Gemini Vision
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mimeType", mimeType);
            inlineData.addProperty("data", base64Encoded);

            JsonObject imagePart = new JsonObject();
            imagePart.add("inlineData", inlineData);
            parts.add(imagePart);
        }

        // Đóng gói JSON gửi đi
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