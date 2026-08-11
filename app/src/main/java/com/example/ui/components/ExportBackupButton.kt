package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.HamrahanViewModel
import kotlinx.coroutines.launch

@Composable
fun ExportBackupButton(viewModel: HamrahanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                val result = viewModel.exportDatabaseToJson(context)
                result.onSuccess { file ->
                    Toast.makeText(context, "فایل پشتیبان با موفقیت در پوشه دانلودها ذخیره شد:\n${file.name}", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "خطا در تهیه فایل پشتیبان", Toast.LENGTH_SHORT).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("تهیه پشتیبان آفلاین (JSON)")
    }
}
