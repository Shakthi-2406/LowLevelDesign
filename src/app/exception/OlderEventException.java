package app.exception;

public class OlderEventException extends RuntimeException {
    public OlderEventException(String message) {
        super(message);
    }
}
