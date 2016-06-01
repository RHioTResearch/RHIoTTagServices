package org.jboss.rhiot.services;

import org.jboss.rhiot.ble.bluez.AdEventInfo;
import org.jboss.rhiot.ble.bluez.HCIDump;
import org.jboss.rhiot.ble.bluez.IAdvertEventCallback;
import org.jboss.rhiot.ble.bluez.RHIoTTag;
import org.jboss.rhiot.services.api.ILaserTag;
import org.jboss.rhiot.services.api.IRHIoTTagScanner;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.http.HttpService;

import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * The main entry point for the scanner facade on top of the HCIDump general scanner that extracts RHIoTTag specific
 * advertising events and
 */
public class RHIoTTagScanner implements ConfigurableComponent, CloudClientListener, IRHIoTTagScanner, IAdvertEventCallback {
    private static final String APP_ID = "org.jboss.rhiot.services.RHIoTTagScanner";
	private static final String   PUBLISH_TOPIC_PROP_NAME  = "publish.semanticTopic";
	private static final String   PUBLISH_QOS_PROP_NAME    = "publish.qos";
	private static final String   PUBLISH_RETAIN_PROP_NAME = "publish.retain";

	private Map<String, Object> properties;
	private Map<String, ILaserTag> tags;
	private BlockingDeque<RHIoTTag> eventQueue;
    private volatile boolean scannerInitialized;
	/** */
	private volatile int gameDurationSecs;
	private CloudService cloudService;
	private CloudClient cloudClient;

	public void setCloudService(CloudService cloudService) {
		this.cloudService = cloudService;
		log("setCloudService, cs=%s\n", cloudService);
	}

	public void unsetCloudService(CloudService cloudService) {
		this.cloudService = null;
		log("unsetCloudService\n");
	}

	public void unsetHttpService(HttpService httpService) {
		httpService.unregister("/rhiot");
	}

	public void setHttpService(HttpService httpService) {
		RHIoTServlet servlet = new RHIoTServlet(this);
		try {
			httpService.registerServlet("/rhiot", servlet, null, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setLaserTagInfo(ILaserTag tagInfo) {
		log("+++ setLaserTagInfo, info=%s\n", tagInfo);
		short[] address = tagInfo.getTagAddress();
		String addressKey = Utils.toString(address);
		if(tags.containsKey(addressKey)) {
			ILaserTag prev = tags.get(addressKey);
			log("Warning, tag(%s) conflicts: %s with: %s", addressKey, tagInfo.getName(), prev.getName());
		}
		tags.put(addressKey, tagInfo);
	}
	public void unsetLaserTagInfo(ILaserTag tagInfo) {
		log("--- unsetLaserTagInfo, info=%s\n", tagInfo);
		short[] address = tagInfo.getTagAddress();
		String addressKey = Utils.toString(address);
		tags.remove(addressKey);
	}

	public Collection<ILaserTag> getTags() {
		return tags.values();
	}

    @Override
	public void onConnectionEstablished() {
		log("onConnectionEstablished\n");
	}

	@Override
	public void onConnectionLost() {
		log("onConnectionLost\n");
		
	}

	@Override
	public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
		log("onControlMessageArrived(devicdId=%s, appTopic=%s, qos=%d, retain=%s, msg=%s\n", deviceId, appTopic, qos, retain, msg);		
	}

	@Override
	public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
		log("onMessageArrived(devicdId=%s, appTopic=%s, qos=%d, retain=%s, msg=%s\n", deviceId, appTopic, qos, retain, msg);
	}

	@Override
	public void onMessageConfirmed(int messageId, String appTopic) {
		log("onMessageConfirmed(%s,%s)\n", messageId, appTopic);
	}

	@Override
	public void onMessagePublished(int messageId, String appTopic) {
		log("onMessagePublished(%s,%s)\n", messageId, appTopic);
	}

	@Override
	public boolean advertEvent(AdEventInfo info) {
		System.out.printf("+++ advertEvent, rssi=%d, time=%s\n", info.getRssi(), new Date(info.getTime()));
		RHIoTTag tag = RHIoTTag.create(info);
		if(tag != null) {
			String key = tag.getAddressString();
			ILaserTag tagInfo = tags.get(key);
			String name = tagInfo != null ? tagInfo.getName() : "<unamed>";
			log("(%s: %s\n", name, tag.toFullString());
		}
		return false;
	}

	protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.activate; Bundle " + APP_ID + " has started with: %s\n", properties);
		this.properties = properties;
		this.eventQueue = new LinkedBlockingDeque<>();
        System.out.printf("hciDev=%s\n", properties.get("hciDev"));

		if(tags == null) {
			tags = new ConcurrentHashMap<>();
		}
		// get the mqtt client for this application
		try  {
			
			// Acquire a Cloud Application Client for this Application
			if(cloudService != null) {
				log("Getting CloudClient for %s...", APP_ID);
				cloudClient = cloudService.newCloudClient(APP_ID);
				cloudClient.addCloudClientListener(this);
			} else {
				log("No CloudService found\n");
			}
			
			// Don't subscribe because these are handled by the default 
			// subscriptions and we don't want to get messages twice			
			//doUpdate(false);
		}
		catch (Exception e) {
			log("Error during component activation, %s", e);
			throw new ComponentException(e);
		}

        updated(properties);
    }

