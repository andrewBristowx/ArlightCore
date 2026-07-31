package com.arlight.core.world;

public enum WorldTransactionStage {
    PREPARING,
    VALIDATED,
    BACKED_UP,
    SWITCHED,
    COMPLETE,
    ROLLED_BACK,
    FAILED;

    public boolean terminal() {
        return this == COMPLETE || this == ROLLED_BACK || this == FAILED;
    }
}
