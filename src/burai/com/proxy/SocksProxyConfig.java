/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.com.proxy;

/**
 * SOCKS5 proxy configuration for SSH connections.
 * 
 * NanoLabo v2.9.3+ supports:
 * - SOCKS proxy for SSH connections
 * - PAC file proxy for built-in web browser
 * - Proxy certificate verification handling
 * 
 * This is essential for users behind corporate firewalls.
 */
public class SocksProxy {

    private boolean socksEnabled;
    private String proxyHost;
    private int proxyPort;
    private String proxyUser;
    private String proxyPassword;

    public SocksProxy() {
        this.socksEnabled = false;
        this.proxyHost = "";
        this.proxyPort = 1080;
        this.proxyUser = "";
        this.proxyPassword = "";
    }

    public void setEnabled(boolean enabled) { this.socksEnabled = enabled; }
    public boolean isEnabled() { return this.socksEnabled; }

    public void setProxyHost(String host) { this.proxyHost = host; }
    public String getProxyHost() { return this.proxyHost; }

    public void setProxyPort(int port) { this.proxyPort = port; }
    public int getProxyPort() { return this.proxyPort; }

    public void setProxyUser(String user) { this.proxyUser = user; }
    public String getProxyUser() { return this.proxyUser; }

    public void setProxyPassword(String pass) { this.proxyPassword = pass; }
    public String getProxyPassword() { return this.proxyPassword; }

    /**
     * Apply SOCKS proxy settings to JVM
     */
    public void applyToSystem() {
        if (this.socksEnabled && this.proxyHost != null && !this.proxyHost.isEmpty()) {
            System.setProperty("socksProxyHost", this.proxyHost);
            System.setProperty("socksProxyPort", String.valueOf(this.proxyPort));
            if (this.proxyUser != null && !this.proxyUser.isEmpty()) {
                System.setProperty("java.net.socks.username", this.proxyUser);
                System.setProperty("java.net.socks.password", this.proxyPassword);
            }
        } else {
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
        }
    }
}
