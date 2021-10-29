package Logger;

import io.resurface.*;
import org.json.*;


public class HttpLoggerForAWSKinesis {
    private final HttpLogger logger;
    private HttpServletRequestImpl request;
    private HttpServletResponseImpl response;
    private String request_body = "";
    private String response_body = "";
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

        JSONObject jsonData = new JSONObject(data);

        if (jsonData.get("messageType").equals("DATA_MESSAGE")) {
            long start = 0;
            JSONArray logEvents = jsonData.getJSONArray("logEvents");
            for (int i = 0; i < logEvents.length(); i++) {
                JSONObject logEvent = logEvents.getJSONObject(i);
                String message = logEvent.getString("message");
                message = message.substring(message.indexOf(':') + 2);
                switch (i) {
                    case 0:
                        // HTTP Method
                        request.setMethod(message.substring(0, message.indexOf(",")));
                        start = logEvent.getLong("timestamp");
                        break;
                    case 2:
                        // Query String
                        StringBuilder qsBuilder = new StringBuilder("");
                        for (String param : getPairs(message)) {
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
                        break;
                    case 3:
                        // Request Headers, Method
                    case 6:
                        // Request Headers, Endpoint
                        for (String header : getPairs(message)) {
                            int sepIdx = header.indexOf('=');
                            if (sepIdx >= 0) {
                                String val = header.length() == sepIdx + 1 ? "" : header.substring(sepIdx + 1);
                                this.request.addHeader(header.substring(0, sepIdx), val);
                            }
                        }
                        break;
//                    case 4:
//                        // Request Body, Method
//                        this.request_body = message;
//                        break;
                    case 5:
                        // Request URL
                        request.setRequestURL(message);
                        break;
                    case 7:
                        // Request Body, Endpoint
                        this.request_body = message;
                        break;
                    case 8:
                        // Response status
                        this.response.setStatus(new Integer(message.substring(0, message.indexOf(","))));
                        // Now and Interval
                        this.now = logEvent.getLong("timestamp");
                        this.interval = start == 0 ? 0 : now - start;
                    case 9:
                        // Response Headers, Endpoint
                    case 12:
                        // Response Headers, Method
                        for (String header : getPairs(message)) {
                            int sepIdx = header.indexOf('=');
                            if (sepIdx >= 0) {
                                String val = header.length() == sepIdx + 1 ? "" : header.substring(sepIdx + 1);
                                this.response.addHeader(header.substring(0, sepIdx), val);
                            }
                        }
                        break;
                    case 10:
                        // Response Body, Endpoint
                        this.response_body = message;
                        break;
//                    case 11:
//                        // Response Body, Method
//                        this.response_body = message;
//                        break;
                }
            }
        }
    }

    public void send(String data) {
        try {
            parseHttp(data);
            HttpMessage.send(logger, request, response, response_body, request_body, now, interval);
            System.out.println("Message sent");
        } catch (JSONException e) {
            System.err.printf("Message not sent due to parsing issue: %s\n", e.getMessage());
                }
    }

    public boolean isEnabled() {
        return logger.isEnabled();
    }

    private String[] getPairs(String message) {
        int end = message.endsWith("[TRUNCATED]") ? 11 : 1;
        return message.substring(1, message.length() - end).split(", +(?=[^\\\"\\s]+(?==)|$)");
    }
}