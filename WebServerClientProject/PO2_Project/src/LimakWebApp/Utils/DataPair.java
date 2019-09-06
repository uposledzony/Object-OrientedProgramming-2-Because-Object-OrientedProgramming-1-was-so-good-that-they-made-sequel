package LimakWebApp.Utils;

import LimakWebApp.DataPackets.CredentialPacket;

import java.util.Map;

/**
 * <h1>DataPair</h1>
 * This class is used by {@link LimakWebApp.ServerSide.MainPageController} to maintain list of clients
 * @author  Kamil Chrustowski
 * @version 1.0
 * @since   12.08.2019
 */
public class DataPair implements Map.Entry<CredentialPacket, Boolean>{

    private CredentialPacket key;
    private Boolean value;

    /**
     * Basic constructor of {@link DataPair}
     * @param packet user's credentials
     * @param value status of user
     */
    public DataPair(CredentialPacket packet, Boolean value){
        this.value = value;
        key = packet;
    }

    /**
     * Sets given value to key
     * @param value Value to update
     * @return boolean
     */
    @Override
    public Boolean setValue(Boolean value){
        Boolean oldValue = this.value;
        this.value = value;
        return oldValue;
    }

    /**
     * Returns Key
     * @return {@link CredentialPacket}
     */
    @Override
    public CredentialPacket getKey(){
        return key;
    }

    /**
     * Returns Value
     * @return {@link Boolean}
     */
    @Override
    public Boolean getValue(){
        return value;
    }

    /**
     * Overridden hashCode method, returns hashcode generated by XOR operation on key hashcode and value hashcode
     * @return int
     */
    @Override
    public int hashCode() {
        int keyHash = (key==null ? 0 : key.hashCode());
        int valueHash = (value==null ? 0 : value.hashCode());
        return keyHash ^ valueHash;
    }

    /**
     * Overridden equals method, compares equality of {@link CredentialPacket} and {@link Boolean}
     * @param o Object to check equality
     * @return boolean
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Map.Entry) || !(((Map.Entry) o).getKey() instanceof CredentialPacket) ||  !(((Map.Entry) o).getValue() instanceof Boolean))
            return false;
        return ((Map.Entry<CredentialPacket, Boolean>)o).getKey().compareTo(key) == 0 && ((Map.Entry<CredentialPacket, Boolean>) o).getValue().equals(value);
    }

    /**
     * Displays pair
     * @return {@link String}
     */
    @Override
    public String toString() {
        return key.getUserName() + " " + (value ? "online" : "offline");
    }

}