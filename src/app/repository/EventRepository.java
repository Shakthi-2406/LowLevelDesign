package app.repository;

import app.model.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventRepository {
    private final Map<String, Event> eventsById = new HashMap<>();

    public void save(Event event) {
        throw new UnsupportedOperationException("TODO");
    }

    public boolean exists(String eventId) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<Event> findByPaymentId(String paymentId) {
        throw new UnsupportedOperationException("TODO");
    }
}
