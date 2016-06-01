package org.jboss.rhiot.services;

/**
 * Created by sstark on 5/31/16.
 */
public class Utils {
    public static String toString(short[] address) {
        StringBuilder tmp = new StringBuilder("");
        if(address != null) {
            for (int n = 0; n < address.length; n ++) {
                tmp.append(String.format("%02X", address[n]));
                tmp.append(':');
            }
            tmp.setLength(tmp.length()-1);
        } else {
            tmp.append(":::::");
        }
        return tmp.toString();
    }
}
