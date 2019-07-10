package xyz.zood.george.time;

public class HoursMinutesSeconds {

    public final int hours;
    public final int minutes;
    public final int seconds;

    public HoursMinutesSeconds(long milliseconds) {
        int secs = (int)(milliseconds / 1000);
        hours = secs / 3600;
        int surplusSeconds = secs % 3600;
        minutes = surplusSeconds / 60;
        seconds = surplusSeconds % 60;
    }

}
