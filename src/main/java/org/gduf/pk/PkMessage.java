package org.gduf.pk;

public class PkMessage {
    private String type;
    private Object payload;
    private Long timestamp;

    public PkMessage() {}

    public PkMessage(String type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
