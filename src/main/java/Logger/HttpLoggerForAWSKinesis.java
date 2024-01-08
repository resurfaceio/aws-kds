// © 2016-2024 Graylog, Inc.

package Logger;

import io.resurface.*;
import org.json.*;


public class HttpLoggerForAWSKinesis {
    private final String SCOPE = System.getenv("KDS_EVENTS_SCOPE") == null ?
            "APP" : System.getenv("KDS_EVENTS_SCOPE").toUpperCase();
    private final HttpLogger logger;
    private HttpServletRequestImpl request;
    private HttpServletResponseImpl response;
    private String request_body;
    private String response_body;
    private Long now, interval;

    public HttpLoggerForAWSKinesis() {
        logger = new HttpLogger();
    }

    public HttpLoggerForAWSKinesis(String url, String rules) {
        logger = new HttpLogger(url, rules);
    }

    public HttpLoggerForAWSKinesis(String url, Boolean enabled, String rules) {
        logger = new HttpLogger(url, enabled, rules);
    }

    private void parseHttp(String data) throws JSONException {
        request = new HttpServletRequestImpl();
        response = new HttpServletResponseImpl();
        request_body = "";
        response_body = "";

        JSONObject jsonData = new JSONObject(data);

        if (jsonData.get("messageType").equals("DATA_MESSAGE")) {
            long start = 0;
            String path = "/";
            JSONArray logEvents = jsonData.getJSONArray("logEvents");
            for (int i = 0; i < logEvents.length(); i++) {
                JSONObject logEvent = logEvents.getJSONObject(i);
                String message = logEvent.getString("message");
                String messageKey = message.substring(message.indexOf(' ') + 1, message.indexOf(':'));
                message = message.substring(message.indexOf(':') + 2);
                if ("HTTP Method".equals(messageKey)) {
                    request.setMethod(message.substring(0, message.indexOf(',')));
                    start = logEvent.getLong("timestamp");
                }
                if ("Method request headers".equals(messageKey)) {
                    int headerIdx = message.indexOf("X-Forwarded-For");
                    if (headerIdx >= 0) {
                        int commaIdx = message.indexOf(',', headerIdx);
                        String[] header = message.substring(headerIdx, commaIdx).split("=");
                        if (header.length > 1) this.request.addHeader(header[0], header[1]);
                    }
                }
                if ("GATEWAY".equals(SCOPE) || "ALL".equals(SCOPE)) {
                    switch (messageKey) {
                        case "HTTP Method":
                            if ("GATEWAY".equals(SCOPE)) {
                                int pathIndex = message.indexOf(',') + 2;
                                String pathKey = message.substring(pathIndex, message.indexOf(':', pathIndex));
                                if (pathKey.equals("Resource Path")) {
                                    path = message.substring(message.indexOf(':', pathIndex) + 2);
                                }
                            }
                            break;
                        case "Method request query string":
                            // Query String as received by API Gateway
                            setParams(message);
                            break;
                        case "Method request headers":
                            // Request Headers as received by API Gateway
                            setRequestHeaders(message);
                            // Reconstruct Gateway URL from request headers
                            StringBuilder reqURL = new StringBuilder()
                                    .append(this.request.getHeader("X-Forwarded-Proto")).append("://")
                                    .append(this.request.getHeader("Host"))
                                    .append(path);
                            this.request.setRequestURL(reqURL.toString());
                            break;
                        case "Method request body before transformations":
                            // Request Body as received by API Gateway
                            if ("GATEWAY".equals(SCOPE)) {
                                this.request_body = message;
                            }
                            if ("ALL".equals(SCOPE) && !message.isEmpty()) {
                                this.request_body += String.format(",{\"Method request body\": %s}", message);
                            }
                            break;
                        case "Method response headers":
                            // Response Headers as returned by API Gateway
                            setResponseHeaders(message);
                            break;
                        case "Method response body after transformations":
                            // Response Body as returned by API Gateway
                            if ("GATEWAY".equals(SCOPE)) {
                                this.response_body = message;
                            }
                            if ("ALL".equals(SCOPE) && !message.isEmpty()) {
                                this.response_body += String.format(",{\"Method response body\": %s}", message);
                            }
                            break;
                        case "Method completed with status":
                            // Response status returned by API Gateway
                            if ("GATEWAY".equals(SCOPE)) {
                                this.response.setStatus(new Integer(message));
                            }
                            this.now = logEvent.getLong("timestamp");
                            this.interval = start == 0 ? 0 : now - start;
                            break;
                    }

                }
                if ("APP".equals(SCOPE) || "ALL".equals(SCOPE)) {
                    switch (messageKey) {
                        case "Endpoint request headers":
                            // Request Headers as received by upstream application
                            setRequestHeaders(message);
                            break;
                        case "Endpoint request URI":
                            String[] urlAndParams = message.split("\\?");
                            if (urlAndParams.length > 0) {
                                // Upstream request URL
                                request.setRequestURL(urlAndParams[0]);
                                if (urlAndParams.length > 1) {
                                    // Query string request params
                                    setParams(urlAndParams[1], false);
                                }
                            }
                            break;
                        case "Endpoint request body after transformations":
                            // Request Body as received by upstream application
                            if ("APP".equals(SCOPE)) {
                                this.request_body = message;
                            }
                            if ("ALL".equals(SCOPE) && !message.isEmpty()) {
                                this.request_body += String.format(",{\"Endpoint request body\": %s}", message);
                            }
                            break;
                        case "Received response. Status":
                            // Response status returned by upstream application
                            this.response.setStatus(new Integer(message.substring(0, message.indexOf(','))));
                            int latencyIndex = message.indexOf(':') + 2;
                            String latencyKey = message.substring(message.indexOf(',') + 2, latencyIndex - 2);
                            String latency = message.substring(latencyIndex, message.indexOf(' ', latencyIndex));
                            // Now and Interval
                            this.now = logEvent.getLong("timestamp");
                            if (latencyKey.equals("Integration latency")) {
                                this.interval = new Long(latency);
                            } else {
                                this.interval = start == 0 ? 0 : now - start;
                            }
                            break;
                        case "Endpoint response headers":
                            // Response Headers as returned by upstream application
                            setResponseHeaders(message);
                            break;
                        case "Endpoint response body before transformations":
                            // Response Body as returned by upstream application
                            if ("APP".equals(SCOPE)) {
                                this.response_body = message;
                            }
                            if ("ALL".equals(SCOPE) && !message.isEmpty()) {
                                this.response_body += String.format(",{\"Endpoint response body\": %s}", message);
                            }
                            break;
                    }
                }
            }
            if ("ALL".equals(SCOPE)) {
                if (request_body != null && !request_body.isEmpty()) {
                    request_body = String.format("[%s]", request_body.substring(1));
                }
                if (response_body != null && !response_body.isEmpty()) {
                    response_body = String.format("[%s]", response_body.substring(1));
                }
            }
        }
    }

