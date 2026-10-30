package app;

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
import app.service.Processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Tests {
    private static final Instant T0 = Instant.parse("2026-10-30T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-30T10:05:00Z");
    private static int nextPaymentId = 1;

    public static void main(String[] args) {
        validatesFields();
        validatesAmountRange();
        rejectsDuplicates();
        guardsStateTransitions();
        guardsEventTime();
        broadcastsToDownstreams();
        Check.summary();
    }

    private static Processor newProcessor() {
        return new Processor(new PaymentRepository(), new EventRepository(), new ArrayList<>());
    }

    private static class RecordingDownstream implements DownstreamService {
        final List<String> received = new ArrayList<>();

        @Override
        public void sendPayload(Event event) {
            received.add(event.getEventId());
        }
    }

    private static class FailingDownstream implements DownstreamService {
        @Override
        public void sendPayload(Event event) {
            throw new IllegalStateException("downstream unavailable");
        }
    }

    private static void broadcastsToDownstreams() {
        RecordingDownstream notification = new RecordingDownstream();
        RecordingDownstream ledger = new RecordingDownstream();
        List<DownstreamService> downstreams = new ArrayList<>();
        downstreams.add(notification);
        downstreams.add(ledger);
        Processor p = new Processor(new PaymentRepository(), new EventRepository(), downstreams);
        Payment pay = payment(10000);

        Check.eq(p.receiveSendEmit(event("evt_b1", pay, PaymentState.AUTHORIZED, T0)), "ACK:evt_b1", "accepted event acked");
        Check.eq(notification.received.toString(), "[evt_b1]", "notification received accepted event");
        Check.eq(ledger.received.toString(), "[evt_b1]", "ledger received accepted event");

        Check.throwsEx(() -> p.receiveSendEmit(event("evt_b1", pay, PaymentState.AUTHORIZED, T0)),
                DuplicateEventException.class, "duplicate dropped");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_b2", pay, PaymentState.AUTHORIZED, T1)),
                InvalidPaymentStateException.class, "invalid transition dropped");
        Check.eq(notification.received.size(), 1, "dropped events are not broadcast");

        RecordingDownstream afterFailure = new RecordingDownstream();
        List<DownstreamService> withFailure = new ArrayList<>();
        withFailure.add(new FailingDownstream());
        withFailure.add(afterFailure);
        Processor q = new Processor(new PaymentRepository(), new EventRepository(), withFailure);
        Check.eq(q.receiveSendEmit(event("evt_b3", payment(10000), PaymentState.AUTHORIZED, T0)), "ACK:evt_b3",
                "failing downstream does not block the ack");
        Check.eq(afterFailure.received.toString(), "[evt_b3]", "failing downstream does not block the next one");
    }

    /** Each call is a distinct payment, so independent AUTHORIZED events don't collide. */
    private static Payment payment(long amountPaisa) {
        return new Payment("pay_" + nextPaymentId++, amountPaisa, "INR", T0, PaymentState.AUTHORIZED);
    }

    private static Event event(String eventId, Payment payment, String signature) {
        return new Event(eventId, payment, PaymentState.AUTHORIZED, T0, signature);
    }

    private static Event event(String eventId, Payment payment, PaymentState type, Instant time) {
        return new Event(eventId, payment, type, time, "sig");
    }

    private static void guardsStateTransitions() {
        PaymentRepository payments = new PaymentRepository();
        Processor p = new Processor(payments, new EventRepository(), new ArrayList<>());
        Payment pay = payment(10000);
        p.receiveSendEmit(event("evt_a", pay, PaymentState.AUTHORIZED, T0));
        Check.eq(p.receiveSendEmit(event("evt_c", pay, PaymentState.CAPTURED, T1)), "ACK:evt_c", "authorized -> captured accepted");

        Payment stored = payments.findById(pay.getId());
        Check.eq(stored.getCurrentState(), PaymentState.CAPTURED, "stored payment moved to CAPTURED");
        Check.eq(stored.getLastUpdatedAt(), T1, "lastUpdatedAt is the event time");
        Check.eq(stored.getStageTimestamps().keySet().toString(), "[AUTHORIZED, CAPTURED]", "stage times kept in order");

        Check.throwsEx(() -> p.receiveSendEmit(event("evt_back", pay, PaymentState.AUTHORIZED, T1)),
                InvalidPaymentStateException.class, "captured -> authorized rejected");
        Check.eq(stored.getCurrentState(), PaymentState.CAPTURED, "rejected transition leaves state unchanged");

        Check.eq(p.receiveSendEmit(event("evt_f", pay, PaymentState.FAILED, T1)), "ACK:evt_f", "captured -> failed accepted");
        Check.eq(p.receiveSendEmit(event("evt_r", pay, PaymentState.REFUNDED, T1)), "ACK:evt_r", "failed -> refunded accepted");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_after", pay, PaymentState.CAPTURED, T1)),
                InvalidPaymentStateException.class, "refunded is terminal");

        Payment late = payment(10000);
        Check.eq(p.receiveSendEmit(event("evt_late_c", late, PaymentState.CAPTURED, T1)), "ACK:evt_late_c",
                "unknown payment can start at any state (events may arrive out of order)");
        Check.eq(payments.findById(late.getId()).getCurrentState(), PaymentState.CAPTURED, "payment created in first event's state");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_late_a", late, PaymentState.AUTHORIZED, T0)),
                InvalidPaymentStateException.class, "earlier AUTHORIZED arriving after CAPTURED is an invalid transition");
    }

    private static void guardsEventTime() {
        PaymentRepository payments = new PaymentRepository();
        Processor p = new Processor(payments, new EventRepository(), new ArrayList<>());
        Payment pay = payment(10000);
        p.receiveSendEmit(event("evt_a", pay, PaymentState.AUTHORIZED, T1));

        Check.throwsEx(() -> p.receiveSendEmit(event("evt_old", pay, PaymentState.CAPTURED, T0)),
                OlderEventException.class, "event older than last update rejected");
        Check.eq(payments.findById(pay.getId()).getCurrentState(), PaymentState.AUTHORIZED, "older event leaves state unchanged");
        Check.eq(p.receiveSendEmit(event("evt_old", pay, PaymentState.CAPTURED, T1)), "ACK:evt_old",
                "same-instant event accepted, and dropped event id was not recorded");
    }

    private static void validatesFields() {
        Processor p = newProcessor();
        Check.eq(p.receiveSendEmit(event("evt_1", payment(10000), "sig")), "ACK:evt_1", "valid event acked");
        Check.throwsEx(() -> p.receiveSendEmit(null), InvalidEventException.class, "null event rejected");
        Check.throwsEx(() -> p.receiveSendEmit(event(null, payment(10000), "sig")), InvalidEventException.class, "null event_id rejected");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_2", null, "sig")), InvalidEventException.class, "null payment rejected");
        Check.throwsEx(() -> p.receiveSendEmit(new Event("evt_3", payment(10000), null, T0, "sig")), InvalidEventException.class, "null event_type rejected");
        Check.throwsEx(() -> p.receiveSendEmit(new Event("evt_4", payment(10000), PaymentState.AUTHORIZED, null, "sig")), InvalidEventException.class, "null created_at rejected");
        Check.eq(p.receiveSendEmit(event("evt_5", payment(10000), "   ")), "ACK:evt_5", "whitespace-only signature accepted (only empty is rejected)");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_5b", payment(10000), "")), InvalidEventException.class, "empty signature rejected");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_6", new Payment("pay_1", 10000, "USD", T0, PaymentState.AUTHORIZED), "sig")),
                InvalidEventException.class, "non-INR currency rejected");
    }

    private static void validatesAmountRange() {
        Processor p = newProcessor();
        Check.eq(p.receiveSendEmit(event("evt_min", payment(100), "sig")), "ACK:evt_min", "1 rupee (min) accepted");
        Check.eq(p.receiveSendEmit(event("evt_max", payment(50_000_000), "sig")), "ACK:evt_max", "5 lakh (max) accepted");
        Check.eq(p.receiveSendEmit(event("evt_paisa", payment(10050), "sig")), "ACK:evt_paisa", "amount with paisa accepted");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_low", payment(99), "sig")), InvalidAmountRangeException.class, "below 1 rupee rejected");
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_high", payment(50_000_001), "sig")), InvalidAmountRangeException.class, "above 5 lakh rejected");
    }

    private static void rejectsDuplicates() {
        Processor p = newProcessor();
        p.receiveSendEmit(event("evt_dup", payment(10000), "sig"));
        Check.throwsEx(() -> p.receiveSendEmit(event("evt_dup", payment(10000), "sig")), DuplicateEventException.class, "duplicate event_id rejected");

        Check.throwsEx(() -> p.receiveSendEmit(event("evt_retry", payment(99), "sig")), InvalidAmountRangeException.class, "invalid first attempt rejected");
        Check.eq(p.receiveSendEmit(event("evt_retry", payment(10000), "sig")), "ACK:evt_retry", "rejected event is not recorded, so a corrected retry is accepted");
    }
}
