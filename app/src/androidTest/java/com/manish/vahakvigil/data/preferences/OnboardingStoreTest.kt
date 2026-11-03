package com.manish.vahakvigil.data.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingStoreTest {
    @Test fun completionSurvivesCreatingANewStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("onboarding_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(OnboardingStore(context).isComplete())

        OnboardingStore(context).markComplete()

        assertTrue(OnboardingStore(context).isComplete())
        context.getSharedPreferences("onboarding_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
