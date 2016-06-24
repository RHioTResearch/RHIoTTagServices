package org.jboss.rhiot.services;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.jboss.rhiot.services.api.IGatewayTagConfig;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple service for configuration the tag ble address to name
 */
public class RHIoTTagConfig implements ConfigurableComponent, IGatewayTagConfig {
    private static final Logger log = LoggerFactory.getLogger(RHIoTTagConfig.class);
    /** The mapping from the tag BLE address to a user assigned name */
    private Map<String, String> addressToNameMap;
    private ArrayList<String> addresses;
    private ArrayList<String> names;

    @Override
    public String getTagName(int index) {
        return names.get(index);
    }

    @Override
    public String getTagAddress(int index) {
        return addresses.get(index);
    }

    @Override
    public String getNameByAddress(String address) {
        return addressToNameMap.get(address);
    }

    public void updateTagInfo(String address, String name) {
        addressToNameMap.put(address, name);
        log.info("Updated name for: "+address+" to: "+name);
    }

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        log.info("activate; Bundle has started with: ", properties.entrySet());
        addressToNameMap = new ConcurrentHashMap<>();
        addresses = new ArrayList<>();
        names = new ArrayList<>();
        updated(properties);
    }

    protected void updated(Map<String, Object> properties) {
        log.info("Updated, properties="+properties);

        for(int n = 0; n < 8; n ++) {
            String key = "gw.tag" + n;
            String[] info = (String[]) properties.get(key);
            if(info != null) {
                String address = info[0];
                String name = info[1];
                updateTagInfo(address, name);
                addresses.add(address);
                names.add(name);
            }
        }
    }
}
