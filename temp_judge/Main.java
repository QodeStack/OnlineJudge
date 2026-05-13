import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int N = scanner.nextInt(); // Số quả táo
        int M = scanner.nextInt(); // Số học sinh

        if (M == 0) {
            System.out.println(0);
        } else {
            System.out.println(N / M);
        }

        scanner.close();
    }
}