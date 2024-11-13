package xyz.zood.george.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Event<T> {

    private boolean handled;
    private final T value;

    public Event(@NonNull T value) {
        this.value = value;
    }

    @Nullable
    public T getEventIfNotHandled() {
        if (handled) {
            return null;
        }

        handled = true;
        return value;
    }

}
