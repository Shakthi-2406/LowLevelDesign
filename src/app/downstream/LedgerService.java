package app.downstream;

import app.model.Event;

public class LedgerService implements DownstreamService {
    @Override
    public void sendPayload(Event event) {
        System.out.println("  [ledger] " + event.getEventId() + " " + event.getEventType()
                + " " + event.getPayment().getAmountPaisa() + " paisa for " + event.getPayment().getId());
    }
}
