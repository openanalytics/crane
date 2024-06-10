package eu.openanalytics.rdepot.crane.test.helpers;

public class TestHelperException extends RuntimeException {
    public TestHelperException(String message) {
        super(message);
    }

    public TestHelperException(String message, Throwable t) {
        super(message, t);
    }
}
