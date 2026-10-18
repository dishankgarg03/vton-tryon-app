package com.example.vtonapp

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed interface TryOnUiState {
    object Idle : TryOnUiState
    object Loading : TryOnUiState
    data class Success(val imageUrl: String) : TryOnUiState
    data class Error(val message: String) : TryOnUiState
}

class TryOnViewModel : ViewModel() {
    private val TUNNEL_URL = "https://probe-landfall-pasta.ngrok-free.dev/"
    private val _uiState = MutableStateFlow<TryOnUiState>(TryOnUiState.Idle)
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    private val BASE_URL = if (TUNNEL_URL.endsWith("/")) TUNNEL_URL else "$TUNNEL_URL/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: TryOnApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TryOnApiService::class.java)
    }

    fun processVirtualTryOn(context: Context, personUri: Uri, garmentUri: Uri, category: String) {
        viewModelScope.launch {
            _uiState.value = TryOnUiState.Loading
            try {
                val personBytes = uriToCompressedBytes(context, personUri) ?: throw Exception("Person file read failed")
                val garmentBytes = uriToCompressedBytes(context, garmentUri) ?: throw Exception("Garment file read failed")

                val personPart = MultipartBody.Part.createFormData(
                    "personImage", "person.jpg",
                    personBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val garmentPart = MultipartBody.Part.createFormData(
                    "garmentImage", "garment.jpg",
                    garmentBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val categoryBody = category.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.uploadTryOnTask(personPart, garmentPart, categoryBody)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success && !body.resultImageUrl.isNullOrBlank()) {
                        // Correct path resolution mapping back out from the tunnel base
                        val correctedUrl = body.resultImageUrl.replace("OUTPUT_URL_PLACEHOLDER", TUNNEL_URL.removeSuffix("/"))
                        _uiState.value = TryOnUiState.Success(correctedUrl)
                    } else {
                        _uiState.value = TryOnUiState.Error(body.error ?: "AI inference failed.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val serverMessage = errorBody?.let { body ->
                        try {
                            JSONObject(body).optString("detail").takeIf { it.isNotBlank() } ?: body
                        } catch (_: JSONException) {
                            body
                        }
                    }
                    val message = serverMessage?.let { "Server error: ${response.code()} - $it" }
                        ?: "Server error: ${response.code()}"
                    _uiState.value = TryOnUiState.Error(message)
                }
            } catch (e: Exception) {
                _uiState.value = TryOnUiState.Error(e.localizedMessage ?: "Network connection failed.")
            }
        }
    }

    fun resetState() { _uiState.value = TryOnUiState.Idle }

    private fun uriToCompressedBytes(context: Context, uri: Uri): ByteArray? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val byteBuffer = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (inputStream!!.read(buffer).also { len = it } != -1) {
                byteBuffer.write(buffer, 0, len)
            }
            return byteBuffer.toByteArray()
        } catch (e: Exception) { return null } finally { inputStream?.close() }
    }
}
