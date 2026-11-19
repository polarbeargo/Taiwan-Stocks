package com.polarbeargo.twstocks

import android.app.Application
import com.polarbeargo.twstocks.data.StockRepository
import com.polarbeargo.twstocks.data.TwseService
import com.polarbeargo.twstocks.data.TwseStockRepository
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

open class StocksApplication : Application() {
    open val repository: StockRepository by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        TwseStockRepository(
            TwseService(client, "https://openapi.twse.com.tw/v1/".toHttpUrl()),
            Dispatchers.IO,
        )
    }
}
