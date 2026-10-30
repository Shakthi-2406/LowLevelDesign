package app.model;

public enum PaymentState {
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED;

    public boolean canTransitionTo(PaymentState next) {
        switch (this) {
            case AUTHORIZED: return next == CAPTURED || next == FAILED || next == REFUNDED;
            case CAPTURED:   return next == FAILED || next == REFUNDED;
            case FAILED:     return next == REFUNDED;
            default:         return false;
        }
    }
}
