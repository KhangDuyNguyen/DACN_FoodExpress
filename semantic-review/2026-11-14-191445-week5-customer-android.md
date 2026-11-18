# Customer App Android – Week 5 (Restaurant List + Detail)

Tuần 5 bổ sung toàn bộ luồng khám phá nhà hàng cho app khách Android: danh sách quán có tìm kiếm, lọc theo cuisine, và phân trang cuộn vô hạn; màn chi tiết quán + thực đơn theo danh mục. Retrofit được nối vào `Retrofit` instance đã có từ `core`, Hilt DI wiring hoạt động đúng chiều, và Navigation Component truyền `restaurantId` kiểu `Long`. Tuy nhiên có hai lỗi **build ngay** (KSP version không khớp Kotlin, thiếu Coil network fetcher), hai lỗi **runtime** (pagination race condition, `hasMore` tính sai), và một lỗi compile tiềm ẩn ở `RestaurantDetailViewModel`.

**Watch for:** KSP `2.3.0` không tồn tại cho Kotlin `2.2.21` → build thất bại ngay; `coil3` thiếu `coil-network-okhttp` → ảnh sẽ không tải được trên Android; race condition trong `loadRestaurants` khi debounce và filter kích hoạt song song.

**Verdict**: NEEDS_CHANGES

---

## High-level view

**KSP / Kotlin version mismatch** là lỗi chặn build ngay lập tức. `libs.versions.toml` khai báo `ksp = "2.3.0"` và `kotlin = "2.2.21"`, nhưng KSP versioning theo format `<kotlin-version>-<ksp-release>` – phiên bản KSP phù hợp với Kotlin 2.2.21 phải là `2.2.21-1.0.x`, không tồn tại `2.3.0` cho Kotlin 2.2.21.

**Coil 3 thiếu network fetcher** khiến `AsyncImage` luôn trả về trống trên Android thật/emulator. `coil3` đã tách engine tải ảnh mạng thành artifact riêng; nếu không khai báo `io.coil-kt.coil3:coil-network-okhttp` thì `AsyncImage` chỉ load được ảnh local, mọi URL đều fail im lặng.

**Pagination race condition** xảy ra khi người dùng gõ chữ nhanh. `debounce(400)` gọi `loadRestaurants(reset=true)`, nhưng `loadRestaurants` đọc `state.keyword` từ `_uiState.value` tại thời điểm coroutine chạy, không phải từ `_searchQuery.value` vừa trigger. Nếu state chưa cập nhật (do coroutine trước chưa xong) thì request gửi đi với keyword cũ.

**`hasMore` tính theo kích thước page cuối** (`resp.data.size >= 20`) thay vì so sánh `page < totalPages`. Nếu tổng số quán chia hết cho 20, page cuối sẽ có đúng 20 phần tử, `hasMore = true`, app gửi thêm một request dư thừa và nhận về response rỗng.

**`checkNotNull` trong `RestaurantDetailViewModel`** sẽ ném `IllegalStateException` ở runtime nếu `restaurantId` không có trong `SavedStateHandle` – xảy ra khi process recreation hoặc deep link sai. Hiện tại Navigation đã wiring đúng nhưng lỗi này cần được xử lý gracefully.

**`core` và `driver-app` vẫn dùng `kapt`** trong khi `customer-app` đã chuyển sang `ksp`. Hai bộ annotation processor cùng tồn tại trong một multi-module project là hợp lệ nhưng làm chậm build và cần chú ý khi migrate.

---

<details>
<summary>Issues (7)</summary>

1. **KSP version không tồn tại cho Kotlin 2.2.21** — `libs.versions.toml` dòng 2 khai báo `ksp = "2.3.0"` không hợp lệ với Kotlin `2.2.21`. Sửa thành `ksp = "2.2.21-1.0.31"` (hoặc bản `1.0.x` mới nhất cho Kotlin 2.2.21).

2. **Coil 3 thiếu `coil-network-okhttp`** — Không có artifact network fetcher, toàn bộ `AsyncImage` load URL sẽ fail im lặng. Thêm `coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }` vào toml và `implementation(libs.coil.network.okhttp)` vào `customer-app/build.gradle.kts`.

3. **Race condition pagination: keyword đọc từ stale state** — `loadRestaurants` trong `RestaurantListViewModel` đọc `state.keyword` từ `_uiState.value` tại dòng `val state = _uiState.value`, nhưng debounce observer chỉ update `_searchQuery`, chưa chắc `_uiState.value.keyword` đã cập nhật tương ứng. Sửa: truyền `keyword` và `cuisineId` trực tiếp vào `loadRestaurants` thay vì đọc lại từ state.

