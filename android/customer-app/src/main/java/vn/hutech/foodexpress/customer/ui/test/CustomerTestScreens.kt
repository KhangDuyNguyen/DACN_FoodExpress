package vn.hutech.foodexpress.customer.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.hutech.foodexpress.customer.R

@Composable
fun CustomerTestScreenA(onNavigateToB: () -> Unit) {
    CustomerTestScreen(
        title = stringResource(R.string.test_screen_a_title),
        buttonText = stringResource(R.string.test_navigate_to_b),
        onButtonClick = onNavigateToB,
        showTestImage = true,
    )
}

@Composable
fun CustomerTestScreenB(onNavigateToA: () -> Unit) {
    CustomerTestScreen(
        title = stringResource(R.string.test_screen_b_title),
        buttonText = stringResource(R.string.test_navigate_to_a),
        onButtonClick = onNavigateToA,
    )
}

@Composable
private fun CustomerTestScreen(
    title: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    showTestImage: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (showTestImage) {
            AsyncImage(
                model = R.drawable.coil_test_image,
                contentDescription = stringResource(R.string.test_image_description),
                modifier = Modifier.size(width = 200.dp, height = 120.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        Button(onClick = onButtonClick) {
            Text(text = buttonText)
        }
    }
}
