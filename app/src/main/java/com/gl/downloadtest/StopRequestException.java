package com.gl.downloadtest;

/**
 * Raised to indicate that the current request should be stopped immediately.
 * <p>
 * Note the message passed to this exception will be logged and therefore must be guaranteed
 * not to contain any PII, meaning it generally can't include any information about the request
 * URI, headers, or destination filename.
 */
public class StopRequestException extends Exception {
    private final int mFinalStatus;

    public StopRequestException(int finalStatus, String message) {
        super(message);
        mFinalStatus = finalStatus;
    }

    public StopRequestException(int finalStatus, Throwable t) {
        super(t);
        mFinalStatus = finalStatus;
    }

    public StopRequestException(int finalStatus, String message, Throwable t) {
        super(message, t);
        mFinalStatus = finalStatus;
    }

    public int getFinalStatus() {
        return mFinalStatus;
    }


}
