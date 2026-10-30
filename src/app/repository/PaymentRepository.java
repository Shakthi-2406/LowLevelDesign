package app.repository;

import app.model.Payment;

import java.util.HashMap;
import java.util.Map;

public class PaymentRepository {
    private final Map<String, Payment> paymentsById = new HashMap<>();

    public void save(Payment payment) {
        throw new UnsupportedOperationException("TODO");
    }

    public Payment findById(String paymentId) {
        throw new UnsupportedOperationException("TODO");
    }
}
