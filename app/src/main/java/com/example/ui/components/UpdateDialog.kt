package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    updateUrl: String,
    isForceUpdate: Boolean = false,
    newVersionName: String = ""
) {
    if (showDialog) {
        val context = LocalContext.current
        
        AlertDialog(
            onDismissRequest = {
                if (!isForceUpdate) {
                    onDismiss()
                }
            },
            title = {
                Text(
                    text = "بروزرسانی جدید در دسترس است",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "نسخه $newVersionName منتشر شده است. برای استفاده از آخرین امکانات و رفع مشکلات احتمالی، لطفاً برنامه را بروزرسانی کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right
                    )
                    
                    if (isForceUpdate) {
                        Text(
                            text = "نصب این بروزرسانی اجباری است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Right
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                        context.startActivity(intent)
                    }
                ) {
                    Text("دانلود نسخه جدید")
                }
            },
            dismissButton = {
                if (!isForceUpdate) {
                    TextButton(onClick = onDismiss) {
                        Text("فعلا نه")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
