/**
 * $RCSfile$
 * $Revision: 3306 $
 * $Date: 2006-01-16 14:34:56 -0300 (Mon, 16 Jan 2006) $
 *
 * Copyright 2003-2011 Jive Software, Glenn Maynard.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.WeakHashMap;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.harmony.javax.security.auth.callback.Callback;
import org.apache.harmony.javax.security.auth.callback.PasswordCallback;

/**
 * This class allows creating SSLSockets from existing Sockets, with an associated
 * {@link ServerTrustManager}.  This allows verifying certificates and retrieving
 * details about certificate failures.  We also enable support for TLS compression
 * here, if available.
 * <p>
 * If configured as optional, TLS will be configured on the first call to
 * {@link #getSocketFactory} or {@link #isAvailable}.
 */
public class XMPPSSLSocketFactory {
    private ServerTrustManager trustManager;
    private boolean secureConnectionRequired;
    private SSLContext sslContext;

    private final ConnectionConfiguration config;
    private final String originalServiceName;
    private boolean isInitialized = false;

    /** If at least one insecure connection has been created with this factory,
     * return a CertificateExceptionDetail.  If all connections have been secure,
     * return null. */
    public CertificateException getSeenInsecureConnection() { return seenInsecureConnection; }
    private CertificateException seenInsecureConnection = null;

    /** Store information about each socket connection.  There's no good way to wrap
     *  an SSLSocket that another class gives to us, so we store these in a WeakHashMap. */
    private class SSLSocketInfo {
        // If the connection is insecure, this contains the exception explaining the
        // reason.
        CertificateException insecureConnection;

        // If compression is enabled, this contains the compression method used.  If compression
        // is not enabled, this is null.
        String compressionMethod;
    };
    public WeakHashMap<SSLSocket, SSLSocketInfo> map = new WeakHashMap<SSLSocket, SSLSocketInfo>();

