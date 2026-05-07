import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();

        boolean[] isComposite = new boolean[n + 1]; // false = nguyên tố
        long sum = 0;

        for (int i = 2; i <= n; i++) {
            if (!isComposite[i]) {
                sum += i;
                // Đánh dấu tất cả bội số của i là hợp số
                for (long j = (long) i * i; j <= n; j += i) {
                    isComposite[(int) j] = true;
                }
            }
        }

        System.out.println(sum);
    }
}