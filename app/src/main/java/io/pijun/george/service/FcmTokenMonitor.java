package io.pijun.george.service;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import io.pijun.george.L;

public class FcmTokenMonitor extends FirebaseInstanceIdService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        String token = FirebaseInstanceId.getInstance().getToken();
        L.i("FirebaseToken refreshed: " + token);

        startService(FcmTokenRegistrar.newIntent(this));
    }
}
