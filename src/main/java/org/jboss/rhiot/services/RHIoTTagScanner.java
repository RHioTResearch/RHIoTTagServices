package org.jboss.rhiot.services;

import org.jboss.rhiot.services.api.IRHIoTTagScanner;
import org.osgi.service.component.ComponentContext;
import org.eclipse.kura.configuration.ConfigurableComponent;

import java.util.Dictionary;
import java.util.Iterator;
import java.util.Map;

/**
 * The main entry point for the scanner facade on top of the HCIDump general scanner that extracts RHIoTTag specific
 * advertising events and
 */
public class RHIoTTagScanner implements ConfigurableComponent, IRHIoTTagScanner {
    private static final String APP_ID = "org.jboss.rhiot.services.RHIoTTagScanner";
    private Map<String, Object> properties;

    protected void activate(ComponentContext componentContext) {
        System.out.printf("RHIoTTagScanner.activate; Bundle " + APP_ID + " has started!\n");
        Dictionary<String,Object> dict = componentContext.getProperties();
        System.out.printf("hciDev=%s\n", dict.get("hciDev"));
        System.out.printf("Loading scannerJni library...\n");
        try {
            System.loadLibrary("scannerJni");
            System.out.printf("done\n");
        }catch (Exception e) {
            System.out.printf("Failed:\n");
            e.printStackTrace();
        }
    }

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.activate2; Bundle " + APP_ID + " has started with: %s\n", properties);
        System.out.printf("hciDev=%s\n", properties.get("hciDev"));
        updated(properties);
    }

    protected void deactivate(ComponentContext componentContext) {
        System.out.printf("RHIoTTagScanner.deactivate; Bundle " + APP_ID + " has stopped!\n");
    }

    public void updated(Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.updated; Bundle " + APP_ID + " has stopped!\n");
        System.out.printf("hciDev=%s\n", properties.get("hciDev"));
        this.properties = properties;
        if(properties != null && !properties.isEmpty()) {
            Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                System.out.printf("New property - %s = %s of type: %s\n", entry.getKey(), entry.getValue(), entry.getValue().getClass());
            }
        }
    }
}
