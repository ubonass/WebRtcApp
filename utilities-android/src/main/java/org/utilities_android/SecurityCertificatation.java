package org.utilities_android;

import android.support.annotation.Nullable;
import android.util.Log;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;


public class SecurityCertificatation {

    private static final String TAG = "SecurityCertificatation";
    //public static String kurento_app_server_cer = "kurento-app-server.cer";
    //public static String kurento_app_client_jks = "kurento-app-client.jks";
    public static String storeType = "JKS";

    @Nullable
    private static InputStream[] serverCertificates;
    @Nullable
    private static InputStream serverCertificate;
    @Nullable
    private static InputStream clientCertificate;
    @Nullable
    private static String clientPasswd;

    /**
     * @return
     */
    public static SSLSocketFactory getSslSocketFactory(InputStream s_certificate,
                                                       InputStream c_certificate,
                                                       String c_password) {

        serverCertificate = s_certificate;
        clientCertificate = c_certificate;
        clientPasswd = c_password;
        try {
            //创建一个证书库，并将证书导入证书库
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null); //双向验证时使用
            keystore.setCertificateEntry("kurento",
                    getCertificateByAssets(s_certificate));
            //实例化信任库
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.
                            getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keystore);
            TrustManager[] trustManagers =
                    trustManagerFactory.getTrustManagers();
            TrustManager trustManager =
                    new AppRtcTrustManager(chooseTrustManager(trustManagers));
            KeyManagerFactory keyManagerFactory = null;
            if (c_certificate != null && c_password != null) {
                //初始化keystore针对客户端的证书,如果需要双向验证
                /////////////////////////////////////////////////////////////
                KeyStore clientKeyStore =
                        KeyStore.getInstance(KeyStore.getDefaultType());
                clientKeyStore.load(c_certificate, c_password.toCharArray());

                keyManagerFactory =
                        KeyManagerFactory.
                                getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(clientKeyStore, c_password.toCharArray());
                /////////////////////////////////////////////////////////////////
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (c_certificate != null && c_password != null) {
                sslContext.init(keyManagerFactory.getKeyManagers(),
                        new TrustManager[]{trustManager}, new SecureRandom());
            } else {
                sslContext.init(null, trustManagers,
                        new SecureRandom());
            }
            //使用双向验证

            return sslContext.getSocketFactory();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static SSLSocketFactory getSslSocketFactory(
            InputStream[] s_certificates,
            InputStream c_certificate,
            String c_password) {
        serverCertificates = s_certificates;
        clientCertificate = c_certificate;
        clientPasswd = c_password;
        try {
            SSLSocketFactory sslSocketFactory = null;
            TrustManager[] trustManagers =
                    prepareTrustManagers(s_certificates);
            KeyManager[] keyManagers =
                    prepareKeyManager(c_certificate, c_password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager trustManager = null;
            if (trustManagers != null) {
                trustManager =
                        new AppRtcTrustManager(chooseTrustManager(trustManagers));
            } else {
                trustManager = new UnSafeTrustManager();
            }
            sslContext.init(keyManagers,
                    new TrustManager[]{trustManager}, new SecureRandom());
            sslSocketFactory = sslContext.getSocketFactory();
            return sslSocketFactory;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (KeyManagementException e) {
            throw new AssertionError(e);
        } catch (KeyStoreException e) {
            throw new AssertionError(e);
        }
    }


    private static class AppRtcTrustManager implements X509TrustManager {

        private X509TrustManager defaultTrustManager;

        private X509TrustManager localTrustManager;

        public AppRtcTrustManager(X509TrustManager localTrustManager)
                throws NoSuchAlgorithmException, KeyStoreException {
            TrustManagerFactory var4 = TrustManagerFactory.
                    getInstance(TrustManagerFactory.getDefaultAlgorithm());
            var4.init((KeyStore) null);
            defaultTrustManager =
                    chooseTrustManager(var4.getTrustManagers());
            this.localTrustManager = localTrustManager;
        }

        public AppRtcTrustManager() {

        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            Log.i(TAG, "checkClientTrusted....");
        }

        /**
         * 检查服务器返回的公钥是否为空
         *
         * @param chain
         * @param authType
         * @throws CertificateException
         */
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            Log.i(TAG, "checkServerTrusted....");
            //1.对服务端返回的证书做判空处理
            if (chain == null) {
                Log.e(TAG, "X509Certificate array is null");
                throw new IllegalArgumentException("checkServerTrusted: " +
                        "X509Certificate array is null");
            }
            if (!(chain.length > 0)) {
                Log.e(TAG, "X509Certificate is empty");
                throw new IllegalArgumentException("checkServerTrusted: " +
                        "X509Certificate is empty");
            }
            //2.校验加密算法种类（通常所说的 ECDHE 密钥交换默认都是指 ECDHE_RSA，
            // 使用 ECDHE 生成 DH 算法所需的公私钥，然后使用 RSA 算法进行签名，最后再计算得出对称密钥。）
            if (!(null != authType &&
                    authType.equalsIgnoreCase("ECDHE_RSA"))) {
                Log.e(TAG, "AuthType is not ECDHE_RSA");
                throw new CertificateException("checkServerTrusted: " +
                        "AuthType is not ECDHE_RSA");
            }

            //3.检查证书是否在有效期内
            for (X509Certificate cert : chain) {
                try {
                    //3.检查证书是否在有效期内
                    cert.checkValidity();
                    //4.校验服务端返回的证书和本地预留的证书的一致性
                    //, "kurento-app-server.cer"
                    if (serverCertificate != null) {
                        cert.verify(
                                getCertificateByAssets(serverCertificate)
                                        .getPublicKey());
                    } else {
                        throw new Exception("serverCertificate can not null");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "cert is not Validity");
                    e.printStackTrace();
                }
            }
            //4.校验公钥字符串是否相同
            /*RSAPublicKey pubkey = (RSAPublicKey) chain[0].getPublicKey();
            String encoded = new BigInteger(1,
                    pubkey.getEncoded()).toString(16);
            final boolean expected = openvidu_server_public_key
                    .equalsIgnoreCase(encoded);
            if (!expected) {
                throw new CertificateException("checkServerTrusted: " +
                        "Expected public key: " + openvidu_server_public_key + "," +
                        " got public key:" + encoded);
            }*/

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            Log.i(TAG, "getAcceptedIssuers.....");
            return new X509Certificate[0];
        }
    }


    private static class UnSafeTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }

    private static X509TrustManager chooseTrustManager(
            TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        return null;
    }

    /**
     * 准备服务器信任的客户端的证书
     *
     * @param //bksFile或者jks文件
     * @param password：      客户端证书的密码 prepareKeyManager(
     *                       conntext.getResources().getAssets().open(file),
     *                       "123456")
     * @return
     */
    private static KeyManager[] prepareKeyManager(InputStream client,
                                                  String password) {
        try {
            if (client == null || password == null) {
                return null;
            }
            // Android默认的是BKS格式的证书jks或者bks文件
            KeyStore clientKeyStore =
                    KeyStore.getInstance(KeyStore.getDefaultType());
            clientKeyStore.load(client, password.toCharArray());

            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(
                            KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(clientKeyStore, password.toCharArray());
            return keyManagerFactory.getKeyManagers();

        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 准备所信任的服务器证书
     * 可能为多个
     *
     * @param certificates：new InputStream[]{new inputstream}
     *                         new InputStream[] {conntext.getResources().getAssets().open(file1),
     *                         conntext.getResources().getAssets().open(file2),
     *                         conntext.getResources().getAssets().open(file3)}
     * @return
     */
    private static TrustManager[] prepareTrustManagers(
            InputStream... certificates/*server cer file*/) {
        if (certificates == null ||
                certificates.length <= 0) {
            return null;
        }
        try {
            CertificateFactory certificateFactory =
                    CertificateFactory.getInstance("X.509");

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);//双向验证时使用
            int index = 0;
            for (InputStream inputstream : certificates) {
                String certificateAlias = Integer.toString(index++);
                keyStore.setCertificateEntry(certificateAlias,
                        certificateFactory.generateCertificate(inputstream));
                try {
                    if (inputstream != null) {
                        inputstream.close();
                    }
                } catch (IOException e) {
                }
            }
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            TrustManager[] trustManagers =
                    trustManagerFactory.getTrustManagers();
            return trustManagers;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //下面两种获取证书的方法选一种即可
    //将公钥文件放在工程的assets文件夹中的时候，获取证书

    /**
     * @param //name
     * @return
     */
    private static X509Certificate getCertificateByAssets(InputStream certificate) {
        if (null == certificate) return null;
        X509Certificate serverCert = null;
        try {
            CertificateFactory certificateFactory =
                    CertificateFactory.getInstance("X.509");
            serverCert = (X509Certificate)
                    certificateFactory.generateCertificate(certificate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serverCert;
    }

    /**
     * @param cert:将公钥信息以字符串的形式硬编码在类中的时候，获取证书
     * @return
     */
    private static X509Certificate getCertificateByString(String cert) {
        X509Certificate serverCert = null;
        try {
            InputStream certificate =
                    new ByteArrayInputStream(cert.getBytes());
            CertificateFactory certificateFactory =
                    CertificateFactory.getInstance("X.509");
            serverCert = (X509Certificate)
                    certificateFactory.generateCertificate(certificate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serverCert;
    }
}
