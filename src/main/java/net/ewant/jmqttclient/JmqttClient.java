package net.ewant.jmqttclient;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;

/**
 * Created by admin on 2018/12/7.
 */
public class JmqttClient {

    Logger logger = LoggerFactory.getLogger(JmqttClient.class);

    static final String TOPIC_START_CHART = "/";

    static final String CLIENT_NOTIFY_TOPIC = "$sys/clients";

    MqttClient client;
    ConfigOptions config;

    MqttConnectOptions options = new MqttConnectOptions();

    BackgroundConnect backgroundConnect = new BackgroundConnect("JmqttClient-BackgroundConnect");

    public JmqttClient(ConfigOptions config, MqttCallback callback){
        this(null, config, callback);
    }

    public JmqttClient(String clientId, ConfigOptions config, MqttCallback callback){
        this(clientId, config, callback, null);
    }

    /**
     * @param clientId
     * @param config
     * @param callback
     * @param persistence
     */
    public JmqttClient(String clientId, ConfigOptions config, MqttCallback callback, MqttClientPersistence persistence){
        try {
            if(config == null){
                throw new IllegalArgumentException("config must be not NULL");
            }
            if(callback == null){
                throw new IllegalArgumentException("callback must be not NULL");
            }
            String connectUrl = getConnectUrl(config);
            this.config = config;
            if(persistence == null){
                persistence = new MemoryPersistence();
            }
            if(clientId == null){
                clientId = config.getClientIdPrefix() + MqttClient.generateClientId();
            }
            this.client = new MqttClient(connectUrl, clientId, persistence);
            if(config.isUseSSL()){
                options.setSocketFactory(createIgnoreVerifySSL().getSocketFactory());
                options.setSSLHostnameVerifier(new IgnoreHostnameVerifier());
            }
            options.setAutomaticReconnect(config.isAutomaticReconnect());
            options.setMaxInflight(config.getMaxInflight());
            options.setUserName(config.getUsername());
            options.setPassword(config.getPassword() == null ? null : config.getPassword().toCharArray());
            this.client.setCallback(new JmqttCallbackExtended(callback));
            this.client.setTimeToWait(config.getTimeToWait() > 0 ? config.getTimeToWait() * 1000 : -1);
            backgroundConnect.start();
        } catch (MqttException e) {
            logger.error(JmqttClient.class.getSimpleName() + " start error: " + e.getMessage(), e);
        }
    }

    private String getConnectUrl(ConfigOptions config){
        if(config.getHost() == null || config.getPort() < 1){
            throw new IllegalArgumentException("connect host and port invalid");
        }
        StringBuilder sb = new StringBuilder(config.isUseSSL() ? "ssl://" : "tcp://");
        sb.append(config.getHost());
        sb.append(":");
        sb.append(config.getPort());
        return sb.toString();
    }

    public void publish(String topic, String message) throws JmqttException{
        this.publish(topic, message, config.getDefaultQos(), false);
    }

    public void publish(String topic, String message, int qos, boolean retained) throws JmqttException{
        try {
            this.publish(topic, message.getBytes("UTF-8"), qos, retained);
        } catch (UnsupportedEncodingException e) {
            throw new JmqttException(e);
        }
    }

    public void publish(String topic, byte[] message, int qos, boolean retained) throws JmqttException{
        try {
            if(!topic.startsWith(TOPIC_START_CHART)){
                topic = TOPIC_START_CHART + topic;
            }
            this.client.publish(topic, message, qos, retained);
        } catch (MqttException e) {
            throw new JmqttException(e);
        }
    }

    public void subscribe(String topicFilter, int qos) throws JmqttException {
        this.subscribe(new String[] {topicFilter}, new int[] {qos});
    }

    public void subscribe(String[] topicFilters, int[] qos) throws JmqttException {
        try {
            client.subscribe(topicFilters, qos);
        } catch (MqttException e) {
            throw new JmqttException(e);
        }
    }

