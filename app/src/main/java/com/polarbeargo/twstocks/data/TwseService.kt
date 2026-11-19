package com.polarbeargo.twstocks.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TwseService(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
) {
    suspend fun get(dataset: String): String = suspendCancellableCoroutine { continuation ->
        val url = baseUrl.newBuilder().addPathSegments("exchangeReport/$dataset").build()
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) throw IOException("TWSE HTTP ${it.code}")
                        it.body?.string() ?: throw IOException("TWSE response has no body")
                    }
                    continuation.resume(body)
                } catch (error: IOException) {
                    continuation.resumeWithException(error)
                }
            }
        })
    }
}
