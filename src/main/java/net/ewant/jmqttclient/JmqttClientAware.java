package net.ewant.jmqttclient;

/**
 * Created by admin on 2019/5/8.
 */
public interface JmqttClientAware {
    void setClient(JmqttClient client);
}