    public void unsubscribe(String topicFilter) throws JmqttException {
        this.unsubscribe(new String[] {topicFilter});
    }

    public void unsubscribe(String[] topicFilters) throws JmqttException {
        try {
            client.unsubscribe(topicFilters);
        } catch (MqttException e) {
            throw new JmqttException(e);
        }
    }

    public void close(){
        try {
            client.disconnect();
        } catch (MqttException e) {
            logger.error(e.getMessage(), e);
        }
    }

    class BackgroundConnect extends Thread{

        boolean checkMode;

        public BackgroundConnect(String name){
            super(name);
        }

        @Override
        public void run() {
            while (true){
                JmqttClient.this.doConnect(checkMode);
            }
        }

    }

    /*递归调用，注意栈溢出*/
    private void doConnect(final boolean reconnect){
        if(reconnect){
            try {
                Thread.sleep(10000);
                if(!backgroundConnect.checkMode)logger.info(JmqttClient.class.getSimpleName() + " client reconnect with id: " + client.getClientId());
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
        try {
            if(this.client.isConnected()){
                logger.debug(JmqttClient.class.getSimpleName() + " check connect with id: " + client.getClientId());
                return;
            }
            this.client.connectWithResult(options, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    backgroundConnect.checkMode = true;
                    logger.info(JmqttClient.class.getSimpleName() + " started and connected with id: " + client.getClientId());
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                }
            });
        } catch (MqttException e) {
            // do something in onFailure
            logger.error(JmqttClient.class.getSimpleName() + " start failed with id: " + client.getClientId() + "error["+e.getMessage()+"]", e);
            JmqttClient.this.doConnect(true);
        }
    }

    private SSLContext createIgnoreVerifySSL() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            // 实现一个X509TrustManager接口，用于绕过验证，不用修改里面的方法
            X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] paramArrayOfX509Certificate,
                        String paramString) throws CertificateException {
                }
                @Override
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] paramArrayOfX509Certificate,
                        String paramString) throws CertificateException {
                }
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            sc.init(null, new TrustManager[] { trustManager }, null);
            return sc;
        } catch (Exception e) {
        }
        return null;
    }

    private class IgnoreHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    private class JmqttCallbackExtended implements MqttCallbackExtended{

        private MqttCallback customCallback;

        JmqttCallbackExtended(MqttCallback customCallback){
            this.customCallback = customCallback;
            if(customCallback instanceof JmqttClientAware){
                ((JmqttClientAware)customCallback).setClient(JmqttClient.this);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            customCallback.connectionLost(cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            if(config.isClientNotify() && CLIENT_NOTIFY_TOPIC.equals(topic)){
                if(customCallback instanceof JmqttSessionNotify){
                    try {
                        String info = new String(message.getPayload());
                        String[] is = info.split(",", -1);
                        if(is.length == 1){
                            ((JmqttSessionNotify) customCallback).onSessionClose(is[0]);
                        }else if(is.length == 4){
                            ((JmqttSessionNotify) customCallback).onSessionOpen(is[0], is[1], is[2], is[3]);
                        }
                    } catch (Exception e) {
                        logger.error("process client notify error!", e);
                    }
                }
                return;
            }
            customCallback.messageArrived(topic, message);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            customCallback.deliveryComplete(token);
            try {
                MqttMessage message = token.getMessage();
                if(message != null && token instanceof MqttDeliveryToken){
                    ((MqttDeliveryToken) token).setMessage(null);
                }
            } catch (MqttException e) {
            }
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if(config.isClientNotify()){
                try {
                    JmqttClient.this.subscribe(CLIENT_NOTIFY_TOPIC, 1);
                } catch (JmqttException e) {
                    logger.error("Subscribe client notify topic error: " + e.getMessage(), e);
                }
            }
            if(customCallback instanceof MqttCallbackExtended){
                ((MqttCallbackExtended) customCallback).connectComplete(reconnect, serverURI);
            }
        }
    }
}
