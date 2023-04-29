package com.itranswarp.jerrymouse.engine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import com.itranswarp.jerrymouse.Config;
import com.itranswarp.jerrymouse.connector.HttpExchangeRequest;
import com.itranswarp.jerrymouse.engine.support.Attributes;
import com.itranswarp.jerrymouse.engine.support.HttpHeaders;
import com.itranswarp.jerrymouse.engine.support.Parameters;
import com.itranswarp.jerrymouse.utils.HttpUtils;

public class HttpServletRequestImpl implements HttpServletRequest {

    final Config config;
    final ServletContext servletContext;
    final HttpExchangeRequest exchangeRequest;
    final String method;
    final HttpHeaders headers;
    final Parameters parameters;

    String characterEncoding;
    int contentLength = 0;

    String requestId = null;
    Attributes attributes = new Attributes();

    Boolean inputCalled = null;

    public HttpServletRequestImpl(Config config, ServletContext servletContext, HttpExchangeRequest exchangeRequest) {
        this.config = config;
        this.servletContext = servletContext;
        this.exchangeRequest = exchangeRequest;

        this.characterEncoding = config.server.requestEncoding;
        this.method = exchangeRequest.getRequestMethod();
        this.headers = new HttpHeaders(exchangeRequest.getRequestHeaders());
        this.parameters = new Parameters(exchangeRequest, this.characterEncoding);
        if ("POST".equals(this.method) || "PUT".equals(this.method) || "DELETE".equals(this.method) || "PATCH".equals(this.method)) {
            this.contentLength = getIntHeader("Content-Length");
        }
    }

    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.characterEncoding = env;
        this.parameters.setCharset(env);
    }

    @Override
    public int getContentLength() {
        return this.contentLength;
    }

    @Override
    public long getContentLengthLong() {
        return this.contentLength;
    }

    @Override
    public String getContentType() {
        return getHeader("Content-Type");
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (this.inputCalled == null) {
            this.inputCalled = Boolean.TRUE;
            return new ServletInputStreamImpl(this.exchangeRequest.getRequestBody());
        }
        throw new IllegalStateException("Cannot reopen input stream after " + (this.inputCalled ? "getInputStream()" : "getReader()") + " was called.");
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (this.inputCalled == null) {
            this.inputCalled = Boolean.FALSE;
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.exchangeRequest.getRequestBody()), this.characterEncoding));
        }
        throw new IllegalStateException("Cannot reopen input stream after " + (this.inputCalled ? "getInputStream()" : "getReader()") + " was called.");
    }

    @Override
    public String getParameter(String name) {
        return this.parameters.getParameter(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return this.parameters.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        return this.parameters.getParameterValues(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return this.parameters.getParameterMap();
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public String getScheme() {
        String header = "http";
        String forwarded = config.server.forwardedHeaders.forwardedProto;
        if (!forwarded.isEmpty()) {
            String forwardedHeader = getHeader(forwarded);
            if (forwardedHeader != null) {
                header = forwardedHeader;
            }
        }
        return header;
    }

    @Override
    public String getServerName() {
        String header = getHeader("Host");
        String forwarded = config.server.forwardedHeaders.forwardedHost;
        if (!forwarded.isEmpty()) {
            String forwardedHeader = getHeader(forwarded);
            if (forwardedHeader != null) {
                header = forwardedHeader;
            }
        }
        if (header == null) {
            InetSocketAddress address = this.exchangeRequest.getLocalAddress();
            header = address.getHostString();
        }
        return header;
    }

    @Override
    public int getServerPort() {
        InetSocketAddress address = this.exchangeRequest.getLocalAddress();
        return address.getPort();
    }

    @Override
    public Locale getLocale() {
        String langs = getHeader("Accept-Language");
        if (langs == null) {
            return HttpUtils.DEFAULT_LOCALE;
        }
        return HttpUtils.parseLocales(langs).get(0);
    }

    @Override
    public Enumeration<Locale> getLocales() {
        String langs = getHeader("Accept-Language");
        if (langs == null) {
            return Collections.enumeration(HttpUtils.DEFAULT_LOCALES);
        }
        return Collections.enumeration(HttpUtils.parseLocales(langs));
    }

    @Override
    public boolean isSecure() {
        return "https".equals(getScheme().toLowerCase());
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        // do not support request dispatcher:
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        return this.servletContext;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String getRequestId() {
        if (this.requestId == null) {
            this.requestId = UUID.randomUUID().toString();
        }
        return this.requestId;
    }

    @Override
    public String getProtocolRequestId() {
        // empty string for http 1.x:
        return "";
    }

    @Override
    public ServletConnection getServletConnection() {
        throw new UnsupportedOperationException("getServletConnection");
    }

    @Override
    public String getAuthType() {
        // not support auth:
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public String getPathInfo() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getPathTranslated() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getContextPath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getQueryString() {
        return this.exchangeRequest.getRequestURI().getRawQuery();
    }

    @Override
    public String getRemoteUser() {
        // not support auth:
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        // not support auth:
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        // not support auth:
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getRequestURI() {
        return this.exchangeRequest.getRequestURI().getPath();
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer sb = new StringBuffer(128);
        sb.append(getScheme()).append("://").append(getServerName()).append(':').append(getServerPort()).append(getRequestURI());
        return sb;
    }

    @Override
    public String getServletPath() {
        return getRequestURI();
    }

    @Override
    public HttpSession getSession(boolean create) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HttpSession getSession() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String changeSessionId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        // not support auth:
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        // not support auth:
    }

    @Override
    public void logout() throws ServletException {
        // not support auth:
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        // not suport multipart:
        return List.of();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        // not suport multipart:
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        // not suport websocket:
        return null;
    }

    // header operations //////////////////////////////////////////////////////

    @Override
    public long getDateHeader(String name) {
        return this.headers.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return this.headers.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return Collections.enumeration(this.headers.getHeaders(name));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(this.headers.getHeaderNames());
    }

    @Override
    public int getIntHeader(String name) {
        return this.headers.getIntHeader(name);
    }

    // attribute operations ///////////////////////////////////////////////////

    @Override
    public Object getAttribute(String name) {
        return this.attributes.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return this.attributes.getAttributeNames();
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name);
        } else {
            this.attributes.setAttribute(name, value);
        }
    }

    @Override
    public void removeAttribute(String name) {
        this.attributes.removeAttribute(name);
    }

    // address and port ///////////////////////////////////////////////////////

    @Override
    public String getRemoteAddr() {
        String addr = null;
        String forwarded = config.server.forwardedHeaders.forwardedFor;
        if (!forwarded.isEmpty()) {
            String forwardedHeader = getHeader(forwarded);
            if (forwardedHeader != null) {
                int n = forwardedHeader.indexOf(',');
                addr = n < 0 ? forwardedHeader : forwardedHeader.substring(n);
            }
        }
        if (addr == null) {
            InetSocketAddress address = this.exchangeRequest.getRemoteAddress();
            addr = address.getHostString();
        }
        return addr;
    }

    @Override
    public String getRemoteHost() {
        // avoid DNS lookup by IP:
        return getRemoteAddr();
    }

    @Override
    public int getRemotePort() {
        InetSocketAddress address = this.exchangeRequest.getRemoteAddress();
        return address.getPort();
    }

    @Override
    public String getLocalAddr() {
        InetSocketAddress address = this.exchangeRequest.getLocalAddress();
        return address.getHostString();
    }

    @Override
    public String getLocalName() {
        // avoid DNS lookup:
        return getLocalAddr();
    }

    @Override
    public int getLocalPort() {
        InetSocketAddress address = this.exchangeRequest.getLocalAddress();
        return address.getPort();
    }
}