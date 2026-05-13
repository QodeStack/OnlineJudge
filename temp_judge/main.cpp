#include <iostream>

using namespace std;

int main() {
    int n, m;
    if (!(cin >> n >> m)) return 0;

    // Đáng lẽ phải kiểm tra IF (m == 0) trước, 
    // nhưng lập trình viên quên mất và chia trực tiếp!
    
    int result = n / m; 
    
    cout << result << "\n";

    return 0;
}