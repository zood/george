package io.pijun.george.service;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import io.pijun.george.L;
import io.pijun.george.Prefs;

public class FcmTokenMonitor extends FirebaseInstanceIdService {

    @Override
    public void onTokenRefresh() {
        L.i("FcmTokenMonitor.onTokenRefresh");
        super.onTokenRefresh();
        String token = FirebaseInstanceId.getInstance().getToken();
        L.i("FirebaseToken refreshed: " + token);
        // clear it out so the registrar will recognize it as new
        Prefs.get(this).setFcmToken(null);

        startService(FcmTokenRegistrar.newIntent(this));
    }
}
