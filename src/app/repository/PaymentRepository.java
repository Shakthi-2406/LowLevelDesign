package app.repository;

import app.model.Payment;

import java.util.HashMap;
import java.util.Map;

public class PaymentRepository {
    private final Map<String, Payment> paymentsById = new HashMap<>();

    public void save(Payment payment) {
        paymentsById.put(payment.getId(), payment);
    }

    /** Returns null when no payment exists for the id. */
    public Payment findById(String paymentId) {
        return paymentsById.get(paymentId);
    }
}
