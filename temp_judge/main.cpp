#include <iostream>
#include <vector>
#include <algorithm>

using namespace std;

int main() {
    // Tối ưu hóa I/O để đọc ghi nhanh hơn
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);
    
    int n;
    if (!(cin >> n)) return 0;
    
    vector<int> a(n);
    for (int i = 0; i < n; ++i) {
        cin >> a[i];
    }
    
    // Tạo mảng b và sắp xếp b tăng dần
    vector<int> b = a;
    sort(b.begin(), b.end());
    
    // Tìm vị trí sai lệch đầu tiên và cuối cùng
    int l = 0, r = n - 1;
    while (l < n && a[l] == b[l]) l++;
    while (r >= 0 && a[r] == b[r]) r--;
    
    // Nếu mảng đã được sắp xếp sẵn từ đầu
    if (l >= r) {
        cout << "YES\n";
        return 0;
    }
    
    // Đảo ngược đoạn [l, r] trong mảng a
    reverse(a.begin() + l, a.begin() + r + 1);
    
    // Kiểm tra xem sau khi đảo, a có khớp với b không
    bool ok = true;
    for (int i = l; i <= r; ++i) {
        if (a[i] != b[i]) {
            ok = false;
            break;
        }
    }
    
    // Kết luận
    if (ok) {
        cout << "YES\n";
    } else {
        cout << "NO\n";
    }
    
    return 0;
}