    public void send(String data) {
        try {
            parseHttp(data);
            if (now == null) now = System.currentTimeMillis();
            if (interval == null) interval = 0L;
            HttpMessage.send(logger, request, response, response_body, request_body, now, interval);
            System.out.printf("Messages sent: %d%n", logger.getSubmitSuccesses());
        } catch (JSONException e) {
            System.err.printf("Message not sent due to parsing issue: %s\n", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return logger.isEnabled();
    }

    private void setRequestHeaders(String message) {
        for (String header : getPairs(message)) {
            int sepIdx = header.indexOf('=');
            if (sepIdx >= 0) {
                String val = header.length() == sepIdx + 1 ? "" : header.substring(sepIdx + 1);
                this.request.addHeader(header.substring(0, sepIdx), val);
            }
        }
    }

    private void setResponseHeaders(String message) {
        for (String header : getPairs(message)) {
            int sepIdx = header.indexOf('=');
            if (sepIdx >= 0) {
                String val = header.length() == sepIdx + 1 ? "" : header.substring(sepIdx + 1);
                this.response.addHeader(header.substring(0, sepIdx), val);
            }
        }
    }

    private void setParams(String message) {
        this.setParams(message, true);
    }

    private void setParams(String message, boolean enclosed) {
        StringBuilder qsBuilder = new StringBuilder("");
        for (String param : enclosed ? getPairs(message) : getPairs(message, 0, 0)) {
            int sepIdx = param.indexOf('=');
            if (sepIdx >= 0) {
                String key = param.substring(0, sepIdx);
                String val = param.length() == sepIdx + 1 ? "" : param.substring(sepIdx + 1);
                this.request.addParam(key, val);
                qsBuilder.append(val.isEmpty() ? key + "&" : key + "=" + val + "&");
            }
        }
        String qs = qsBuilder.toString();
        if (!qs.isEmpty()) {
            request.setQueryString(qs.substring(0, qs.length() - 1));
        }
    }

    private String[] getPairs(String message) {
        return this.getPairs(message, 1, message.length() - 1);
    }

    private String[] getPairs(String message, int startIndex, int endIndex) {
        endIndex -= message.endsWith("[TRUNCATED]") ? 10 : 0;
        return message.substring(startIndex, endIndex).split(", +(?=[^\\\"\\s]+(?==)|$)");
    }
}