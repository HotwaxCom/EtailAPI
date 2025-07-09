package org.apache.ofbiz.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.service.*;
import org.apache.commons.io.IOUtils;
import java.net.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.util.*;

public class EtailServices {

    private static final String MODULE = EtailServices.class.getName();

    private static final String AUTH_TOKEN = "bVK81TGQdfmO~oCKumk5qHia2YFvxoRxEDrk*wQc4nvQ~cBvCMZuT1QuejxZJ9F29tQ3o4ZxxcA7FsFA0qNn9g==";

    //GET SALES ORDER BY ID
    public static Map<String, Object> getSalesOrder(DispatchContext dctx, Map<String, Object> context) {
        String orderId = (String) context.get("orderId");
        String apiUrl = "https://app-e2.etailsolutions.com/api/SalesOrder/" + orderId;
        Map<String, Object> result = ServiceUtil.returnSuccess();

        try {
            URL url = new URL(apiUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-evp-authentication", AUTH_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");

            if (conn.getResponseCode() != 200) {
                return ServiceUtil.returnError("HTTP Error: " + conn.getResponseCode());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(sb.toString(), Map.class);
            result.put("order", responseMap);

        } catch (Exception e) {
            Debug.logError(e, MODULE, "Failed to fetch Etail sales order");
            return ServiceUtil.returnError("Exception: " + e.getMessage());
        }

        return result;
    }

    //CREATE SALES ORDER
    public static Map<String, Object> createEtailOrders(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        ObjectMapper mapper = new ObjectMapper();

        try {
            Map<String, Object> orderMap = UtilGenerics.cast(context.get("orderBody"));
            if (UtilValidate.isEmpty(orderMap)) {
                return ServiceUtil.returnError("Missing or empty 'orderBody' in input.");
            }
            String jsonBody = mapper.writeValueAsString(orderMap);
            Debug.logInfo("Sending JSON to Etail SalesOrder API: " + jsonBody, MODULE);

            String apiUrl = "https://app-e2.etailsolutions.com/api/SalesOrder?AssignFulfillment=true";
            URL url = new URL(apiUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-evp-authentication", AUTH_TOKEN);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            String contentType = conn.getContentType();

            Debug.logInfo("Etail createSalesOrder response code: " + responseCode, MODULE);
            Debug.logInfo("Etail createSalesOrder content-type: " + contentType, MODULE);

            InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                return ServiceUtil.returnError("Etail API returned no response body (InputStream is null).");
            }

            StringBuilder responseBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
            }

            String responseStr = responseBuilder.toString();
            Debug.logInfo("Etail createSalesOrder response body: " + responseStr, MODULE);

            if (contentType != null && contentType.contains("application/json")) {
                Map<String, Object> responseMap = mapper.readValue(responseStr, Map.class);
                if (responseCode >= 200 && responseCode < 300) {
                    result.put("response", responseMap);
                } else {
                    return ServiceUtil.returnError("Etail API error response: " + responseMap);
                }
            } else {
                return ServiceUtil.returnError("Unexpected non-JSON response from Etail: " + responseStr);
            }

        } catch (Exception e) {
            Debug.logError(e, MODULE, "Exception while calling Etail createSalesOrder API");
            return ServiceUtil.returnError("Exception while calling Etail API: " + e.getMessage());
        }

        return result;
    }

    //UPDATE SALES ORDER
    public static Map<String, Object> updateSalesOrder(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        ObjectMapper mapper = new ObjectMapper();

        String orderId = (String) context.get("orderId");
        Map<String, Object> orderMap = UtilGenerics.cast(context.get("orderBody"));

        if (UtilValidate.isEmpty(orderId)) {
            return ServiceUtil.returnError("Missing required parameter: orderId");
        }

        if (UtilValidate.isEmpty(orderMap)) {
            return ServiceUtil.returnError("Missing or empty 'orderBody'");
        }

        try {
            String jsonBody = mapper.writeValueAsString(orderMap);
            Debug.logInfo("Sending update to Etail for Order ID " + orderId + ": " + jsonBody, MODULE);
            String apiUrl = "https://app-e2.etailsolutions.com/api/SalesOrder/" + URLEncoder.encode(orderId, "UTF-8");
            URL url = new URL(apiUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-evp-authentication", AUTH_TOKEN);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }
            int responseCode = conn.getResponseCode();
            String contentType = conn.getContentType();
            Debug.logInfo("Etail updateSalesOrder response code: " + responseCode, MODULE);
            InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                return ServiceUtil.returnError("Etail API returned no response (InputStream is null)");
            }

            StringBuilder responseBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
            }

            String responseStr = responseBuilder.toString();
            Debug.logInfo("Etail updateSalesOrder response: " + responseStr, MODULE);

            if (contentType != null && contentType.contains("application/json")) {
                Map<String, Object> responseMap = mapper.readValue(responseStr, Map.class);
                if (responseCode >= 200 && responseCode < 300) {
                    result.put("response", responseMap);
                } else {
                    return ServiceUtil.returnError("Etail API update error: " + responseMap);
                }
            } else {
                return ServiceUtil.returnError("Etail API returned unexpected content: " + responseStr);
            }

        } catch (Exception e) {
            Debug.logError(e, MODULE, "Exception while updating Etail SalesOrder");
            return ServiceUtil.returnError("Exception while updating Etail SalesOrder: " + e.getMessage());
        }
        return result;
    }

    //DELETE SALES ORDER
    public static Map<String, Object> deleteSalesOrder(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        ObjectMapper mapper = new ObjectMapper();

        String id = (String) context.get("id");

        if (UtilValidate.isEmpty(id)) {
            return ServiceUtil.returnError("Missing required parameter: id");
        }

        try {
            String apiUrl = "https://app-e2.etailsolutions.com/api/SalesOrder/" + id;
            URL url = new URL(apiUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-evp-authentication", AUTH_TOKEN);
            conn.setDoOutput(false);

            int responseCode = conn.getResponseCode();
            String contentType = conn.getContentType();

            Debug.logInfo("Etail deleteSalesOrder response code: " + responseCode, MODULE);

            InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                return ServiceUtil.returnError("Etail API returned no response (InputStream is null)");
            }

            String responseStr = IOUtils.toString(is, "UTF-8");
            Debug.logInfo("Etail deleteSalesOrder response: " + responseStr, MODULE);

            if (responseCode >= 200 && responseCode < 300) {
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("message", "Etail order deleted successfully.");
                responseMap.put("reference", responseStr);
                result.put("response", responseMap);
            } else {
                return ServiceUtil.returnError("Etail API delete error: " + responseStr);
            }

        } catch (Exception e) {
            Debug.logError(e, MODULE, "Exception while deleting Etail SalesOrder");
            return ServiceUtil.returnError("Exception while deleting Etail SalesOrder: " + e.getMessage());
        }

        return result;
    }
}