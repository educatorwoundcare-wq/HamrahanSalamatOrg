package com.example.ui.dev

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.HamrahanViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperAuthScreen(
    viewModel: HamrahanViewModel,
    navController: NavController
) {
    val lockoutRemaining by viewModel.devAuthLockoutRemaining.collectAsState()
    var pin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (viewModel.isDevSessionValid()) {
            navController.navigate(com.example.ui.navigation.DeveloperControlCenter) {
                popUpTo(com.example.ui.navigation.DeveloperAuth) { inclusive = true }
            }
        } else {
            viewModel.notifyDevAuthScreenOpened()
        }
    }

    // Auto-verify when 4 digits are entered
    LaunchedEffect(pin) {
        if (pin.length == 4 && lockoutRemaining == 0L) {
            val success = viewModel.verifyDevPin(pin)
            if (success) {
                errorMessage = "ورود موفق بود"
                delay(300)
                navController.navigate(com.example.ui.navigation.DeveloperControlCenter) {
                    popUpTo(com.example.ui.navigation.DeveloperAuth) { inclusive = true }
                }
            } else {
                pin = ""
                errorMessage = "رمز توسعه‌دهنده صحیح نیست."
            }
        } else if (pin.length > 4) {
            pin = pin.take(4)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 ورود به بخش توسعه‌دهنده", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "این بخش مخصوص توسعه و عیب‌یابی نرم‌افزار است.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (lockoutRemaining > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ورود موقتاً قفل شده است.",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لطفاً ${lockoutRemaining / 1000} ثانیه صبر کنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Text(
                    text = "رمز توسعه‌دهنده را وارد کنید:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { newPin ->
                        if (newPin.length <= 4) {
                            pin = newPin
                            errorMessage = ""
                        }
                    },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "🙈 مخفی کردن رمز" else "👁 نمایش رمز"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    singleLine = true,
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(0.7f),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp)
                )

                if (errorMessage.isNotEmpty() && errorMessage != "ورود موفق بود") {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (errorMessage == "ورود موفق بود") {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
