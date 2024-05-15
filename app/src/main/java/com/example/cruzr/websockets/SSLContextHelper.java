package com.example.cruzr.websockets;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import android.content.Context;
import android.util.Log;

public class SSLContextHelper {

    public static SSLContext createSSLContext(Context context) throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException,
            UnrecoverableKeyException, KeyManagementException {

        // self-signed certificate @ app/src/main/assets
        // for java's default key & trust store refer to Java-WebSocket example
        // https://github.com/TooTallNate/Java-WebSocket/blob/master/src/main/example/SSLServerExample.java
        String keystoreFileName = "certificate.bks";
        String keystorePassword = "7d7dd475";

        try (InputStream keystoreInputStream = context.getAssets().open(keystoreFileName)) {

            KeyStore keystore = KeyStore.getInstance("BKS");
            keystore.load(keystoreInputStream, keystorePassword.toCharArray());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
            keyManagerFactory.init(keystore, keystorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(keystore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), null);

            return sslContext;

        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException |
                 UnrecoverableKeyException | KeyManagementException exception) {
            Log.e("Server", "Invalid SSL certificate " + exception);
            throw exception;
        }
    }
}
