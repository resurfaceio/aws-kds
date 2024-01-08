// © 2016-2024 Graylog, Inc.

package KinesisDataStreamConsumer;

import org.json.*;

public class LogEventsParser {
    static JSONObject Parse(String data) {
        JSONObject parsed = new JSONObject();
        JSONObject jsonData = new JSONObject(data);
        if (jsonData.get("messageType").equals("DATA_MESSAGE")) {
            long start = 0, now;
            JSONArray logEvents = jsonData.getJSONArray("logEvents");
            for (int i = 0; i < logEvents.length(); i++) {
                JSONObject logEvent = logEvents.getJSONObject(i);
                String message = logEvent.getString("message");
                message = message.substring(message.indexOf(':') + 2);
                switch (i) {
                    case 0:
                        // HTTP Method
                        parsed.put("method", message.substring(0, message.indexOf(",")));
                        start = logEvent.getLong("timestamp");
                        break;
                    case 2:
                        // Query String
                        parsed.put("queryString", message.substring(0, message.length() - 2));
                        break;
                    case 3:
                        // Request Headers, Method
                        parsed.put("reqHeadersM", getHeaders(message));
                        break;
                    case 4:
                        // Request Body, Method
                        parsed.put("reqBodyM", message);
                        break;
                    case 5:
                        // Request URL
                        parsed.put("URL", message);
                        break;
                    case 6:
                        // Request Headers, Endpoint
                        parsed.put("reqHeadersE", getHeaders(message));
                        break;
                    case 7:
                        // Request Body, Endpoint
                        parsed.put("reqBodyE", message);
                        break;
                    case 8:
                        // Response status
                        parsed.put("statusCode", message.substring(0, message.indexOf(",")));
                        // Now and Interval
                        now = logEvent.getLong("timestamp");
                        parsed.put("now", now);
                        parsed.put("interval", start == 0 ? 0 : now - start);
                    case 9:
                        // Response Headers, Endpoint
                        parsed.put("resHeadersE", getHeaders(message));
                        break;
                    case 10:
                        // Response Body, Endpoint
                        parsed.put("resBodyE", message);
                        break;
                    case 11:
                        // Response Body, Method
                        parsed.put("resBodyM", message);
                        break;
                    case 12:
                        // Response Headers, Method
                        parsed.put("resHeadersM", getHeaders(message));
                        break;
                }
            }
        }
        return parsed;
    }

    private static String[] getHeaders(String message) {
        if (message.endsWith("[TRUNCATED]")) {
            return message.substring(1).split(", ");
        } else {
            return message.substring(1, message.length() - 1).split(", ");
        }
    }
}
