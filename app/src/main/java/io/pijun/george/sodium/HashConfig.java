package io.pijun.george.sodium;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class HashConfig {

    // These values were all pulled from libsodium
    private final static long ARGON2I13_OPSLIMIT_INTERACTIVE = 4;
    private final static long ARGON2I13_OPSLIMIT_MODERATE = 6;
    private final static long ARGON2I13_OPSLIMIT_SENSITIVE = 8;
    private final static long ARGON2I13_MEMLIMIT_INTERACTIVE = 33554432;    // 32 MB
    private final static long ARGON2I13_MEMLIMIT_MODERATE = 134217728;      // 128 MB
    private final static long ARGON2I13_MEMLIMIT_SENSITIVE = 536870912;     // 512 MB

    private final static long ARGON2ID13_OPSLIMIT_INTERACTIVE = 2;
    private final static long ARGON2ID13_OPSLIMIT_MODERATE = 3;
    private final static long ARGON2ID13_OPSLIMIT_SENSITIVE = 4;
    private final static long ARGON2ID13_MEMLIMIT_INTERACTIVE = 67108864;   // 64 MB
    private final static long ARGON2ID13_MEMLIMIT_MODERATE = 268435456;     // 256 MB
    private final static long ARGON2ID13_MEMLIMIT_SENSITIVE = 1073741824;   // 1024 MB

    public enum MemSecurity {
        Interactive,
        Moderate,
        Sensitive,
        Custom
    }

    public enum OpsSecurity {
        Interactive,
        Moderate,
        Sensitive,
        Custom
    }

    public enum Algorithm {
        Argon2i13("argon2i13", 1, 16),
        Argon2id13("argon2id13", 2, 16);

        public final String name;
        public final int saltLength;
        public final int sodiumId;

        Algorithm(String name, int sodiumId, int saltLength) {
            this.name = name;
            this.sodiumId = sodiumId;
            this.saltLength = saltLength;
        }

        @Nullable @AnyThread
        public static Algorithm get(String name) {
            for (Algorithm a : values()) {
                if (a.name.equals(name)) {
                    return a;
                }
            }

            return null;
        }
    }

    public final Algorithm alg;
    private long opsLimit;
    private long memLimit;

    private HashConfig(@NonNull Algorithm alg, long opsLimit, long memLimit) {
        this.alg = alg;
        this.opsLimit = opsLimit;
        this.memLimit = memLimit;
    }

    public HashConfig(@NonNull Algorithm alg, OpsSecurity opsSecurity, MemSecurity memSecurity) {
        this.alg = alg;
        setOpsSecurity(opsSecurity);
        setMemSecurity(memSecurity);
    }

    @Nullable
    public static HashConfig create(@NonNull String algName, long opsLimit, long memLimit) {
        Algorithm alg = Algorithm.get(algName);
        if (alg == null) {
            return null;
        }

        return new HashConfig(alg, opsLimit, memLimit);
    }

    public long getMemLimit() {
        return memLimit;
    }

    @NonNull @AnyThread
    public MemSecurity getMemSecurity() {
        if (alg == Algorithm.Argon2i13) {
            if (memLimit == ARGON2I13_MEMLIMIT_INTERACTIVE) {
                return MemSecurity.Interactive;
            } else if (memLimit == ARGON2I13_MEMLIMIT_MODERATE) {
                return MemSecurity.Moderate;
            } else if (memLimit == ARGON2I13_MEMLIMIT_SENSITIVE) {
                return MemSecurity.Sensitive;
            } else {
                return MemSecurity.Custom;
            }
        }

        if (memLimit == ARGON2ID13_MEMLIMIT_INTERACTIVE) {
            return MemSecurity.Interactive;
        } else if (memLimit == ARGON2ID13_MEMLIMIT_MODERATE) {
            return MemSecurity.Moderate;
        } else if (memLimit == ARGON2ID13_MEMLIMIT_SENSITIVE) {
            return MemSecurity.Sensitive;
        } else {
            return MemSecurity.Custom;
        }
    }

    public long getOpsLimit() {
        return opsLimit;
    }

    @NonNull @AnyThread
    public OpsSecurity getOpsSecurity() {
        if (alg == Algorithm.Argon2i13) {
            if (opsLimit == ARGON2I13_OPSLIMIT_INTERACTIVE) {
                return OpsSecurity.Interactive;
            } else if (opsLimit == ARGON2I13_OPSLIMIT_MODERATE) {
                return OpsSecurity.Moderate;
            } else if (opsLimit == ARGON2I13_OPSLIMIT_SENSITIVE) {
                return OpsSecurity.Sensitive;
            } else {
                return OpsSecurity.Custom;
            }
        }

        if (opsLimit == ARGON2ID13_OPSLIMIT_INTERACTIVE) {
            return OpsSecurity.Interactive;
        } else if (opsLimit == ARGON2ID13_OPSLIMIT_MODERATE) {
            return OpsSecurity.Moderate;
        } else if (opsLimit == ARGON2ID13_OPSLIMIT_SENSITIVE) {
            return OpsSecurity.Sensitive;
        } else {
            return OpsSecurity.Custom;
        }
    }

    private void setMemSecurity(MemSecurity sec) {
        if (alg == Algorithm.Argon2i13) {
            switch (sec) {
                case Interactive:
                    memLimit = ARGON2I13_MEMLIMIT_INTERACTIVE;
                    break;
                case Moderate:
                    memLimit = ARGON2I13_MEMLIMIT_MODERATE;
                    break;
                case Sensitive:
                    memLimit = ARGON2I13_MEMLIMIT_SENSITIVE;
                    break;
                case Custom:
                    throw new RuntimeException("'Custom' is not a valid option. Must use one of 'interactive', 'moderate' or 'sensitive'");
            }
        } else {
            switch (sec) {
                case Interactive:
                    memLimit = ARGON2ID13_MEMLIMIT_INTERACTIVE;
                    break;
                case Moderate:
                    memLimit = ARGON2ID13_MEMLIMIT_MODERATE;
                    break;
                case Sensitive:
                    memLimit = ARGON2ID13_MEMLIMIT_SENSITIVE;
                    break;
                case Custom:
                    throw new RuntimeException("'Custom' is not a valid option. Must use one of 'interactive', 'moderate' or 'sensitive'");
            }
        }
    }

    private void setOpsSecurity(OpsSecurity sec) {
        if (alg == Algorithm.Argon2i13) {
            switch (sec) {
                case Interactive:
                    opsLimit = ARGON2I13_OPSLIMIT_INTERACTIVE;
                    break;
                case Moderate:
                    opsLimit = ARGON2I13_OPSLIMIT_MODERATE;
                    break;
                case Sensitive:
                    opsLimit = ARGON2I13_OPSLIMIT_SENSITIVE;
                    break;
                case Custom:
                    throw new RuntimeException("'Custom' is not a valid option. Must use one of 'interactive', 'moderate' or 'sensitive'");
            }
        } else {
            switch (sec) {
                case Interactive:
                    opsLimit = ARGON2ID13_OPSLIMIT_INTERACTIVE;
                    break;
                case Moderate:
                    opsLimit = ARGON2ID13_OPSLIMIT_MODERATE;
                    break;
                case Sensitive:
                    opsLimit = ARGON2ID13_OPSLIMIT_SENSITIVE;
                    break;
                case Custom:
                    throw new RuntimeException("'Custom' is not a valid option. Must use one of 'interactive', 'moderate' or 'sensitive'");
            }
        }
    }

    @Override
    public String toString() {
        return "HashConfig{" +
                "alg=" + alg +
                ", opsLimit=" + opsLimit +
                ", memLimit=" + memLimit +
                '}';
    }

}