4. **`hasMore` false negative/positive** — `RestaurantListViewModel` dòng: `hasMore = resp.data.size >= 20`. Khi tổng quán chia hết cho 20, page cuối trả đúng 20 phần tử → app request thêm rồi nhận rỗng; khi size thực là 15/page thì đúng. Nên sửa thành `hasMore = (nextPage + 1) < resp.totalPages`.

5. **`checkNotNull` crash không được bắt** — `RestaurantDetailViewModel` dòng `private val restaurantId: Long = checkNotNull(savedStateHandle["restaurantId"])` ném `IllegalStateException` nếu key không có (deep link sai, process restart với back stack không khớp). Nên bắt exception này và đẩy vào `error` state thay vì crash.

6. **`kapt` / `ksp` lẫn lộn trong multi-module** — `core` và `driver-app` vẫn dùng `kapt` cho Hilt compiler, `customer-app` dùng `ksp`. Không gây lỗi nhưng tăng build time và nên migrate đồng nhất về `ksp` theo hướng đã chọn ở `customer-app`.

7. **Load more trigger khi danh sách ít hơn 3 item** — `lastIndex >= state.restaurants.size - 3` luôn đúng khi size < 3, dẫn đến page 1 bị request ngay cùng lúc page 0 vẫn đang chạy. Thêm guard `state.restaurants.isNotEmpty()` vào điều kiện.

</details>

---

<details>
<summary>Details</summary>

## KSP 2.3.0 không tồn tại cho Kotlin 2.2.21

`libs.versions.toml` dòng 2:
```toml
ksp = "2.3.0"
kotlin = "2.2.21"
```

KSP release versioning theo schema `<kotlin-version>-<release>`, ví dụ `2.2.21-1.0.31`. Phiên bản `2.3.0` là KSP cho Kotlin 2.3.x (chưa release). Gradle sẽ báo lỗi "Could not resolve com.google.devtools.ksp:2.3.0" ngay khi sync. Sửa:
```toml
ksp = "2.2.21-1.0.31"
```

