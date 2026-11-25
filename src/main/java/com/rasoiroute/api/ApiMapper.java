package com.rasoiroute.api;

import com.rasoiroute.dispatch.DispatchPlan;
import com.rasoiroute.dispatch.RouteStop;
import com.rasoiroute.dispatch.RouteSummary;
import com.rasoiroute.model.Order;
import com.rasoiroute.urgency.ScoredOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the app's objects (orders, plans) into plain maps and lists that Json.write can send to the browser. */
final class ApiMapper {

    private ApiMapper() {
        // static helper only
    }

    /** Distances are shown to 3 decimals (to the metre). */
    static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static Map<String, Object> order(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id());
        m.put("customerName", o.customerName());
        m.put("phone", o.phone());
        m.put("item", o.item());
        m.put("type", o.type().name());
        m.put("fulfilment", o.fulfilment().name());
        m.put("bookedAt", o.bookedAt().toString());
        m.put("dueAt", o.dueAt().toString());
        m.put("locality", o.locality());
        return m;
    }

    static Map<String, Object> scored(ScoredOrder s) {
        Map<String, Object> m = order(s.order());
        m.put("score", s.score());
        m.put("minutesLeft", s.minutesLeft());
        m.put("late", s.late());
        return m;
    }

    static Map<String, Object> plan(DispatchPlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", plan.day().toString());
        m.put("ordersDue", plan.ordersDue());
        m.put("deliveryCount", plan.deliveryCount());
        m.put("pickupCount", plan.pickupCount());
        m.put("lateCount", plan.lateCount());

        List<Object> top = new ArrayList<>();
        for (ScoredOrder s : plan.topUrgent()) {
            top.add(scored(s));
        }
        m.put("topUrgent", top);

        List<Object> pickups = new ArrayList<>();
        for (Order o : plan.pickups()) {
            pickups.add(order(o));
        }
        m.put("pickups", pickups);

        m.put("route", route(plan.route()));
        m.put("problems", new ArrayList<>(plan.problems()));
        return m;
    }

    static Map<String, Object> route(RouteSummary r) {
        Map<String, Object> m = new LinkedHashMap<>();

        List<Object> stops = new ArrayList<>();
        for (RouteStop stop : r.stops()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("locality", stop.locality());
            s.put("latitude", stop.latitude());
            s.put("longitude", stop.longitude());
            s.put("legKm", round3(stop.legKm()));
            s.put("orderIds", new ArrayList<>(stop.orderIds()));
            stops.add(s);
        }
        m.put("stops", stops);
        m.put("returnKm", round3(r.returnKm()));
        m.put("totalKm", round3(r.totalKm()));
        m.put("mstKm", round3(r.mstKm()));
        m.put("nearestNeighbourKm", round3(r.nearestNeighbourKm()));
        m.put("arrivalOrderKm", round3(r.arrivalOrderKm()));

        if (r.exactKm().isPresent()) {
            double exact = r.exactKm().getAsDouble();
            m.put("exactKm", round3(exact));
            m.put("ratioToExact", round3(exact == 0.0 ? 1.0 : r.totalKm() / exact));
        } else {
            m.put("exactKm", null);
            m.put("ratioToExact", null);
        }
        m.put("ratioToMst", round3(r.mstKm() == 0.0 ? 1.0 : r.totalKm() / r.mstKm()));
        m.put("withinGuarantee", r.withinGuarantee());
        return m;
    }
}
