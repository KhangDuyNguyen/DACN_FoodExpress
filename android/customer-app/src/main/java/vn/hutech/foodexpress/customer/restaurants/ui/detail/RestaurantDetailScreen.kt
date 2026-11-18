package vn.hutech.foodexpress.customer.restaurants.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import vn.hutech.foodexpress.customer.restaurants.data.model.MenuItemDto

private val Orange = Color(0xFFFF6B35)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantDetailScreen(
    onBack: () -> Unit,
    viewModel: RestaurantDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.restaurant?.name ?: "Chi tiết quán", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color(0xFFF8F9FA),
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Orange)
            }

            state.error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("😕", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(state.error ?: "Lỗi kết nối", color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::retry) { Text("Thử lại") }
            }

            else -> {
                val restaurant = state.restaurant!!
                val menu = state.menu
                val categories = menu?.categories ?: emptyList()
                val selectedCatId = state.selectedCategoryId ?: categories.firstOrNull()?.id
                val selectedItems = categories.find { it.id == selectedCatId }?.items ?: emptyList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // Cover image
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFFE0E0E0)),
                        ) {
                            if (restaurant.coverUrl != null) {
                                AsyncImage(
                                    model = restaurant.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            // Open status badge
                            val isOpen = restaurant.openStatus == "OPEN"
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(
                                        if (isOpen) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    if (isOpen) "🟢 Đang mở" else "🔴 Đã đóng",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // Info section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = restaurant.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                            if (restaurant.cuisines.isNotEmpty()) {
                                Text(
                                    text = restaurant.cuisines.joinToString(" · ") { it.name },
                                    color = Orange,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("⭐ ${String.format("%.1f", restaurant.avgRating)} (${restaurant.ratingCount})", fontSize = 13.sp)
                                Text("🕐 ${restaurant.prepTimeMinutes} phút", fontSize = 13.sp)
                                Text("💳 Min ${restaurant.minOrderAmount / 1000}k", fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("📍 ${restaurant.address}", fontSize = 13.sp, color = Color.Gray)
                            if (!restaurant.phone.isNullOrBlank()) {
                                Text("📞 ${restaurant.phone}", fontSize = 13.sp, color = Color.Gray)
                            }
                            if (!restaurant.description.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(restaurant.description, fontSize = 13.sp, color = Color(0xFF555555))
                            }
                        }
                    }

                    // Menu category tabs
                    if (categories.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Thực đơn",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            val selectedIndex = categories.indexOfFirst { it.id == selectedCatId }.coerceAtLeast(0)
                            ScrollableTabRow(
                                selectedTabIndex = selectedIndex,
                                containerColor = Color.White,
                                contentColor = Orange,
                                edgePadding = 8.dp,
                            ) {
                                categories.forEachIndexed { idx, cat ->
                                    Tab(
                                        selected = idx == selectedIndex,
                                        onClick = { viewModel.selectCategory(cat.id) },
                                        text = { Text("${cat.name} (${cat.items.size})") },
                                    )
                                }
                            }
                        }
                    }

                    // Menu items
                    items(selectedItems, key = { it.id }) { item ->
                        MenuItemCard(
                            item = item,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }

                    if (selectedItems.isEmpty() && !state.isLoading) {
                        item {
                            Text(
                                "Nhóm món này chưa có món ăn.",
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(
    item: MenuItemDto,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp)),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Item image
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Text("🍴", fontSize = 28.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!item.description.isNullOrBlank()) {
                            Text(
                                text = item.description,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (!item.isAvailable) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEEEEEE), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("Hết món", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                // Option groups summary
                if (item.optionGroups.isNotEmpty()) {
                    Text(
                        text = item.optionGroups.joinToString(", ") { it.name },
                        fontSize = 11.sp,
                        color = Orange,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${item.price.toLocaleString()}đ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Orange,
                    )
                    if (item.isAvailable) {
                        Button(
                            onClick = { /* TODO: add to cart (tuần 6) */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Orange),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("+ Thêm", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (!item.isAvailable) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            )
        }
    }
}

// Extension helper
private fun Long.toLocaleString(): String =
    String.format("%,d", this).replace(',', '.')
