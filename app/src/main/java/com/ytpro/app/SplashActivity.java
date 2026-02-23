package com.ytpro.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0a0a0a);
        getWindow().setNavigationBarColor(0xFF0a0a0a);
        setContentView(R.layout.activity_splash);

        View logoWrap = findViewById(R.id.logoWrap);
        TextView tagline = findViewById(R.id.tagline);

        // Başlangıç: görünmez + hafif küçük
        logoWrap.setAlpha(0f);
        logoWrap.setScaleX(0.85f);
        logoWrap.setScaleY(0.85f);

        // Logo: fade in + scale up — sadece ViewPropertyAnimator, çakışma yok
        logoWrap.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setStartDelay(150)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();

        // Tagline: daha geç, sade fade
        tagline.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(800)
            .start();

        // 1.8 sn sonra geç — gereksiz bekleme yok
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 1800);
    }
}
