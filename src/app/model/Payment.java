package app.model;

import java.time.Instant;
import java.util.LinkedHashMap;

public class Payment {
    private final String id;
    private final long amountPaisa;
    private final String currency;
    private final Instant createdAt;
    private Instant lastUpdatedAt;
    private PaymentState currentState;
    private final LinkedHashMap<PaymentState, Instant> stageTimestamps = new LinkedHashMap<>();

    public Payment(String id, long amountPaisa, String currency, Instant createdAt, PaymentState initialState) {
        this.id = id;
        this.amountPaisa = amountPaisa;
        this.currency = currency;
        this.createdAt = createdAt;
        this.lastUpdatedAt = createdAt;
        this.currentState = initialState;
        this.stageTimestamps.put(initialState, createdAt);
    }

    public String getId() { return id; }
    public long getAmountPaisa() { return amountPaisa; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public PaymentState getCurrentState() { return currentState; }
    public LinkedHashMap<PaymentState, Instant> getStageTimestamps() { return new LinkedHashMap<>(stageTimestamps); }

    /** Records the move only; transition and ordering rules are enforced by the caller. */
    public void moveTo(PaymentState next, Instant at) {
        this.currentState = next;
        this.lastUpdatedAt = at;
        this.stageTimestamps.put(next, at);
    }
}
