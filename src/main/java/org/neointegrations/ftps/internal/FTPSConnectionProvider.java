package org.neointegrations.ftps.internal;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.*;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.connection.PoolingConnectionProvider;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Password;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.neointegrations.ftps.internal.client.MuleFTPSClient;
import org.neointegrations.ftps.internal.util.FTPSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


public class FTPSConnectionProvider implements PoolingConnectionProvider<FTPSConnection> {

    private final Logger _logger = LoggerFactory.getLogger(FTPSConnectionProvider.class);

    @Parameter
    private String user;

    @Password
    @Parameter
    private String password;

    @Parameter
    private String host;

    @Optional(defaultValue = "21")
    @Parameter
    private int port;

    @Optional(defaultValue = "60")
    @Parameter
    private int timeout;

    @Optional(defaultValue = "3600")
    @Parameter
    private int socketTimeout;

    @Optional(defaultValue = "#[1024 * 1024]")
    @DisplayName("Buffer size (in bytes)")
    @Parameter
    public int bufferSizeInBytes;

    @Optional(defaultValue = "false")
    @DisplayName("Show FTP commands")
    @Parameter
    public boolean debugFtpCommand;

    @Optional(defaultValue = "true")
    @DisplayName("TLSv1.2 Only")
    @Parameter
    public boolean tlsV12Only;

    @Optional(defaultValue = "true")
    @DisplayName("Certificate validation")
    @Parameter
    public boolean remoteVerificationEnable;

    @Optional(defaultValue = "true")
    @DisplayName("SSL session Reuse required by the FTPS server?")
    @Parameter
    private boolean sslSessionReuse;

    @Optional(defaultValue = "Europe/London")
    @Summary("You can find all the time zones here - https://en.wikipedia.org/wiki/List_of_tz_database_time_zones")
    @DisplayName("Time zone of the server.")
    @Parameter
    private String serverTimeZone;

    private SSLContext _sslContext = null;

    public FTPSConnectionProvider() throws ConnectionException {
        super();
        // To resolve [NET-408 Issue](https://issues.apache.org/jira/browse/NET-408), below property is needed
        // to share SSL session with the data connection
        System.setProperty("jdk.tls.useExtendedMasterSecret", "false");
        _sslContext = init();

    }

    @Override
    public FTPSConnection connect() throws ConnectionException {

        if (_logger.isDebugEnabled()) _logger.debug("Connection starting...");
        final MuleFTPSClient _client = new MuleFTPSClient(false, _sslContext,
                sslSessionReuse);
        connectionInit(_client);
        return new FTPSConnection(this, _client);
    }

    @Override
    public void disconnect(FTPSConnection connection) {
        if (_logger.isDebugEnabled()) _logger.debug("Disconnecting...");
        if (connection == null || connection.ftpsClient() == null) return;
        FTPSUtil.logoutQuietly(connection.ftpsClient());
        if (_logger.isDebugEnabled()) _logger.debug("After connection.ftpsClient().logout()");
        FTPSUtil.disconnectQuietly(connection.ftpsClient());
        if (_logger.isDebugEnabled()) _logger.debug("Disconnected ");
    }

    @Override
    public ConnectionValidationResult validate(final FTPSConnection connection) {
        if (_logger.isDebugEnabled()) _logger.debug("Validating connection...");
        if (connection.isConnected()) {
            return ConnectionValidationResult.success();
        } else {
            return ConnectionValidationResult.failure("Connection is closed",
                    new ConnectionException("Connection is closed"));
        }
    }

    public void reconnect(final FTPSConnection connection) throws IOException, ConnectionException {
        if (_logger.isDebugEnabled()) _logger.debug("Connection re-starting...");
        if (connection.isConnected()) this.disconnect(connection);
        connectionInit(connection.ftpsClient());
        if (_logger.isDebugEnabled()) _logger.debug("Connection restarted");
    }

    private SSLContext init() throws ConnectionException {
        if (_logger.isDebugEnabled()) _logger.debug("Creating trust manager...");
        SSLContext sslContext = null;
        try {
            TrustManager[] trustManager = null;
            if (!remoteVerificationEnable) {
                trustManager = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }

                            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                            }

                            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                            }
                        }
                };
            } else {
                TrustManagerFactory tmf = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm());

                // Using null here initialises the TMF with the default trust store.
                tmf.init((KeyStore) null);

                // Get hold of the default trust manager
                X509TrustManager x509Tm = null;
                for (TrustManager tm : tmf.getTrustManagers()) {
                    if (tm instanceof X509TrustManager) {
                        x509Tm = (X509TrustManager) tm;
                        break;
                    }
                }

                // Wrap it in your own class.
                final X509TrustManager finalTm = x509Tm;
                X509TrustManager customTm = new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return finalTm.getAcceptedIssuers();
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException {
                        finalTm.checkServerTrusted(chain, authType);
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain,
                                                   String authType) throws CertificateException, CertificateException {
                        finalTm.checkClientTrusted(chain, authType);
                    }
                };
                trustManager = new TrustManager[]{customTm};
            }

            if (tlsV12Only) sslContext = SSLContext.getInstance("TLSv1.2");
            else sslContext = SSLContext.getInstance("TLS");

            sslContext.init(null, trustManager, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            _logger.error("Unable to create SSL Context. NoSuchAlgorithmException: ", e.getMessage(), e);
            throw new ConnectionException(e);
        } catch (KeyManagementException e) {
            _logger.error("Unable to create SSL Context. KeyManagementException: ", e.getMessage(), e);
            throw new ConnectionException(e);
        }

        if (_logger.isDebugEnabled()) _logger.debug("Trust Manager created");
        return sslContext;
    }

    private void connectionInit(final MuleFTPSClient _client) throws ConnectionException{
        try {
            if (debugFtpCommand) {
                _client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out),
                        true));
            }
            _client.setRemoteVerificationEnabled(remoteVerificationEnable);

            final FTPClientConfig config = new FTPClientConfig();
            config.setServerTimeZoneId(serverTimeZone);
            _client.configure(config);

            // Timeout settings
            _client.setDefaultTimeout(timeout);
            _client.setConnectTimeout(timeout);

            _client.connect(host, port);

            _client.setSoTimeout(socketTimeout);
            _client.login(user, password);
            _client.enterLocalPassiveMode();

            if (_logger.isDebugEnabled()) _logger.debug("Connected to {}", host);
            if (_logger.isDebugEnabled()) _logger.debug("Reply: {}", _client.getReplyString());
            int reply = _client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                _client.disconnect();
                _logger.error("FTP server refused connection with reply {}", reply);
                throw new ConnectionException("FTP server refused connection");
            }
            _client.execPROT("P");
            _client.setFileType(FTP.BINARY_FILE_TYPE);
            _client.setBufferSize(bufferSizeInBytes);
            _client.enterLocalPassiveMode();
            if (_logger.isDebugEnabled()) _logger.debug("Connection started");
        } catch (IOException e) {
            _logger.error("FTPS server refused connection", e);
            FTPSUtil.logoutQuietly(_client);
            try {
                _client.disconnect();
            } catch (Exception exp) {
                throw new RuntimeException(exp);
            }
            throw new ConnectionException(e);
        }

    }
}
