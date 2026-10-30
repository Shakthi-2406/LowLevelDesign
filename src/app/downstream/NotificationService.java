package app.downstream;

import app.model.Event;

public class NotificationService implements DownstreamService {
    @Override
    public void sendPayload(Event event) {
        System.out.println("  [notification] " + event.getEventId() + " " + event.getEventType()
                + " for " + event.getPayment().getId());
    }
}
