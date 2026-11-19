package com.polarbeargo.twstocks

import com.polarbeargo.twstocks.data.TwseService
import com.polarbeargo.twstocks.data.TwseStockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

class StockRepositoryTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
    private lateinit var repository: TwseStockRepository

    @Before fun setup() {
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(
                when (request.path) {
                    "/v1/exchangeReport/STOCK_DAY_ALL" -> "[${trade()}]"
                    "/v1/exchangeReport/STOCK_DAY_AVG_ALL" -> "[${average()}]"
                    "/v1/exchangeReport/BWIBBU_ALL" -> "[${valuation()}]"
                    else -> throw AssertionError("Unexpected endpoint: ${request.path}")
                },
            )
        }
        repository = TwseStockRepository(TwseService(client, server.url("/v1/")), Dispatchers.IO)
    }

    @After fun cleanup() {
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test fun `fetches three datasets caches and forces refresh`() = runBlocking {
        val first = repository.load(false)
        assertEquals("2330", first.single().code)
        assertEquals(3, server.requestCount)
        assertSame(first, repository.load(false))
        assertEquals(3, server.requestCount)
        repository.load(true)
        assertEquals(6, server.requestCount)
    }

    @Test fun `concurrent readers share one complete snapshot`() = runBlocking {
        val left = async(Dispatchers.Default) { repository.load(false) }
        val right = async(Dispatchers.Default) { repository.load(false) }
        assertSame(left.await(), right.await())
        assertEquals(3, server.requestCount)
    }

    @Test fun `all three endpoints are in flight before any response is needed`() = runBlocking {
        val arrived = CountDownLatch(3)
        val original = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrived.countDown()
                if (!arrived.await(2, TimeUnit.SECONDS)) return MockResponse().setResponseCode(504)
                return original.dispatch(request)
            }
        }
        assertEquals(1, repository.load(false).size)
        assertEquals(0L, arrived.count)
    }

    @Test fun `http error fails refresh without replacing cache`() = runBlocking {
        val cached = repository.load(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503)
        }
        try {
            repository.load(true)
            fail("Expected HTTP failure")
        } catch (_: IOException) {
            assertSame(cached, repository.load(false))
        }
    }

    @Test fun `cancellation cancels the underlying HTTP call`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setHeadersDelay(1, TimeUnit.SECONDS).setBody("[]")
        }
        val request = async { TwseService(client, server.url("/v1/")).get("STOCK_DAY_ALL") }
        // Let the coroutine enqueue its call before blocking for the server request.
        kotlinx.coroutines.yield()
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val call = client.dispatcher.runningCalls().single()
        request.cancelAndJoin()
        assertTrue(call.isCanceled())
    }
}
