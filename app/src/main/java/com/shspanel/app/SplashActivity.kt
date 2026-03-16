package com.shspanel.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.view.animation.AnimationSet
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.ivLogo)
        val title = findViewById<TextView>(R.id.tvTitle)
        val subtitle = findViewById<TextView>(R.id.tvSubtitle)

        val scaleAnim = ScaleAnimation(
            0.5f, 1.0f, 0.5f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 800 }

        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 900 }

        val logoSet = AnimationSet(true).apply {
            addAnimation(scaleAnim)
            addAnimation(fadeIn)
            fillAfter = true
        }
        logo.startAnimation(logoSet)

        val titleFade = AlphaAnimation(0f, 1f).apply {
            duration = 700
            startOffset = 500
            fillAfter = true
        }
        title.startAnimation(titleFade)

        val subFade = AlphaAnimation(0f, 1f).apply {
            duration = 700
            startOffset = 700
            fillAfter = true
        }
        subtitle.startAnimation(subFade)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2200)
    }
}
