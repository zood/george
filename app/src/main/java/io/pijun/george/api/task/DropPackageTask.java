package io.pijun.george.api.task;

import androidx.annotation.NonNull;

import io.pijun.george.crypto.EncryptedData;

public class DropPackageTask extends OscarTask {
    public static final String NAME = "drop_package";
    public String hexBoxId;
    public EncryptedData pkg;

    public DropPackageTask(String accessToken) {
        super(NAME, accessToken);
    }

    @NonNull
    @Override
    public String toString() {
        return "DropPackageTask{" +
                "hexBoxId='" + hexBoxId + '\'' +
                ", pkg=" + pkg +
                '}';
    }
}
