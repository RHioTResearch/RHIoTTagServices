package com.eurotech.cloud.examples;


import com.eurotech.cloud.client.EdcCallbackHandler;
import com.eurotech.cloud.client.EdcClientFactory;
import com.eurotech.cloud.client.EdcCloudClient;
import com.eurotech.cloud.client.EdcConfiguration;
import com.eurotech.cloud.client.EdcConfigurationFactory;
import com.eurotech.cloud.client.EdcDeviceProfile;
import com.eurotech.cloud.client.EdcDeviceProfileFactory;
import com.eurotech.cloud.message.EdcBirthPayload;
import com.eurotech.cloud.message.EdcPayload;
import org.apache.log4j.Logger;


/**
 * Sample Java client which connects to the Everyware Cloud platform.
 * It simulates a device whose connection logic is written in Java
 * and it illustrates how devices can connect to the Everyware Cloud
 * and publish/received data.
 *
 * The EdcJavaClient class performs the following actions:
 *
 * 1. Prepares the configuration
 * 2. Connects to the broker and start a session
 * 3. Subscribes to the a couple of topic
 * 4. Starts publishing some data and verify that the messages are also received
 * 5. Disconnect
 *
 */
public class EdcJavaClient implements EdcCallbackHandler
{
    private static final Logger log = Logger.getLogger(EdcJavaClient.class);

    // >>>>>> Set these variables according to your Cloud user account
    //
    private static final String ACCOUNT_NAME = "Red-Hat";                                    // Your Account name in Cloud
    private static final String ASSET_ID     = "sstark-client";                                       // Unique Asset ID of this client device
    private static final String BROKER_URL   = "mqtt://broker-sandbox.everyware-cloud.com:1883";  // URL address of broker
    private static final String CLIENT_ID    = "sstark-client";                                // Unique Client ID of this client device
    private static final String USERNAME     = "s-stark";                            // Username in account, to use for publishing
    private static final String PASSWORD     = "A8LEMP5!s9Gvk!";                                 // Password associated with Username
    //
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


    public static void main(String[] args)
            throws Exception
    {
        //
        // Configure: create client configuration, and set its properties
        //
        EdcConfigurationFactory confFact = EdcConfigurationFactory.getInstance();
        EdcConfiguration conf = confFact.newEdcConfiguration(ACCOUNT_NAME,
                ASSET_ID,
                BROKER_URL,
                CLIENT_ID,
                USERNAME,
                PASSWORD);

        EdcDeviceProfileFactory profFactory = EdcDeviceProfileFactory.getInstance();
        EdcDeviceProfile prof = profFactory.newEdcDeviceProfile();
        prof.setDisplayName("sstark-gateway client test");			// friendly name for this CLIENT_ID, for display in the Cloud
        prof.setModelName("Eclipse Java Client");

        //set GPS position in device profile - this is sent only once, with the birth certificate
        prof.setLongitude(-122.52929);
        prof.setLatitude(47.14170);

        //
        // Connect and start the session
        //
        EdcCloudClient edcCloudClient = EdcClientFactory.newInstance(conf, prof, new EdcJavaClient());
        edcCloudClient.startSession();
        log.info("Session started");

        //
        // Subscribe
        //
        //log.info("Subscribe to data topics of this asset in the account");
        //edcCloudClient.subscribe("#", 1);

        log.info("Subscribe to data topics of all assets in the account");
        //edcCloudClient.subscribe("+", "#", 1);
        edcCloudClient.subscribe("sstark-gateway/org.jboss.rhiot.services.RHIoTTagScanner/data", "+", 1);

        System.out.println("Subscribe to control topics of all assets in the account");
        edcCloudClient.controlSubscribe("+", "#", 1);

        //
        // Sleep to allow receipt of more publishes, then terminate connection
        log.info("Waiting 60 seconds to wait for any more published messages.");
        int listenSeconds = 60;             //keep connection alive for listenSeconds
        Thread.sleep(listenSeconds * 1000); //sleep in milliseconds

        //
        // Stop the session and close the connection
        //
        edcCloudClient.stopSession();
        edcCloudClient.terminate();
        log.info("Terminating EDC Cloud Client");
    }


    // -----------------------------------------------------------------------
    //
    //    MQTT Callback methods
    //
    // -----------------------------------------------------------------------

    //display control messages received from broker
    public void controlArrived(String assetId, String topic, EdcPayload msg, int qos, boolean retain)
    {
        log.info("Control publish arrived on semantic topic: " + topic + " , qos: " + qos);
        // Print all the metrics
        for (String name : msg.metricNames()) {
            System.out.println(name + ":" + msg.getMetric(name));
        }

        if (topic.contains("BC")) {
            EdcBirthPayload edcBirthMessage;
            try {
                edcBirthMessage = new EdcBirthPayload(msg);
                System.out.println("Birth certificate arrived: " + edcBirthMessage.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //display data messages received from broker
    public void publishArrived(String assetId, String topic, EdcPayload msg, int qos, boolean retain) {
        log.debug("Data publish arrived on semantic topic: " + topic + ", qos: " + qos + ", assetId: " + assetId);
        // Print all the metrics
        for (String name : msg.metricNames()) {
            String content = "";
            if (msg.getMetric(name).getClass().isArray()) {
                //display byte arrays as both hex characters and String
                byte[] contentArray = (byte[]) msg.getMetric(name);
                for (int i = 0; i < contentArray.length; i++) {
                    content = content + Integer.toHexString(0xFF & contentArray[i]) + " ";

                }
                content = content + " (as String: '" + new String(contentArray) + "')";
            } else {
                content = msg.getMetric(name).toString();
            }
            System.out.println(name + ":" + content);
        }
    }

    public void connectionLost() {
        log.warn("EDC client connection lost");
    }

    public void connectionRestored() {
        log.warn("EDC client reconnected");
    }

    public void published(int messageId) {
        log.debug("Publish message ID: " + messageId + " confirmed");
    }

    public void subscribed(int messageId) {
        log.info("Subscribe message ID: " + messageId + " confirmed");
    }

    public void unsubscribed(int messageId) {
        log.info("Unsubscribe message ID: " + messageId + " confirmed");
    }

    public void controlArrived(String assetId, String topic, byte[] payload, int qos, boolean retain) {
        log.info("controlArrived, assetId: "+assetId+", topic: "+topic);
    }

    public void publishArrived(String assetId, String topic, byte[] payload, int qos, boolean retain) {
        log.debug("publishArrived, assetId: "+assetId+", topic: "+topic);
    }
}
