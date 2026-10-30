package app.service;

import app.exception.DuplicateEventException;
import app.exception.InvalidAmountRangeException;
import app.exception.InvalidEventException;
import app.exception.InvalidPaymentStateException;
import app.exception.OlderEventException;
import app.model.Event;
import app.model.Payment;
import app.model.PaymentState;
import app.repository.EventRepository;
import app.repository.PaymentRepository;

public class Processor {
    static final long MIN_AMOUNT_PAISA = 10000L;
    static final long MAX_AMOUNT_PAISA = 50_000_00000L;
    private static final String CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final EventRepository eventRepository;

    public Processor(PaymentRepository paymentRepository, EventRepository eventRepository) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
    }

    /** Returns the ack for the upstream; throws a domain exception to drop the event. */
    public synchronized String receiveSendEmit(Event event) {
        validateEvent(event);
        Payment stored = paymentRepository.findById(event.getPayment().getId());
        guardStateAndTime(stored, event);
        persistPayment(stored, event);
        eventRepository.save(event);
        return "ACK:" + event.getEventId();
    }

    private void guardStateAndTime(Payment stored, Event event) {
        PaymentState next = event.getEventType();
        if (stored == null) {
            return;
        }
        if (!stored.getCurrentState().canTransitionTo(next)) {
            throw new InvalidPaymentStateException("payment " + stored.getId() + " cannot move from "
                    + stored.getCurrentState() + " to " + next);
        }
        if (event.getTime().isBefore(stored.getLastUpdatedAt())) {
            throw new OlderEventException("event " + event.getEventId() + " at " + event.getTime()
                    + " is older than payment last update " + stored.getLastUpdatedAt());
        }
    }

    private void persistPayment(Payment stored, Event event) {
        if (stored == null) {
            Payment incoming = event.getPayment();
            paymentRepository.save(new Payment(incoming.getId(), incoming.getAmountPaisa(),
                    incoming.getCurrency(), event.getTime(), event.getEventType()));
            return;
        }
        stored.moveTo(event.getEventType(), event.getTime());
    }

    private void validateEvent(Event event) {
        if (event == null) {
            throw new InvalidEventException("event is required");
        }
        requireText(event.getEventId(), "event_id");
        requireText(event.getSignature(), "signature");
        if (event.getEventType() == null) {
            throw new InvalidEventException("event_type is required");
        }
        if (event.getTime() == null) {
            throw new InvalidEventException("created_at is required");
        }
        Payment payment = event.getPayment();
        if (payment == null) {
            throw new InvalidEventException("payment is required");
        }
        requireText(payment.getId(), "payment_id");
        if (!CURRENCY.equals(payment.getCurrency())) {
            throw new InvalidEventException("currency must be " + CURRENCY + ", got " + payment.getCurrency());
        }
        long amount = payment.getAmountPaisa();
        if (amount < MIN_AMOUNT_PAISA || amount > MAX_AMOUNT_PAISA) {
            throw new InvalidAmountRangeException("amount " + amount + " paisa outside ["
                    + MIN_AMOUNT_PAISA + ", " + MAX_AMOUNT_PAISA + "]");
        }
        if (eventRepository.exists(event.getEventId())) {
            throw new DuplicateEventException("duplicate event_id: " + event.getEventId());
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new InvalidEventException(field + " is required");
        }
    }
}
