package app.model;

import java.time.Instant;

public class Event {
    private final String eventId;
    private final Payment payment;
    private final PaymentState eventType;
    private final Instant time;
    private final String signature;

    public Event(String eventId, Payment payment, PaymentState eventType, Instant time, String signature) {
        this.eventId = eventId;
        this.payment = payment;
        this.eventType = eventType;
        this.time = time;
        this.signature = signature;
    }

    public String getEventId() { return eventId; }
    public Payment getPayment() { return payment; }
    public PaymentState getEventType() { return eventType; }
    public Instant getTime() { return time; }
    public String getSignature() { return signature; }
}