    /**
     * Return an SSLSocket connected to the given socket.  This is equivalent
     * to {@link SSLSocketFactory#createSocket}(socket, host, port, true).
     */
    public SSLSocket attachSSLConnection(Socket s, String host, int port) throws IOException {
        initIfNeeded();
        if(sslContext == null)
            throw new IOException("TLS not available");

        SSLSocketFactory factory = sslContext.getSocketFactory();
        Socket socket = factory.createSocket(s, host, port, true);

        SSLSocket sslSocket = (SSLSocket) socket;

        SSLSocketInfo info = new SSLSocketInfo();
        map.put(sslSocket, info);

        sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            public void handshakeCompleted(HandshakeCompletedEvent event) {
                SSLSocket socket = (SSLSocket) event.getSocket();
                SSLSocketInfo info = map.get(socket);

                if(secureConnectionRequired) {
                    // If secureConnectionRequired is true then we've performed the certificate
                    // check in ServerTrustManager.checkServerTrusted; if it fails then the
                    // handshake will be aborted, so we'll never get here.
                    info.insecureConnection = null;
                } else {
                    try {
                        checkSecureConnection(socket);
                        info.insecureConnection = null;
                    } catch(CertificateException e) {
                        // The connection isn't secure.  Store the reason.
                        info.insecureConnection = e;
                        seenInsecureConnection = e;
                    }
                }

                info.compressionMethod = getCompressionMethod(socket);
            }
        });

        initCompression(sslSocket);

        return sslSocket;
    }

    /** Attempt to request compression on the given socket, if supported by the implementation.
     *  This is supported by org.apache.harmony.xnet.provider.jsse. */
    private static void initCompression(SSLSocket socket)
    {
        try {
            Method getSupportedCompressionMethods = socket.getClass().getMethod("getSupportedCompressionMethods");
            Method setEnabledCompressionMethods = socket.getClass().getMethod("setEnabledCompressionMethods", String[].class);

            String[] compressionMethods = (String[]) getSupportedCompressionMethods.invoke(socket);
            setEnabledCompressionMethods.invoke(socket, (Object) compressionMethods);
        } catch (Exception e) {
        }
    }

    /** Return the name of the compression method in use on a socket, or null if none is active. */
    private static String getCompressionMethod(SSLSocket socket) {
        try {
            SSLSession session = socket.getSession();
            Method getCompressionMethod = session.getClass().getMethod("getCompressionMethod");
            String compressionMethod = (String) getCompressionMethod.invoke(session);
            if(compressionMethod != null && compressionMethod.equals("NULL"))
                return null;
            else
                return compressionMethod;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verify the certificate for the given socket.  If the certificate isn't
     * trusted, throw {@link ServerTrustManager.CertificateExceptionDetail }.
     */
    private void checkSecureConnection(SSLSocket socket)
    throws CertificateException
    {
        SSLSession session = socket.getSession();

        Certificate[] certs;
        try {
            certs = session.getPeerCertificates();
        } catch(SSLPeerUnverifiedException e) {
            // If checkSecureConnection is called then getSecurityMode() != required, and
            // we've disabled certificate checks within ServiceTrustManager, so all certificates
            // are trusted at that level and this exception should never happen.
            throw new RuntimeException(e);
        }

        trustManager.checkCertificates(certs);
    }

    XMPPSSLSocketFactory(ConnectionConfiguration config, String originalServiceName)
    throws XMPPException
    {
        this.config = config;
        this.originalServiceName = originalServiceName;
        this.secureConnectionRequired = config.getSecurityMode() == ConnectionConfiguration.SecurityMode.required;

        // Initialize now if a secure connection is required, so if it's not
        // successful we'll fail immediately.
        if(secureConnectionRequired)
            init();
    }

    private void initIfNeeded() {
        try {
            init();
        } catch(XMPPException e) {
            // If secureConnectionRequired, then we should have initialized in the constructor
            // where we can throw the exception.
            if(secureConnectionRequired)
                throw new RuntimeException("Unexpected TLS error", e);
        }
    }

    /**
     * If we havn't yet attempted to initialize TLS, do so.
     *
     * @throws XMPPException if TLS initialization fails.
     */
    private void init() throws XMPPException {
        if(isInitialized)
            return;
        isInitialized = true;

        try {
            try {
                sslContext = SSLContext.getInstance("TLS");
            } catch (NoSuchAlgorithmException e) {
                throw new XMPPException("TLS not supported", e);
            }
        } catch(XMPPException e) {
            // The environment doesn't support TLS.  Clear socketFactory, and
            // isAvailable will return false.
            sslContext = null;
            e.printStackTrace();
            throw e;
        }

        trustManager = getServerTrustManager(sslContext, config, secureConnectionRequired, originalServiceName);
    }

    /** @return true if TLS is available. */
    public boolean isAvailable() {
        initIfNeeded();
        return sslContext != null;
    }

    /** Return true if the specified socket is over a secure connection. */
    public CertificateException isInsecureConnection(Socket socket) {
        return map.get(socket).insecureConnection;
    }

    /** Return the name of the compression in use on the specified socket, or null if no
     * compression is active. */
    public String getCompressionMethod(Socket socket) {
        return map.get(socket).compressionMethod;
    }

    private static KeyManager[] createKeyManagers(ConnectionConfiguration config)
    throws Exception
    {
        KeyStore ks = null;
        PasswordCallback pcb = null;
        KeyManager[] kms = null;

        if(config.getCallbackHandler() == null)
            return null;
        if(config.getKeystoreType().equals("NONE"))
            return null;

        if(config.getKeystoreType().equals("PKCS11")) {
            try {
                Constructor<?> c = Class.forName("sun.security.pkcs11.SunPKCS11").getConstructor(InputStream.class);
                String pkcs11Config = "name = SmartCard\nlibrary = "+config.getPKCS11Library();
                ByteArrayInputStream inputStream = new ByteArrayInputStream(pkcs11Config.getBytes());
                Provider p = (Provider)c.newInstance(inputStream);
                Security.addProvider(p);
                ks = KeyStore.getInstance("PKCS11",p);
                pcb = new PasswordCallback("PKCS11 Password: ",false);
                config.getCallbackHandler().handle(new Callback[]{pcb});
                ks.load(null,pcb.getPassword());
            }
            catch (Exception e) {
                ks = null;
                pcb = null;
            }
        }
        else if(config.getKeystoreType().equals("Apple")) {
            ks = KeyStore.getInstance("KeychainStore","Apple");
            ks.load(null, null);
            //pcb = new PasswordCallback("Apple Keychain",false);
            //pcb.setPassword(null);
        }
        else {
            ks = KeyStore.getInstance(config.getKeystoreType());
            try {
                pcb = new PasswordCallback("Keystore Password: ",false);
                config.getCallbackHandler().handle(new Callback[]{pcb});
                ks.load(new FileInputStream(config.getKeystorePath()), pcb.getPassword());
            }
            catch(Exception e) {
                ks = null;
                pcb = null;
            }
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        try {
            if(pcb == null) {
                kmf.init(ks,null);
            } else {
                kmf.init(ks,pcb.getPassword());
                pcb.clearPassword();
            }
            kms = kmf.getKeyManagers();
        } catch (NullPointerException npe) {
            kms = null;
        }
        return kms;
    }

    /**
     * Prepare a ServerTrustManager and initialize the given context with it.  Return
     * the created ServerTrustManager.
     */
    public static ServerTrustManager getServerTrustManager(SSLContext context, ConnectionConfiguration config,
            boolean secureConnectionRequired, String serviceName) throws XMPPException
    {
        try {
            KeyManager[] kms = createKeyManagers(config);

            // Verify certificate presented by the server
            ServerTrustManager trustManager = new ServerTrustManager(serviceName, config, secureConnectionRequired);
            TrustManager[] trustManagers = new TrustManager[]{trustManager};

            try {
                context.init(kms, trustManagers, new SecureRandom());
            } catch (KeyManagementException e) {
                throw new XMPPException(e);
            }

            return trustManager;
        } catch(RuntimeException e) {
            throw e; // don't catch unchecked exceptions below
        } catch(Exception e) {
            throw new XMPPException("Error creating keystore", e);
        }
    }
};
