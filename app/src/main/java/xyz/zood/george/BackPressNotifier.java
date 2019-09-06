package xyz.zood.george;

import androidx.annotation.Nullable;

public interface BackPressNotifier {
    void setBackPressInterceptor(@Nullable BackPressInterceptor interceptor);
}
