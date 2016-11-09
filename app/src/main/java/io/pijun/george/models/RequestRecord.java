package io.pijun.george.models;

public class RequestRecord {

    public long id;
    public long userId;
    public long sentDate;
    public RequestResponse response;

    @Override
    public String toString() {
        return "RequestRecord{" +
                "id=" + id +
                ", userId=" + userId +
                ", sentDate=" + sentDate +
                ", response=" + response +
                '}';
    }
}
