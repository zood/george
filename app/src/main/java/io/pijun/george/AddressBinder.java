package io.pijun.george;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import java.util.Locale;

import io.pijun.george.api.GMapsClient;
import io.pijun.george.api.ReverseGeocoding;
import io.pijun.george.models.FriendLocation;
import retrofit2.Call;
import retrofit2.Response;

public class AddressBinder {

    private long mFriendId;
    private Context mContext;
    private AddressReceiver mReceiver;
    private String mResult = null;
    private volatile boolean mCancelled = false;


    public AddressBinder(@NonNull AddressReceiver rcvr, @NonNull Context context, long friendId) {
        this.mFriendId = friendId;
        this.mContext = context;
        this.mReceiver = rcvr;
        this.mReceiver.setAddressBinder(this);
    }

    @AnyThread
    public void cancel() {
        mCancelled = true;
    }

    @AnyThread
    public void start() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    _start();
                }
            });
        } else {
            //noinspection WrongThread
            _start();
        }
    }

    @WorkerThread
    private void _start() {
        if (mCancelled) {
            return;
        }

        FriendLocation location = DB.get(mContext).getFriendLocation(mFriendId);
        if (mCancelled) {
            return;
        }

        if (location == null) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    finish();
                }
            });
            return;
        }

        String latlng = location.latitude + "," + location.longitude;
        L.i("latlng: " + latlng);
        Call<ReverseGeocoding> call = GMapsClient.get().
                getReverseGeocoding(latlng, Locale.getDefault().getLanguage());
        try {
            Response<ReverseGeocoding> response = call.execute();
            if (mCancelled) {
                return;
            }
            if (response.isSuccessful()) {
                ReverseGeocoding body = response.body();
                mResult = body.getLocalityAddress();
                L.i("geocoding result: " + body);
//                L.i("raw geocoding: " + response.raw().body().string());
            } else {
                L.w("failed to retrieve reverse geocoding:\n" + response.errorBody().string());
            }
        } catch (Exception ex) {
            L.w("unable to perform geocoding - " + ex.getLocalizedMessage());
        }

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    @UiThread
    private void finish() {
        if (mCancelled) {
            return;
        }
        if (mReceiver.getAddressBinder() != this) {
            return;
        }

        mReceiver.onAddressResolved(mResult);
    }

    interface AddressReceiver {
        @AnyThread
        AddressBinder getAddressBinder();
        @AnyThread
        void setAddressBinder(AddressBinder binder);
        @UiThread
        void onAddressResolved(@Nullable String address);
    }

}
