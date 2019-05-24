package net.ewant.jmqttclient;

/**
 * Created by admin on 2019/5/16.
 */
public class JmqttException extends Exception {
    public JmqttException(String message) {
        super(message);
    }
    public JmqttException(Throwable cause) {
        super(cause);
    }
    public JmqttException(String message, Throwable cause) {
        super(message, cause);
    }
}
