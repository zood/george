package io.pijun.george.api;

public class ReverseGeocoding {

    public static class AddressComponent {
        public String longName;
        public String shortName;
        public String[] types;
    }

    public static class Result {
//        public AddressComponent[] addressComponents;
        public String formattedAddress;
        public String placeId;
        public String[] types;
    }

    public String status;
    public Result[] results;

}
