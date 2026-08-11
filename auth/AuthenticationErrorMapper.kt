package com.example.data.auth

import com.example.data.FirebaseException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class FirebaseErrorDetail(
    val code: Int = 0,
    val message: String = ""
)

data class FirebaseErrorPayload(
    val error: FirebaseErrorDetail? = null
)

object AuthenticationErrorMapper {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val errorAdapter = moshi.adapter(FirebaseErrorPayload::class.java)

    fun parseFirebaseErrorReason(rawBody: String?): String {
        if (rawBody.isNullOrBlank()) return "پاسخ خالی از سرور"
        return try {
            val payload = errorAdapter.fromJson(rawBody)
            payload?.error?.message ?: rawBody
        } catch (e: Exception) {
            rawBody
        }
    }

    fun mapHttpResponse(response: Response, rawBody: String?): FirebaseException {
        val code = response.code
        val firebaseReason = parseFirebaseErrorReason(rawBody)

        return when {
            firebaseReason.contains("INVALID_KEY") || firebaseReason.contains("API key not valid") -> {
                FirebaseException.AuthenticationError(code, firebaseReason, "کلید ارتباط با سرور Firebase معتبر نیست (کد $code).")
            }
            firebaseReason.contains("PROJECT_NOT_FOUND") -> {
                FirebaseException.UnknownError(code, "پروژه Firebase یافت نشد (کد $code).")
            }
            firebaseReason.contains("INVALID_REFRESH_TOKEN") || firebaseReason.contains("TOKEN_EXPIRED") -> {
                FirebaseException.TokenExpired("توکن احراز هویت منقضی شده یا نامعتبر است: $firebaseReason")
            }
            firebaseReason.contains("USER_DISABLED") -> {
                FirebaseException.AuthenticationError(code, firebaseReason, "حساب کاربری توسط سرور مسدود شده است.")
            }
            firebaseReason.contains("OPERATION_NOT_ALLOWED") -> {
                FirebaseException.PermissionDenied(code, "ورود گمنام (Anonymous Auth) در کنسول Firebase فعال نشده است.")
            }
            code == 401 || code == 403 -> {
                FirebaseException.PermissionDenied(code, "عدم دسترسی به سرور ابری (کد $code): $firebaseReason")
            }
            code == 404 -> {
                FirebaseException.WorkspaceNotFound("مرکز یا آدرس مورد نظر در سرور ابری یافت نشد (کد $code).")
            }
            code == 429 -> {
                FirebaseException.AuthenticationError(code, firebaseReason, "تعداد درخواست‌ها بیش از حد مجاز است. لطفاً کمی بعد تلاش کنید (کد $code).")
            }
            code in 500..599 -> {
                FirebaseException.UnknownError(code, "خطای داخلی سرور ابری Firebase (کد $code): $firebaseReason")
            }
            else -> {
                FirebaseException.AuthenticationError(code, firebaseReason, "خطای احراز هویت در سرور (کد $code): $firebaseReason")
            }
        }
    }

    fun mapThrowable(throwable: Throwable, defaultMessage: String): FirebaseException {
        if (throwable is FirebaseException) return throwable

        return when (throwable) {
            is SocketTimeoutException -> {
                FirebaseException.NetworkError("مهلت زمانی ارتباط با سرور ابری به پایان رسید (Timeout): ${throwable.localizedMessage}", throwable)
            }
            is UnknownHostException -> {
                FirebaseException.NetworkError("اتصال اینترنت برقرار نیست یا آدرس سرور ابری یافت نشد: ${throwable.localizedMessage}", throwable)
            }
            is SSLException -> {
                FirebaseException.NetworkError("خطای امنیت گواهی SSL در ارتباط با سرور: ${throwable.localizedMessage}", throwable)
            }
            is IOException -> {
                FirebaseException.NetworkError("خطای ارتباط شبکه هنگام اتصال به سرور: ${throwable.localizedMessage}", throwable)
            }
            else -> {
                FirebaseException.UnknownError(0, "$defaultMessage: ${throwable.localizedMessage}", throwable)
            }
        }
    }
}
