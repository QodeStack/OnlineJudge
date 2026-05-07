package com.autojudge.service;

import com.autojudge.model.TestCase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AIService {
    // Lưu ý: Nhớ đổi Key mới sau khi dự án hoàn thành để bảo mật!
    private static final String API_KEY = "AIzaSyA2qvcGs7vKP8C1djKHn-xJn4PLEIjw9Dc"; 
    
    // Sử dụng model gemini-2.5-flash thế hệ mới nhất từ danh sách của bạn
   // Dùng bản Lite để né tình trạng kẹt xe của Google
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + API_KEY;
    private final OkHttpClient client;
    private final Gson gson;

    public AIService() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public List<TestCase> generateTestCases(String problemText) throws Exception {
        String systemPrompt = "Bạn là một hệ thống Online Judge chuyên nghiệp. " +
                "Hãy đọc đề bài sau và tạo ra chính xác 10 testcase từ dễ đến khó (bao gồm cả các trường hợp đặc biệt - edge cases). " +
                "CHỈ TRẢ VỀ DUY NHẤT MỘT MẢNG JSON, KHÔNG CÓ BẤT KỲ VĂN BẢN NÀO KHÁC (KHÔNG dùng markdown ```json). " +
                "Định dạng yêu cầu: [{\"id\": 1, \"input\": \"dữ liệu đầu vào\", \"expectedOutput\": \"kết quả mong đợi\", \"explanation\": \"giải thích ngắn\"}]\n\n" +
                "Đề bài:\n" + problemText;

        JsonObject part = new JsonObject();
        part.addProperty("text", systemPrompt);
        
        JsonArray parts = new JsonArray();
        parts.add(part);
        
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
            // NẾU LỖI: Đoạn này sẽ đọc chính xác Google đang chửi gì
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