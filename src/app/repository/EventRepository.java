package app.repository;

import app.model.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventRepository {
    private final Map<String, Event> eventsById = new HashMap<>();

    public void save(Event event) {
        eventsById.put(event.getEventId(), event);
    }

    public boolean exists(String eventId) {
        return eventsById.containsKey(eventId);
    }

    public List<Event> findByPaymentId(String paymentId) {
        throw new UnsupportedOperationException("TODO");
    }
}
