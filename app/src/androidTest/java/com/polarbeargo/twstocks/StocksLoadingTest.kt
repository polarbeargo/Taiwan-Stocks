package com.polarbeargo.twstocks

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.polarbeargo.twstocks.ui.StocksActivity
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

internal fun fixtureRepository(): FixtureRepository =
    (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FixtureApplication).repository

internal fun awaitStock(code: String, name: String? = null) {
    onView(withId(R.id.stocks)).perform(object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)
        override fun getDescription() = "Wait for bound stock $code / $name"
        override fun perform(uiController: UiController, view: View) {
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            do {
                uiController.loopMainThreadUntilIdle()
                val holder = (view as RecyclerView).findViewHolderForAdapterPosition(0)
                if (holder?.itemView?.findViewById<TextView>(R.id.code)?.text?.toString() == code &&
                    (name == null || holder.itemView.findViewById<TextView>(R.id.name).text.toString() == name)) return
                uiController.loopMainThreadForAtLeast(25)
            } while (android.os.SystemClock.uptimeMillis() < deadline)
            throw AssertionError("Stock $code / $name was not bound")
        }
    })
}

class StocksLoadingTest {
    private val repository get() = fixtureRepository()

    @Before fun setup() {
        repository.reset()
        repository.controlled = true
    }

    @After fun cleanup() = repository.reset()

    @Test fun initialLoadingFailureAndRetryReplaceErrorWithData() {
        ActivityScenario.launch(StocksActivity::class.java).use {
            val initial = repository.nextRequest()
            assertFalse(initial.forced)
            onView(withId(R.id.progress)).check(matches(isDisplayed()))
            onView(withId(R.id.status)).check(matches(withText(R.string.loading)))
            onView(withId(R.id.refresh)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            initial.response.completeExceptionally(IOException("Expected fixture failure"))
            onView(withId(R.id.retry)).check(matches(isDisplayed()))
            onView(withId(R.id.status)).check(matches(withText(R.string.load_error)))
            onView(withId(R.id.progress)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.retry)).perform(click())
            val retry = repository.nextRequest()
            assertTrue(retry.forced)
            onView(withId(R.id.progress)).check(matches(isDisplayed()))
            retry.response.complete(repository.stocks)
            awaitStock("2330")
            onView(withId(R.id.statusGroup)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.refresh)).check(matches(isEnabled()))
        }
    }

    @Test fun refreshChangesDataAndFailedRefreshRetainsDataUntilSnackbarRetry() {
        ActivityScenario.launch(StocksActivity::class.java).use {
            repository.nextRequest().response.complete(repository.stocks)
            awaitStock("2330")
            onView(withId(R.id.refresh)).perform(click())
            val refresh = repository.nextRequest()
            assertTrue(refresh.forced)
            onView(withId(R.id.refresh)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            awaitStock("2330")
            val updated = repository.stocks.map { it.copy(name = "Updated ${it.code}") }
            refresh.response.complete(updated)
            awaitStock("2330", "Updated 2330")
            onView(withId(R.id.refresh)).perform(click())
            val failure = repository.nextRequest()
            assertTrue(failure.forced)
            failure.response.completeExceptionally(IOException("Expected refresh failure"))
            onView(withText(R.string.refresh_error)).check(matches(isDisplayed()))
            awaitStock("2330", "Updated 2330")
            onView(withId(com.google.android.material.R.id.snackbar_action)).perform(click())
            val retry = repository.nextRequest()
            assertTrue(retry.forced)
            retry.response.complete(repository.stocks)
            awaitStock("2330", repository.stocks.last().name)
            onView(withId(R.id.refresh)).check(matches(isEnabled()))
        }
    }
}
