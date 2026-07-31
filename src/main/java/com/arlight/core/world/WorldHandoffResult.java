package com.arlight.core.world;

import java.util.UUID;

public record WorldHandoffResult(boolean success, UUID transactionId,
                                 WorldTransactionStage stage, String message) {
}