    protected void deactivate(ComponentContext componentContext) {
        HCIDump.setAdvertEventCallback(null);
        HCIDump.freeScanner();
        scannerInitialized = false;
		tags.clear();
		eventQueue.clear();
		tags = null;
		eventQueue = null;
        System.out.printf("RHIoTTagScanner.deactivate; Bundle " + APP_ID + " has stopped!\n");
    }

    protected void updated(Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.updated; Bundle " + APP_ID + " has updated!\n");
        String hciDev = (String) properties.get("hciDev");
        if(hciDev == null)
        	hciDev = "hci1";
        boolean debugMode = false;
		if(properties.get("hcidumpDebugMode") != null)
			debugMode = (Boolean) properties.get("hcidumpDebugMode");
        System.out.printf("hciDev=%s\n", hciDev);
        HCIDump.loadLibrary();
        this.properties = properties;
        if(properties != null && !properties.isEmpty()) {
            Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
				String key = entry.getKey();
				Object value = entry.getValue();
				String type = value != null ? value.getClass().getName() : "none";
                System.out.printf("New property - %s = %s of type: %s\n", key, value, type);
            }
        }
        // Setup scanner
        HCIDump.enableDebugMode(debugMode);
        HCIDump.setAdvertEventCallback(this);
        if(scannerInitialized == false) {
            HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
			System.out.printf("Initialized scanner\n");
        } else {
			HCIDump.freeScanner();
			HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
            System.out.printf("Reinitialized scanner\n");
        }
        scannerInitialized = true;
    }
    
    private void log(String format, Object ...args) {
    	System.out.printf(format, args);
    }

	private void handleTagEvents() {
		while (scannerInitialized) {
			try {
				RHIoTTag tag = eventQueue.poll(10, TimeUnit.MILLISECONDS);
				// TODO: See if there is a hit
				// TODO: See if the game restart
				// TODO: See if the shooter is out of bullets
				doPublish(tag);

			} catch (InterruptedException e) {
			}
		}
	}

	private void doPublish(RHIoTTag tag)
	{
		// fetch the publishing configuration from the publishing properties
		String  topicRoot  = (String) properties.get(PUBLISH_TOPIC_PROP_NAME);
		String topic = topicRoot + "/" + tag.getAddressString();
		Integer qos    = (Integer) properties.get(PUBLISH_QOS_PROP_NAME);
		Boolean retain = (Boolean) properties.get(PUBLISH_RETAIN_PROP_NAME);

		// Allocate a new payload
		KuraPayload payload = new KuraPayload();

		// Timestamp the message
		payload.setTimestamp(new Date());

		payload.addMetric("rhiotTag.temperature", tag.getTempC());
		payload.addMetric("rhiotTag.keys", (int) tag.getKeys());
		payload.addMetric("rhiotTag.lux", (int) tag.getLux());

		// Publish the message
		try {
			cloudClient.publish(topic, payload, qos, retain);
			log("Published to: %s message: %s", topic, payload);
		}
		catch (Exception e) {
			log("Cannot publish topic: %s\n", topic, e);
		}
	}
}
