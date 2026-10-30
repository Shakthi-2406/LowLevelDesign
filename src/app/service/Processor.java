package app.service;

import app.downstream.DownstreamService;
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

import java.util.ArrayList;
import java.util.List;

public class Processor {
    static final long MIN_AMOUNT_PAISA = 100L;
    static final long MAX_AMOUNT_PAISA = 50_000_000L;
    private static final String CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final EventRepository eventRepository;
    private final List<DownstreamService> downstreamServices;

    public Processor(PaymentRepository paymentRepository, EventRepository eventRepository,
                     List<DownstreamService> downstreamServices) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.downstreamServices = new ArrayList<>(downstreamServices);
    }

    /** Returns the ack for the upstream; throws a domain exception to drop the event. */
    public synchronized String receiveSendEmit(Event event) {
        validateEvent(event);
        Payment stored = paymentRepository.findById(event.getPayment().getId());
        guardStateAndTime(stored, event);
        persistPayment(stored, event);
        eventRepository.save(event);
        broadcastDownstreamServices(event);
        return sendAcknowledgementToUpstream(event);
    }

    /** Fire-and-forget: a failing downstream is logged, not retried, and does not block the others or the ack. */
    private void broadcastDownstreamServices(Event event) {
        for (DownstreamService downstream : downstreamServices) {
            try {
                downstream.sendPayload(event);
            } catch (RuntimeException e) {
                System.err.println("downstream " + downstream.getClass().getSimpleName() + " failed for "
                        + event.getEventId() + ": " + e.getMessage());
            }
        }
    }

    private String sendAcknowledgementToUpstream(Event event) {
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
