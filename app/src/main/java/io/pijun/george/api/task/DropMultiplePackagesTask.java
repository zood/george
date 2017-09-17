package io.pijun.george.api.task;

import java.util.Map;

import io.pijun.george.crypto.EncryptedData;

public class DropMultiplePackagesTask extends OscarTask {
    public static final transient String NAME = "drop_multiple_packages";
    public Map<String, EncryptedData> packages;

    public DropMultiplePackagesTask(String accessToken) {
        super(NAME, accessToken);
    }
}
