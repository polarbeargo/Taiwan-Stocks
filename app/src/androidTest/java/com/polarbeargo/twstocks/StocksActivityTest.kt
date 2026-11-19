package com.polarbeargo.twstocks

import android.content.pm.ActivityInfo
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.polarbeargo.twstocks.ui.StocksActivity
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matcher

@RunWith(AndroidJUnit4::class)
class StocksActivityTest {
    @Before fun resetRepository() {
        fixtureRepository().reset()
    }

    @After fun resetTheme() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @Test fun sortDialogValuationAndRotation() {
        ActivityScenario.launch(StocksActivity::class.java).use { scenario ->
            assertFirstCode("2330")
            onView(withId(R.id.sort)).perform(click())
            onView(withId(R.id.descending)).inRoot(isDialog()).check(matches(isChecked()))
            onView(withId(R.id.ascending)).inRoot(isDialog()).perform(click())
            assertFirstCode("0050")
            onView(withId(R.id.stocks)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
            onView(withText(R.string.pe_ratio)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.peRatio)).inRoot(isDialog()).check(matches(withText("—")))
            onView(withText(R.string.close)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            assertFirstCode("0050")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            assertFirstCode("0050")
            onView(withId(R.id.sort)).perform(click())
            onView(withId(R.id.descending)).inRoot(isDialog()).perform(click())
            assertFirstCode("2330")
            onView(withId(R.id.stocks)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
            onView(withId(R.id.peRatio)).inRoot(isDialog()).check(matches(withText("20.25")))
            onView(withId(R.id.dividendYield)).inRoot(isDialog()).check(matches(withText("2.50")))
            onView(withId(R.id.pbRatio)).inRoot(isDialog()).check(matches(withText("5.10")))
        }
    }

    @Test fun darkModeAndPriceColors() {
        ActivityScenario.launch(StocksActivity::class.java).use { scenario ->
            assertFirstCode("2330")
            assertFirstColor(R.id.closing, R.color.price_down)
            assertFirstColor(R.id.change, R.color.price_down)
            scenario.onActivity { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
            assertFirstCode("2330")
            assertFirstColor(R.id.closing, R.color.price_down)
            onView(withId(R.id.sort)).perform(click())
            onView(withId(R.id.ascending)).inRoot(isDialog()).perform(click())
            assertFirstCode("0050")
            assertFirstColor(R.id.closing, R.color.price_up)
            assertFirstColor(R.id.change, R.color.price_up)
        }
    }

    private fun assertFirstCode(code: String) {
        onView(withId(R.id.stocks)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)
            override fun getDescription(): String = "Wait for first stock $code to be bound"
            override fun perform(uiController: UiController, view: View) {
                val recycler = view as RecyclerView
                val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                do {
                    uiController.loopMainThreadUntilIdle()
                    val first = recycler.findViewHolderForAdapterPosition(0)
                    if (first?.itemView?.findViewById<TextView>(R.id.code)?.text?.toString() == code) return
                    uiController.loopMainThreadForAtLeast(25)
                } while (android.os.SystemClock.uptimeMillis() < deadline)
                throw AssertionError("Timed out waiting for $code")
            }
        })
        onView(withId(R.id.stocks)).check { view, error ->
            if (error != null) throw error
            val recycler = view as RecyclerView
            val first = recycler.findViewHolderForAdapterPosition(0)
                ?: throw AssertionError("First stock is not bound")
            assertEquals(code, first.itemView.findViewById<TextView>(R.id.code).text.toString())
        }
    }

    private fun assertFirstColor(textId: Int, colorId: Int) {
        onView(withId(R.id.stocks)).check { view, error ->
            if (error != null) throw error
            val first = (view as RecyclerView).findViewHolderForAdapterPosition(0)
                ?: throw AssertionError("First stock is not bound")
            val text = first.itemView.findViewById<TextView>(textId)
            assertEquals(ContextCompat.getColor(text.context, colorId), text.currentTextColor)
        }
    }
}
