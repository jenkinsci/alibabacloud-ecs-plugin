package com.alibabacloud.jenkins.ecs;

public enum ConnectionStrategy {

    PUBLIC_IP("Public IP"),

    PRIVATE_IP("Private IP");

    private final String displayText;

    ConnectionStrategy(String displayText) {
        this.displayText = displayText;
    }

    /**
     * For backwards compatibility.
     * @param connectUsingPublicIp whether or not to use a public ip to establish a connection.
     * @param associatePublicIp whether or not to associate to a public ip.
     * @return an {@link ConnectionStrategy} based on provided parameters.
     */
    public static ConnectionStrategy backwardsCompatible(boolean connectUsingPublicIp, boolean associatePublicIp) {
         if (connectUsingPublicIp || associatePublicIp) {
            return PUBLIC_IP;
        } else {
            return PRIVATE_IP;
        }
    }

    public String getDisplayText() {
        return this.displayText;
    }
}
