package net.ewant.jmqttclient;

/**
 * Created by admin on 2019/5/9.
 */
public interface JmqttSessionNotify {
    void onSessionOpen(String clientId, String clientIp, String username, String password);
    void onSessionClose(String clientId);
}
