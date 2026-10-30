package app;

import app.model.Event;
import app.model.Payment;
import app.model.PaymentState;
import app.repository.EventRepository;
import app.repository.PaymentRepository;
import app.service.Processor;

import java.time.Instant;

/** Plays the upstream (RazorPay) side: emits events, including retries and bad ones, and prints each outcome. */
public class Main {
    public static void main(String[] args) {
        PaymentRepository payments = new PaymentRepository();
        Processor processor = new Processor(payments, new EventRepository());

        Payment pay = new Payment("pay_456", 10000L, "INR", at("10:00"), PaymentState.AUTHORIZED);
        Payment tooSmall = new Payment("pay_789", 99L, "INR", at("10:00"), PaymentState.AUTHORIZED);

        emit(processor, new Event("evt_1", pay, PaymentState.AUTHORIZED, at("10:00"), "sig"));
        emit(processor, new Event("evt_1", pay, PaymentState.AUTHORIZED, at("10:00"), "sig"));
        emit(processor, new Event("evt_2", pay, PaymentState.CAPTURED, at("10:05"), "sig"));
        emit(processor, new Event("evt_3", pay, PaymentState.AUTHORIZED, at("10:06"), "sig"));
        emit(processor, new Event("evt_4", pay, PaymentState.REFUNDED, at("10:01"), "sig"));
        emit(processor, new Event("evt_5", tooSmall, PaymentState.AUTHORIZED, at("10:00"), "sig"));
        emit(processor, new Event("evt_6", pay, PaymentState.REFUNDED, at("10:10"), ""));
        emit(processor, new Event("evt_7", pay, PaymentState.REFUNDED, at("10:10"), "sig"));

        Payment stored = payments.findById("pay_456");
        System.out.println("pay_456 final state: " + stored.getCurrentState()
                + ", stages: " + stored.getStageTimestamps());
    }

    private static void emit(Processor processor, Event event) {
        String label = event.getEventId() + " " + event.getEventType() + " -> ";
        try {
            System.out.println(label + processor.receiveSendEmit(event));
        } catch (RuntimeException e) {
            System.out.println(label + "DROPPED " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Instant at(String hhmm) {
        return Instant.parse("2026-10-30T" + hhmm + ":00Z");
    }
}
