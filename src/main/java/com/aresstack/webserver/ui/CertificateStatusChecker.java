package com.aresstack.webserver.ui;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

/**
 * Liest die Zertifikatsdaten einer Site per lokalem TLS-Handshake mit SNI.
 * Es werden keine Nutzdaten übertragen; die Zertifikatskette wird bewusst
 * nicht validiert, weil nur ihre Metadaten angezeigt werden sollen.
 */
public final class CertificateStatusChecker {

    public record CertificateInfo(Date issued, Date expires, String issuer) {

        public boolean isExpired() {
            return expires.before(new Date());
        }
    }

    private CertificateStatusChecker() {
    }

    public static CertificateInfo fetch(String host, int httpsPort) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{TRUST_ALL}, null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", httpsPort), 3000);
            socket.setSoTimeout(3000);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setServerNames(List.of(new SNIHostName(host)));
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            X509Certificate certificate =
                    (X509Certificate) socket.getSession().getPeerCertificates()[0];
            return new CertificateInfo(
                    certificate.getNotBefore(),
                    certificate.getNotAfter(),
                    certificate.getIssuerX500Principal().getName());
        }
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
