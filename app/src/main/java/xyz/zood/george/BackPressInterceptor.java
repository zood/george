package xyz.zood.george;

public interface BackPressInterceptor {

    /**
     * Method that an activities fragments can implement to receive notifications about user
     * back button presses. The interceptor should return true when it consumes the back
     * press, and false otherwise.
     * @return true if the back press was consume; false otherwise.
     */
    boolean onBackPressed();

}
