package org.jboss.rhiot.services.api;

/**
 * The service interface for each user's game settings that they control
 */
public interface ILaserTag {
    /**
     *
     * @return the ble address of the RHIoTTag
     */
    public short[] getTagAddress();

    /**
     *
     * @return a meaningful name assigned to the RHIoTTag
     */
    public String getName();

    /**
     *
     * @return
     */
    public int getLeftBtnWindow();

    /**
     * Above what value must the light sensor raw lux reading be to be considered a hit.
     * @return lux threshold value above which a hit is recorded.
     */
    public int getLuxThreshold();
}
