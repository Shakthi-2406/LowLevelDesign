package app.downstream;

import app.model.Event;

/** Fire-and-forget: failures are not retried. */
public interface DownstreamService {
    void sendPayload(Event event);
}
