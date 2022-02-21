package shashok.concurrent.exception;

public class EntityLockerException extends RuntimeException {

    public EntityLockerException() {}

    public EntityLockerException(String message) {
        super(message);
    }
}
