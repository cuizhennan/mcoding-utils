package com.mogu.international.spider.util; import org.apache.commons.collections.MapUtils; import org.apache.commons.lang.math.NumberUtils; import org.apache.commons.lang3.StringUtils; import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mx on 16/10/10.
 */
public class HttpClientUtil {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtil.class.getCanonicalName());

    //验证response encoding
    private static final String check_response_encoding_regex = "<meta[^>]*(?:content|charset)=\"(?:text/html;\\s*)?([\\w-]*|charset=[\\w-]*)\"";

    static final int timeOut = 30 * 1000;

    //最大链接数
    static final int maxTotal = 500;
    //路由的基础链接数
    static final int maxPerRoute = 500;
    //目标主机的最大链接数
    static final int maxRoute = 100;


    //httpclient 请求实例
    private static CloseableHttpClient httpClient = null;

    private final static Object syncLock = new Object();

    public static void config(HttpRequestBase httpRequestBase, String... proxy) {
        RequestConfig requestConfig = null;
        if (proxy.length == 2) {
            String host = proxy[0];
            int port = Integer.valueOf(proxy[1]);

            requestConfig = RequestConfig.custom().setProxy(new HttpHost(host, port))
                    .setConnectionRequestTimeout(timeOut)
                    .setConnectTimeout(timeOut)
                    .setSocketTimeout(timeOut).build();
        } else {
            requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(timeOut)
                    .setConnectTimeout(timeOut)
                    .setSocketTimeout(timeOut).build();
        }

        httpRequestBase.setConfig(requestConfig);
    }

    public static CloseableHttpClient getHttpClient(String url) {
        String hostname = url.split("/")[2];
        int port = 80;
        if (hostname.contains(":")) {
            String[] arr = hostname.split(":");
            hostname = arr[0];
            port = Integer.parseInt(arr[1]);
        }

        if (httpClient == null) {
            synchronized (syncLock) {
                if (httpClient == null) {
                    httpClient = createHttpClient(maxTotal, maxPerRoute, maxRoute, hostname, port);
                }
            }
        }

        return httpClient;
    }

    public static CloseableHttpClient createHttpClient(int maxTotal, int maxPerRoute, int maxRoute, String hostname, int port) {
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainsf).register("https", sslsf).build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);

        //最大链接数
        cm.setMaxTotal(maxTotal);
        //路由的基础链接数
        cm.setDefaultMaxPerRoute(maxPerRoute);

        HttpHost httpHost = new HttpHost(hostname, port);

        //目标主机的最大链接数
        cm.setMaxPerRoute(new HttpRoute(httpHost), maxRoute);

        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                //重试5次之后放弃
                if (executionCount >= 5) {
                    return false;
                }

                //服务器丢掉了连接，那么就重试
                if (exception instanceof NoHttpResponseException) {
                    return true;
                }

                //不要重试SSL握手异常
                if (exception instanceof SSLHandshakeException) {
                    return false;
                }

                //超时
                if (exception instanceof InterruptedIOException) {
                    return false;
                }

                //目标服务器不可达
                if (exception instanceof UnknownHostException) {
                    return false;
                }

                //连接被拒绝
                if (exception instanceof ConnectTimeoutException) {
                    return false;
                }

                //SSL握手异常
                if (exception instanceof SSLException) {
                    return false;
                }

                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                // 如果请求是幂等的，就再次尝试
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }

                return false;
            }
        };

        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).setRetryHandler(httpRequestRetryHandler).build();
        return httpClient;
    }

    /**
     * 设置post提及参数
     *
     * @param httpPost
     * @param params
     */
    private static void setPostParams(HttpPost httpPost, Map<String, Object> params) {
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        Set<String> keySet = params.keySet();
        for (String key : keySet) {
            nvps.add(new BasicNameValuePair(key, params.get(key).toString()));
        }

        try {
            httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            logger.error("setPost Params Failed ", e);
        }
    }

    /**
     * POST Method
     *
     * @param url
     * @param params
     * @return
     * @throws IOException
     */
    public static String post(String url, Map<String, Object> params) throws IOException {
        return post(url, params, null);
    }

    /**
     * POST Method
     *
     * @param url
     * @param params
     * @param headerParams
     * @return
     * @throws IOException
     */
    public static String post(String url, Map<String, Object> params, Map<String, String> headerParams) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");
        config(httpPost);
        setPostParams(httpPost, params);
        if (null != headerParams) {
            setPostHeaders(httpPost, headerParams);
        }
        CloseableHttpResponse response = null;
        try {
            //cookie sets
            CookieStore cookieStore = new BasicCookieStore();
            HttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

            response = getHttpClient(url).execute(httpPost, localContext);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity, "utf-8");
            EntityUtils.consume(entity);

            logger.info("Http Single GET Method SUCCEED Real-Encoding: {} Default-Use:{} , {}", RegexUtil.regexMatch(check_response_encoding_regex, result), url);
            return result;
        } catch (Exception e) {
            logger.error("POST Request Failed {} ", url, e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static String postAndSetCookie(String url, Map<String, Object> params, Map<String, String> headerParams, CookieStore cookieStore) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");
        config(httpPost);
        setPostParams(httpPost, params);
        if (null != headerParams) {
            setPostHeaders(httpPost, headerParams);
        }
        CloseableHttpResponse response = null;
        try {
            //cookie sets
            HttpClientContext httpClientContext = new HttpClientContext();
            httpClientContext.setCookieStore(cookieStore);

            response = getHttpClient(url).execute(httpPost, httpClientContext);

            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity, "utf-8");
            EntityUtils.consume(entity);

            logger.info("Http Single GET Method SUCCEED Real-Encoding: {} Default-Use:{} , {}", RegexUtil.regexMatch(check_response_encoding_regex, result), url);
            return result;
        } catch (Exception e) {
            logger.error("POST Request Failed {} ", url, e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * @param url
     * @return
     */
    public static String get(String url) {
        return get(url, "UTF-8");
    }

    /**
     * @param url
     * @param encoding
     * @return
     */
    public static String get(String url, String encoding) {
        return get(url, null, encoding);
    }

    /**
     * @param url
     * @param headerParams
     * @return
     */
    public static String get(String url, Map<String, String> headerParams, String host, int port) {
        return get(url, headerParams, "UTF-8", host, port);
    }

    /**
     * @param url
     * @param headerParams
     * @return
     */
    public static String get(String url, Map<String, String> headerParams, String encoding, String host, int port) {
        return get(url, headerParams, encoding, host, port + "");
    }

    /**
     * @param url
     * @param encoding
     * @param proxy
     * @return
     */
    public static String get(String url, Map<String, String> headerParams, String encoding, String... proxy) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");

        if (null != headerParams && MapUtils.isNotEmpty(headerParams)) {
            setGetHeaders(httpGet, headerParams);
        }

        //set proxys
        if (proxy.length == 2) {
            String host = proxy[0];
            String port = proxy[1];
            if (StringUtils.isNotEmpty(host) && NumberUtils.isNumber(port)) {
                config(httpGet, host, port + "");
            } else config(httpGet);
        } else config(httpGet);

        CloseableHttpResponse response = null;
        try {
            //cookie sets
            CookieStore cookieStore = new BasicCookieStore();
            HttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

            response = getHttpClient(url).execute(httpGet, localContext);

            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity, encoding);
            EntityUtils.consume(entity);

            logger.info("Http Single GET Method SUCCEED Real-Encoding: {} Default-Use:{} , {}", RegexUtil.regexMatch(check_response_encoding_regex, result), encoding, url);
            return result;
        } catch (IOException e) {
            logger.error("GET Request Failed {} ", url, e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return null;
    }

    /**
     * 获取cookie
     *
     * @param url
     * @param headerParams
     * @param encoding
     * @param cookieStoreMap
     * @return
     */
    public static String getAndGetCookie(String url, Map<String, String> headerParams, String encoding, Map<String, CookieStore> cookieStoreMap) {
        return getAndGetCookie(url, headerParams, encoding, cookieStoreMap, null, null);
    }

    /**
     * 获取cookie 和设置代理
     *
     * @param url
     * @param headerParams
     * @param encoding
     * @param cookieStoreMap 获取cookie
     * @return
     */
    public static String getAndGetCookie(String url, Map<String, String> headerParams, String encoding, Map<String, CookieStore> cookieStoreMap, String hostname, Integer port) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");
        setGetHeaders(httpGet, headerParams);

        if (StringUtils.isNotEmpty(hostname) && null != port) {
            config(httpGet, hostname, port + "");
        } else config(httpGet);

        CloseableHttpResponse response = null;
        try {
            //cookie sets
            CookieStore cookieStore = new BasicCookieStore();

            HttpClientContext httpClientContext = new HttpClientContext();
            httpClientContext.setCookieStore(cookieStore);

            response = getHttpClient(url).execute(httpGet, httpClientContext);
            //存储cookie
            cookieStoreMap.put(url, cookieStore);

            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity, encoding);
            EntityUtils.consume(entity);

            logger.info("Http Single getAndGetCookie Method SUCCEED Real-Encoding: {} Default-Use:{} , {}", RegexUtil.regexMatch(check_response_encoding_regex, result), encoding, url);
            return result;
        } catch (IOException e) {
            logger.error("GET Request GET-Cookie Failed {} ", url, e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return null;
    }


    /**
     * get 设置Cookie
     *
     * @param url
     * @param headerParams
     * @param encoding
     * @param cookieStore
     * @return
     */
    public static String getAndSetCookie(String url, Map<String, String> headerParams, String encoding, CookieStore cookieStore) {
        return getAndSetCookie(url, headerParams, encoding, cookieStore, null, null);
    }

    /**
     * get 设置Cookie 和代理
     *
     * @param url
     * @param headerParams
     * @param encoding
     * @param cookieStore
     * @return
     */
    public static String getAndSetCookie(String url, Map<String, String> headerParams, String encoding, CookieStore cookieStore, String hostname, Integer port) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.71 Safari/537.36");
        setGetHeaders(httpGet, headerParams);

        if (StringUtils.isNotEmpty(hostname) && null != port) {
            config(httpGet, hostname, port + "");
        } else config(httpGet);

        CloseableHttpResponse response = null;
        try {
            //Set Cookies
            HttpClientContext httpClientContext = new HttpClientContext();
            httpClientContext.setCookieStore(cookieStore);

            response = getHttpClient(url).execute(httpGet, httpClientContext);

            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity, encoding);
            EntityUtils.consume(entity);

            logger.info("Http Single getAndSetCookie Method SUCCEED Real-Encoding: {} Default-Use:{} , {}", RegexUtil.regexMatch(check_response_encoding_regex, result), encoding, url);
            return result;
        } catch (IOException e) {
            logger.error("GET Request And Set-Cookie Failed {} ", url, e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    public static void setGetHeaders(HttpGet httpGet, Map<String, String> headerParams) {
        if (MapUtils.isNotEmpty(headerParams)) {
            for (String key : headerParams.keySet()) {
                httpGet.setHeader(key, headerParams.get(key));
            }
        }
    }

    public static void setPostHeaders(HttpPost httpPost, Map<String, String> headerParams) {
        if (MapUtils.isNotEmpty(headerParams)) {
            for (String key : headerParams.keySet()) {
                httpPost.setHeader(key, headerParams.get(key));
            }
        }
    }
}
