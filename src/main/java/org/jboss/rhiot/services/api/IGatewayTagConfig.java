package org.jboss.rhiot.services.api;

/**
 * Provides the mappings from the RHIoTTag BLE address to a name for each tag that is to be associated with the
 * gateway.
 */
public interface IGatewayTagConfig {

    public String getTagName(int index);
    public String getTagAddress(int index);
    public String getNameByAddress(String address);
}
