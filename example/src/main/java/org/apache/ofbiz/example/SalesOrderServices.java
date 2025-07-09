package org.apache.ofbiz.example;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.entity.*;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.order.order.OrderReadHelper;
import org.apache.ofbiz.service.*;

import java.math.BigDecimal;
import java.util.*;

public class SalesOrderServices {

    private static final String MODULE = SalesOrderServices.class.getName();
    private static final String RESOURCE = "OrderUiLabels";
    private static final String RES_ERROR = "OrderErrorUiLabels";

    //Finds Orders By Id
    public static Map<String, Object> findOrderById(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String orderId = (String) context.get("orderId");
        OrderReadHelper orh = new OrderReadHelper(delegator, orderId);

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("orderHeader", orh.getOrderHeader());
        result.put("orderItems", orh.getOrderItems());
        return result;
    }

    //Gets all orders
    public static Map<String, Object> getAllOrders(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = new HashMap<>();

        try {
            List<GenericValue> orderHeaders = EntityQuery.use(delegator).from("OrderHeader").orderBy("orderId").queryList();
            List<Map<String, Object>> orderList = new ArrayList<>();

            for (GenericValue orderHeader : orderHeaders) {
                Map<String, Object> orderMap = orderHeader.getAllFields();

                List<GenericValue> orderItems = EntityQuery.use(delegator)
                        .from("OrderItem")
                        .where("orderId", orderHeader.getString("orderId"))
                        .orderBy("orderItemSeqId")
                        .queryList();

                List<Map<String, Object>> itemList = new ArrayList<>();
                for (GenericValue item : orderItems) {
                    itemList.add(item.getAllFields());
                }

                orderMap.put("orderItems", itemList);
                orderList.add(orderMap);
            }

            result.put("orders", orderList);
            result.put("responseMessage", "success");
            result.put("success", true);
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Error fetching orders: " + e.getMessage());
        }
        return result;
    }

    //Creates a new Order
    @SuppressWarnings("unchecked")
    public static Map<String, Object> createOrderHeader(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();

        try {
            List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) context.get("orderItems");
            List<GenericValue> orderItems = new ArrayList<>();
            if (itemMaps != null) {
                for (Map<String, Object> itemMap : itemMaps) {
                    orderItems.add(delegator.makeValue("OrderItem", itemMap));
                }
            }

            List<Map<String, Object>> adjMaps = (List<Map<String, Object>>) context.get("orderAdjustments");
            List<GenericValue> orderAdjustments = new ArrayList<>();
            if (adjMaps != null) {
                for (Map<String, Object> adjMap : adjMaps) {
                    orderAdjustments.add(delegator.makeValue("OrderAdjustment", adjMap));
                }
            }

            Map<String, Object> newContext = new HashMap<>(context);
            newContext.put("orderItems", orderItems);
            newContext.put("orderAdjustments", orderAdjustments);

            return dispatcher.runSync("storeOrder", newContext);

        } catch (Exception e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Error in createOrderHeader: " + e.getMessage());
        }
    }

    //Updates Order Header and Items
    @SuppressWarnings("unchecked")
    public static Map<String, Object> updateOrderHeader(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        String orderId = (String) context.get("orderId");
        List<Map<String, Object>> orderItems = (List<Map<String, Object>>) context.get("orderItems");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            Map<String, Object> updateHeaderCtx = new HashMap<>(context);
            updateHeaderCtx.put("userLogin", userLogin);

            Map<String, Object> headerUpdates = dispatcher.runSync("updateOrderHeader", updateHeaderCtx);
            if (ServiceUtil.isError(headerUpdates)) {
                return headerUpdates;
            }

            if (orderItems != null && !orderItems.isEmpty()) {
                for (Map<String, Object> item : orderItems) {
                    String orderItemSeqId = (String) item.get("orderItemSeqId");
                    if (orderItemSeqId == null) {
                        return ServiceUtil.returnError("Missing 'orderItemSeqId' in one of the orderItems.");
                    }

                    GenericValue orderItem = EntityQuery.use(delegator)
                            .from("OrderItem")
                            .where("orderId", orderId, "orderItemSeqId", orderItemSeqId)
                            .queryOne();

                    if (orderItem == null) {
                        return ServiceUtil.returnError("OrderItem not found: " + orderItemSeqId);
                    }

                    if (item.containsKey("quantity")) {
                        orderItem.set("quantity", new BigDecimal(item.get("quantity").toString()));
                    }
                    if (item.containsKey("unitPrice")) {
                        orderItem.set("unitPrice", new BigDecimal(item.get("unitPrice").toString()));
                    }

                    orderItem.store();
                    Debug.logInfo("Updated OrderItem [" + orderItemSeqId + "] for order [" + orderId + "]", MODULE);
                }
            }
        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Error updating order: " + e.getMessage());
        }

        return ServiceUtil.returnSuccess("Order header and items updated.");
    }

    //Deletes the Order ( StatusId="ORDER_CANCELLED")
    public static Map<String, Object> deleteOrderHeader(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String orderId = (String) context.get("orderId");
        Locale locale = (Locale) context.get("locale");

        try {
            GenericValue orderHeader = EntityQuery.use(delegator)
                    .from("OrderHeader")
                    .where("orderId", orderId)
                    .queryOne();

            if (orderHeader == null) {
                return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                        "OrderErrorOrderNotFound", locale));
            }

            orderHeader.set("statusId", "ORDER_CANCELLED");
            orderHeader.store();

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("orderId", orderId);
            result.put("statusId", "ORDER_CANCELLED");
            return result;

        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                    "OrderErrorCannotUpdateStatus", locale) + " - " + e.getMessage());
        }
    }
}
