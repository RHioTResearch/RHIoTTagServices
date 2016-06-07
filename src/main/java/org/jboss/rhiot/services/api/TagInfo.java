package org.jboss.rhiot.services.api;

/**
 * Created by sstark on 6/6/16.
 */
public class TagInfo {
    private String address;
    private String name;

    public TagInfo() {
    }
    public TagInfo(String address, String name) {
        this.address = address;
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