**Confidence: confirmed** — versioning scheme được document tại [github.com/google/ksp/releases](https://github.com/google/ksp/releases).

---

## Coil 3 không tải được ảnh mạng (thiếu network fetcher)

Coil 3 tách phần fetch HTTP ra khỏi artifact core. `coil-compose` chỉ cung cấp `AsyncImage` composable và bộ decoder; nếu không có `coil-network-okhttp`, Coil không biết cách fetch URL `http://` hay `https://`. Mọi `AsyncImage(model = "https://...")` sẽ fail im lặng (placeholder hoặc trống), không có exception để debug.

`libs.versions.toml` cần thêm:
```toml
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
```

`customer-app/build.gradle.kts` cần thêm:
```kotlin
implementation(libs.coil.network.okhttp)
```

**Confidence: confirmed** — tài liệu Coil 3 migration guide ghi rõ network fetcher là tùy chọn tách biệt từ v3.0.

---

## Race condition pagination: keyword stale trong loadRestaurants

Trong `RestaurantListViewModel`, `onKeywordChange` cập nhật cả `_searchQuery` và `_uiState.keyword`. `observeSearch` debounce 400ms rồi gọi `loadRestaurants(reset=true)`. Nhưng `loadRestaurants` đọc keyword như sau:

```kotlin
private fun loadRestaurants(reset: Boolean) {
    val state = _uiState.value          // ← đọc snapshot tại đây
    val nextPage = if (reset) 0 else state.page
    _uiState.value = state.copy(isLoading = true, error = null)
    viewModelScope.launch {
        runCatching {
            api.searchRestaurants(
                keyword = state.keyword.takeIf { it.isNotBlank() },  // ← dùng snapshot cũ
```

`_uiState.value = _uiState.value.copy(keyword = kw)` trong `onKeywordChange` chạy synchronously trên main thread, nhưng nếu coroutine từ lần `loadRestaurants` trước chưa complete và đang chạy `_uiState.value = ...` trên background, thì snapshot đọc được có thể là keyword cũ. Ngoài ra khi `onCuisineSelected` gọi `loadRestaurants(reset=true)` ngay sau khi set `selectedCuisineId`, `loadRestaurants` đọc snapshot ngay lập tức trước khi state commit xong.

Cách sửa an toàn: truyền `keyword` và `cuisineId` vào hàm thay vì đọc từ state:
```kotlin
private fun loadRestaurants(reset: Boolean, keyword: String = _uiState.value.keyword, cuisineId: Int? = _uiState.value.selectedCuisineId) { ... }
```

**Confidence: likely** — pattern đọc state trước khi launch coroutine là race tiêu biểu trong ViewModel với multiple producers.

---

## hasMore tính sai khi tổng record chia hết cho page size

```kotlin
hasMore = resp.data.size >= 20,
```

Nếu backend có đúng 40 quán và page size = 20:
- Page 0 → 20 items → `hasMore = true` ✓
- Page 1 → 20 items → `hasMore = true` ✗ (đây là trang cuối)
- Page 2 → 0 items → app gửi request dư, nhận rỗng, `hasMore = false`

Backend đã trả về `totalPages` trong `RestaurantPageResponse`. Sửa:
```kotlin
hasMore = (nextPage + 1) < resp.totalPages,
```

**Confidence: confirmed** — logic đọc từ code, `totalPages` có sẵn trong response model.

---

## checkNotNull crash không được xử lý gracefully

```kotlin
private val restaurantId: Long = checkNotNull(savedStateHandle["restaurantId"])
```

`checkNotNull` ném `IllegalStateException` tại thời điểm ViewModel khởi tạo, trước khi state được set. Hilt sẽ không bắt exception này, Activity sẽ crash. Trường hợp xảy ra: deep link tới detail screen với URL sai format, hoặc process death + restore với back stack không nhất quán.

Nên dùng:
```kotlin
private val restaurantId: Long? = savedStateHandle["restaurantId"]

init {
    if (restaurantId == null) {
        _uiState.value = RestaurantDetailUiState(isLoading = false, error = "ID quán không hợp lệ")
    } else {
        load()
    }
}
```

**Confidence: confirmed** — `checkNotNull` không wrapping, exception propagates lên Activity.

---

## kapt / ksp lẫn lộn trong multi-module

`customer-app` dùng `ksp` plugin và `ksp(libs.hilt.compiler)`. `core` và `driver-app` vẫn dùng `kotlin-kapt` plugin và `kapt(libs.hilt.compiler)`. Hilt compiler hỗ trợ cả hai, nên không gây lỗi compile. Tuy nhiên Gradle phải chạy cả `kaptGenerateStubs` và `kspDebugKotlin` task set, làm tăng build time. Nên migrate `core` và `driver-app` sang `ksp` theo cùng pattern của `customer-app`.

**Confidence: confirmed** — đọc trực tiếp từ `core/build.gradle.kts` và `driver-app/build.gradle.kts`.

---

## Load more trigger quá sớm

```kotlin
if (lastIndex != null && lastIndex >= state.restaurants.size - 3) {
    viewModel.loadMore()
}
```

`snapshotFlow` emit mỗi khi visible items thay đổi. Khi danh sách có ít hơn 3 item (ví dụ mới load page 0 xong với 2 kết quả), `state.restaurants.size - 3` âm, điều kiện luôn đúng ngay từ đầu. `loadMore()` đã có guard `if (_uiState.value.isLoading || !_uiState.value.hasMore) return`, nhưng nếu `hasMore` vẫn là `true` (default) trước khi page 0 xong, thì page 1 sẽ được request ngay lập tức cùng lúc page 0.

Sửa thêm guard:
```kotlin
if (lastIndex != null && state.restaurants.isNotEmpty() && lastIndex >= state.restaurants.size - 3) {
```

**Confidence: confirmed** — `RestaurantListUiState` default `hasMore = true`, `page = 0`.

---



</details>

---

<details>
<summary>File map</summary>

| File | Thay đổi |
|------|----------|
| `gradle/libs.versions.toml` | Khai báo versions + libraries + plugins; `ksp = "2.3.0"` không hợp lệ cho Kotlin 2.2.21; thiếu `coil-network-okhttp` |
| `customer-app/build.gradle.kts` | Dùng KSP, Hilt, Compose, Coil 3; thiếu `coil-network-okhttp` dependency |
| `restaurants/data/model/RestaurantModels.kt` | Toàn bộ DTO model: Cuisine, Restaurant list/detail, Menu |
| `restaurants/data/api/RestaurantApiService.kt` | Retrofit interface: cuisines, search, detail, menu |
| `restaurants/data/api/RestaurantModule.kt` | Hilt module provide `RestaurantApiService` |
| `restaurants/ui/list/RestaurantListViewModel.kt` | Pagination + search + filter state management; race condition + hasMore bug |
| `restaurants/ui/list/RestaurantListScreen.kt` | Compose UI: search bar, cuisine chips, restaurant cards, load more |
| `restaurants/ui/detail/RestaurantDetailViewModel.kt` | Parallel load detail + menu; `checkNotNull` crash |
| `restaurants/ui/detail/RestaurantDetailScreen.kt` | Compose UI: cover image, info, category tabs, menu items |
| `navigation/CustomerAppNavigation.kt` | NavHost với Long argument; đúng |
| `CustomerApplication.kt` | `@HiltAndroidApp`; đúng |
| `MainActivity.kt` | `@AndroidEntryPoint`; đúng |
| `core/.../NetworkModule.kt` | Provide `Retrofit` singleton cho toàn app |
| `core/.../ApiConfig.kt` | Base URL theo DEBUG/RELEASE |
| `AndroidManifest.xml` | INTERNET permission; đúng |

Diff đầy đủ: `git diff main -- android/`

</details>
