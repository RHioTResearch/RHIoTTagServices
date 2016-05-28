package org.jboss.rhiot.services;

import org.jboss.rhiot.ble.bluez.AdEventInfo;
import org.jboss.rhiot.ble.bluez.HCIDump;
import org.jboss.rhiot.ble.bluez.IAdvertEventCallback;
import org.jboss.rhiot.ble.bluez.RHIoTTag;
import org.jboss.rhiot.services.api.IRHIoTTagScanner;
import org.osgi.service.component.ComponentContext;
import org.eclipse.kura.configuration.ConfigurableComponent;

import java.nio.ByteOrder;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * The main entry point for the scanner facade on top of the HCIDump general scanner that extracts RHIoTTag specific
 * advertising events and
 */
public class RHIoTTagScanner implements ConfigurableComponent, IRHIoTTagScanner, IAdvertEventCallback {
    private static final String APP_ID = "org.jboss.rhiot.services.RHIoTTagScanner";
    private Map<String, Object> properties;
    private volatile boolean scannerInitialized;

    @Override
    public boolean advertEvent(AdEventInfo info) {
        System.out.printf("+++ advertEvent, rssi=%d, time=%s\n", info.getRssi(), new Date(info.getTime()));
        RHIoTTag tag = RHIoTTag.create(info);
        if(tag != null) {
            System.out.printf("%s\n", tag.toFullString());
        }
        return false;
    }

    
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.activate2; Bundle " + APP_ID + " has started with: %s\n", properties);
        System.out.printf("hciDev=%s\n", properties.get("hciDev"));
        updated(properties);
    }

    protected void deactivate(ComponentContext componentContext) {
        HCIDump.setAdvertEventCallback(null);
        HCIDump.freeScanner();
        scannerInitialized = false;
        System.out.printf("RHIoTTagScanner.deactivate; Bundle " + APP_ID + " has stopped!\n");
    }

    public void updated(Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.updated; Bundle " + APP_ID + " has updated!\n");
        String hciDev = properties.get("hciDev").toString();
        boolean debugMode = (Boolean) properties.get("hcidumpDebugMode");
        System.out.printf("hciDev=%s\n", hciDev);
        HCIDump.loadLibrary();
        this.properties = properties;
        if(properties != null && !properties.isEmpty()) {
            Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                System.out.printf("New property - %s = %s of type: %s\n", entry.getKey(), entry.getValue(), entry.getValue().getClass());
            }
        }
        // Setup scanner
        HCIDump.enableDebugMode(debugMode);
        HCIDump.setAdvertEventCallback(this);
        if(scannerInitialized == false) {
            HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
        } else {
            System.out.printf("Scanner already iniatialized, skipped HCIDump.initScanner\n");
        }
        scannerInitialized = true;
    }
}
