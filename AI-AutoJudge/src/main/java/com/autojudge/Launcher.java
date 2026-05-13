package com.autojudge;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        // Gọi hàm main của MainApp từ đây
        MainApp.main(args);
    }
}