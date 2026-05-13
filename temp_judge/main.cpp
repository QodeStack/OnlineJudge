#include <iostream>

int main() {
    int N, M;
    std::cin >> N >> M;
    if (M == 0) {
        std::cout << 0 << std::endl;
    } else {
        std::cout << N / M << std::endl;
    }
    return 0;
}