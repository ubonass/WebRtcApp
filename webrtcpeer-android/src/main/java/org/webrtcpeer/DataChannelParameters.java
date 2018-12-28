package org.webrtcpeer;

public class DataChannelParameters {
    public final boolean ordered;
    public final int maxRetransmitTimeMs;
    public final int maxRetransmits;
    public final String protocol;
    public final boolean negotiated;
    public final int id;

    public DataChannelParameters(boolean ordered,
                                 int maxRetransmitTimeMs,
                                 int maxRetransmits,
                                 String protocol,
                                 boolean negotiated, int id) {
        this.ordered = ordered;
        this.maxRetransmitTimeMs = maxRetransmitTimeMs;
        this.maxRetransmits = maxRetransmits;
        this.protocol = protocol;
        this.negotiated = negotiated;
        this.id = id;
    }

    @Override
    public String toString() {
        return "DataChannelParameters{" +
                "ordered=" + ordered +
                ", maxRetransmitTimeMs=" + maxRetransmitTimeMs +
                ", maxRetransmits=" + maxRetransmits +
                ", protocol='" + protocol + '\'' +
                ", negotiated=" + negotiated +
                ", id=" + id +
                '}';
    }
}
