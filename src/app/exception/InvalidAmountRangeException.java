package app.exception;

public class InvalidAmountRangeException extends RuntimeException {
    public InvalidAmountRangeException(String message) {
        super(message);
    }
}
