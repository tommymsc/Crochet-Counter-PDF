package com.lauraapps.crochetcounter;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Button;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.*;
import com.google.android.ump.*;

final class AdsController {
    private final Activity activity;
    private final ConsentInformation consent;
    private AdView banner;
    private InterstitialAd interstitial;
    private LinearLayout host;
    private Button privacy;
    private boolean started, ready, destroyed, loading, showing;
    private int transitions;
    private long lastShown = SystemClock.elapsedRealtime();
    private static final String BANNER = BuildConfig.LIVE_ADS ? "ca-app-pub-2506564165861733/2077507499" : "ca-app-pub-3940256099942544/6300978111";
    private static final String INTERSTITIAL = BuildConfig.LIVE_ADS ? "ca-app-pub-2506564165861733/5222866382" : "ca-app-pub-3940256099942544/1033173712";

    AdsController(Activity activity) {
        this.activity = activity;
        consent = UserMessagingPlatform.getConsentInformation(activity);
    }
    void start() {
        consent.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(),
            () -> {
                if (destroyed) return;
                updatePrivacy();
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                    if (!destroyed) { updatePrivacy(); activate(); }
                });
            }, error -> { if (!destroyed) { updatePrivacy(); activate(); } });
    }
    private void activate() {
        if (destroyed || started || !consent.canRequestAds()) return;
        started = true;
        new Thread(() -> MobileAds.initialize(activity.getApplicationContext(), status ->
            activity.runOnUiThread(() -> {
                if (destroyed) return;
                ready = true; attachBanner(); loadInterstitial();
            }))).start();
    }
    void bind(LinearLayout host, Button privacy) {
        if (banner != null) { banner.destroy(); banner = null; }
        this.host = host;
        this.privacy = privacy;
        privacy.setOnClickListener(v -> UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (destroyed) return;
            updatePrivacy();
            if (!consent.canRequestAds()) {
                interstitial = null;
                if (banner != null) { host.removeView(banner); banner.destroy(); banner = null; }
            } else { activate(); attachBanner(); loadInterstitial(); }
        }));
        updatePrivacy(); attachBanner();
    }
    private void updatePrivacy() {
        if (privacy != null) privacy.setVisibility(consent.getPrivacyOptionsRequirementStatus()
            == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED ? View.VISIBLE : View.GONE);
    }
    private void attachBanner() {
        if (!ready || destroyed || host == null || banner != null || !consent.canRequestAds()) return;
        if (activity.getResources().getDisplayMetrics().widthPixels / activity.getResources().getDisplayMetrics().density < 320) return;
        banner = new AdView(activity);
        banner.setAdSize(AdSize.BANNER);
        banner.setAdUnitId(BANNER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        host.addView(banner, params);
        banner.loadAd(new AdRequest.Builder().build());
    }
    private void loadInterstitial() {
        if (!ready || destroyed || loading || interstitial != null || !consent.canRequestAds()) return;
        loading = true;
        InterstitialAd.load(activity, INTERSTITIAL, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) { loading = false; if (!destroyed) interstitial = ad; }
            @Override public void onAdFailedToLoad(LoadAdError error) { loading = false; }
        });
    }
    void projectTransition() {
        transitions++;
        if (destroyed || showing || !consent.canRequestAds()) return;
        if (interstitial == null) { loadInterstitial(); return; }
        if (transitions < 3 || SystemClock.elapsedRealtime() - lastShown < 180000L || !activity.hasWindowFocus()) return;
        InterstitialAd ad = interstitial; interstitial = null;
        showing = true; transitions = 0; lastShown = SystemClock.elapsedRealtime();
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { showing = false; loadInterstitial(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { showing = false; loadInterstitial(); }
        });
        ad.show(activity);
    }
    void pause() { if (banner != null) banner.pause(); }
    void resume() { if (banner != null) banner.resume(); }
    void destroy() { destroyed = true; if (banner != null) banner.destroy(); banner = null; interstitial = null; host = null; privacy = null; }
}
