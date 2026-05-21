package com.example.testrepo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class NetworkStateHelper {
    private NetworkStateHelper() {
    }

    static boolean hasInternetConnection(@NonNull Context context) {
        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (connectivityManager == null) {
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        return networkCapabilities != null
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    @Nullable
    static ConnectivityManager.NetworkCallback registerDefaultNetworkCallback(
            @NonNull Context context,
            @NonNull Runnable onChanged
    ) {
        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (connectivityManager == null) {
            return null;
        }

        ConnectivityManager.NetworkCallback networkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        onChanged.run();
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        onChanged.run();
                    }

                    @Override
                    public void onCapabilitiesChanged(
                            @NonNull Network network,
                            @NonNull NetworkCapabilities networkCapabilities
                    ) {
                        onChanged.run();
                    }

                    @Override
                    public void onUnavailable() {
                        onChanged.run();
                    }
                };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            return networkCallback;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static void unregisterNetworkCallback(
            @NonNull Context context,
            @Nullable ConnectivityManager.NetworkCallback networkCallback
    ) {
        if (networkCallback == null) {
            return;
        }

        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (connectivityManager == null) {
            return;
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
            // Ignore callbacks that are already unregistered or no longer valid.
        }
    }

    @Nullable
    private static ConnectivityManager getConnectivityManager(@NonNull Context context) {
        return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
}
