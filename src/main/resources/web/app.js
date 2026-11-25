"use strict";

/*
 * KitchenHQ screen. It talks to the Java server only through the /api/... addresses.
 *
 * Safety note: every piece of text that came from a person or from the server is put on the page with
 * textContent (never innerHTML), so a name like <script>... can never run as code.
 */
(function () {

  var SVG_NS = "http://www.w3.org/2000/svg";

  var TYPES = {
    DAILY_FOOD: { icon: "utensils", label: "Daily food", hint: "Lunch and dinner boxes" },
    BAKERY: { icon: "cake", label: "Bakery", hint: "Cakes and snacks" },
    CATERING: { icon: "users", label: "Catering", hint: "Parties and events" }
  };
  var HOW = {
    DELIVERY: { icon: "truck", label: "Delivery", hint: "We bring it to them" },
    PICKUP: { icon: "bag", label: "Pickup", hint: "They come to the kitchen" }
  };
  var TIME_CHOICES = [["09:00", "9 AM"], ["12:00", "12 noon"], ["15:00", "3 PM"], ["18:00", "6 PM"], ["20:00", "8 PM"]];
  var VIEWS = ["overview", "orders", "planner", "stock", "analytics", "settings"];
  var PAGE_SIZE = 30;

  var reduceMotion = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

  // Everything the screen remembers lives here.
  var state = {
    view: "today",
    day: todayText(),          // the day being looked at, like 2026-11-19
    pretendNow: "",            // "" means "use the real time"
    kitchen: null,
    places: [],                // every delivery locality (for the map)
    plan: null,
    orders: [],
    stats: { bloomHits: 0, repeatsBlocked: 0, falseAlarms: 0, total: 0 },
    selectedStop: null,
    filter: "ALL",
    shown: PAGE_SIZE,
    animateMap: true,
    draft: { type: null, fulfilment: "DELIVERY", locality: null, date: todayText(), time: "12:00", otherTime: false, family: null, quantity: 1 },
    stock: { days: 7, data: null },
    demo: { count: 100, days: 7, dup: 10 },
    clearArmed: false,
    streetFitKey: "",
    search: "",                // the words typed in the orders search box
    typeFilter: "ALL",
    freshId: null,             // the id of an order that was just saved (it glows for a moment)
    tilesBefore: null
  };
  var leafletMap = null;
  var tileProblemShown = false;


  // ---------------------------------------------------------------- icons (small line drawings, no pictures from outside)
  // Each icon is a list of shapes on a 24 x 24 grid: a string is a path, {c:[x,y,r]} a circle, {r:[x,y,w,h,rx]} a rectangle.
  var ICONS = {
    "utensils": ["M3 2v7a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2V2", "M7 2v20", "M21 15V2a5 5 0 0 0-5 5v6a2 2 0 0 0 2 2h3zm0 0v7"],
    "map": ["M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z", "M9 3v15", "M15 6v15"],
    "plus": ["M12 5v14", "M5 12h14"],
    "list": ["M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01"],
    "sliders": ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"],
    "calendar": [{r: [3, 4, 18, 18, 2]}, "M16 2v4", "M8 2v4", "M3 10h18"],
    "truck": [{r: [1, 3, 15, 13, 1]}, "M16 8h4l3 3v5h-7z", {c: [5.5, 18.5, 2.5]}, {c: [18.5, 18.5, 2.5]}],
    "bag": ["M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z", "M3 6h18", "M16 10a4 4 0 0 1-8 0"],
    "box": ["M21 8l-9-5-9 5v8l9 5 9-5z", "M3.3 7L12 12l8.7-5", "M12 22V12"],
    "clock": [{c: [12, 12, 10]}, "M12 6v6l4 2"],
    "alert": [{c: [12, 12, 10]}, "M12 8v4", "M12 16h.01"],
    "check": [{c: [12, 12, 10]}, "M8 12l3 3 5-6"],
    "cake": ["M20 21v-8a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8", "M4 16s.5-1 2-1 2.5 2 4 2 2.5-2 4-2 2.5 2 4 2 2-1 2-1", "M2 21h20", "M7 8v3", "M12 8v3", "M17 8v3", "M7 4h.01", "M12 4h.01", "M17 4h.01"],
    "users": ["M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", {c: [9, 7, 4]}, "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75"],
    "phone": ["M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z"],
    "trash": ["M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6", "M14 11v6"],
    "warning": ["M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z", "M12 9v4", "M12 17h.01"],
    "repeat": ["M17 1l4 4-4 4", "M3 11V9a4 4 0 0 1 4-4h14", "M7 23l-4-4 4-4", "M21 13v2a4 4 0 0 1-4 4H3"],
    "edit": ["M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"],
    "flag": ["M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7"],
    "route": [{c: [6, 19, 3]}, "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", {c: [18, 5, 3]}],
    "trend-down": ["M23 18l-9.5-9.5-5 5L1 6", "M17 18h6v-6"],
    "search": [{c: [11, 11, 8]}, "M21 21l-4.35-4.35"],
    "play": ["M5 3l14 9-14 9z"],
    "globe": [{c: [12, 12, 10]}, "M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"],
    "chevron-left": ["M15 18l-6-6 6-6"],
    "chevron-right": ["M9 18l6-6-6-6"],
    "pin": ["M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z", {c: [12, 10, 3]}],
    "home": ["M3 10.5L12 3l9 7.5", "M5 9.5V21h14V9.5", "M10 21v-6h4v6"],
    "chart": ["M12 20V10", "M18 20V4", "M6 20v-4"],
    "gear": [{c: [12, 12, 3]}, "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"],
    "menu": ["M3 12h18", "M3 6h18", "M3 18h18"],
    "x": ["M18 6L6 18", "M6 6l12 12"],
    "arrow-right": ["M5 12h14", "M12 5l7 7-7 7"],
    "panel-left": [{r: [3, 3, 18, 18, 2]}, "M9 3v18"]
  };

  function fillShapes(svg, name) {
    (ICONS[name] || ICONS.box).forEach(function (shape) {
      var node;
      if (typeof shape === "string") {
        node = svgEl("path", { d: shape });
      } else if (shape.c) {
        node = svgEl("circle", { cx: shape.c[0], cy: shape.c[1], r: shape.c[2] });
      } else {
        node = svgEl("rect", { x: shape.r[0], y: shape.r[1], width: shape.r[2], height: shape.r[3], rx: shape.r[4] });
      }
      svg.appendChild(node);
    });
  }

  /** A small icon as its own picture (for buttons, headings, cards). */
  function iconSvg(name) {
    var svg = svgEl("svg", {
      viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", "stroke-width": 2,
      "stroke-linecap": "round", "stroke-linejoin": "round", "aria-hidden": "true", focusable: "false"
    }, "i");
    fillShapes(svg, name);
    return svg;
  }

  /** An icon placed inside the big map picture. */
  function mapIcon(name, cx, cy, size, colour) {
    var svg = svgEl("svg", {
      x: cx - size / 2, y: cy - size / 2, width: size, height: size, viewBox: "0 0 24 24", fill: "none", stroke: colour,
      "stroke-width": 2, "stroke-linecap": "round", "stroke-linejoin": "round", "pointer-events": "none"
    });
    fillShapes(svg, name);
    return svg;
  }

  /** Puts the icon in front of whatever is inside this element. */
  function addIcon(node, name) {
    node.insertBefore(iconSvg(name), node.firstChild);
    return node;
  }

  function iconBox(name, className) {
    var box = el("span", null, className);
    box.appendChild(iconSvg(name));
    return box;
  }

  function fillStaticIcons() {
    document.querySelectorAll("[data-icon]").forEach(function (holder) {
      if (!holder.firstChild) {
        holder.appendChild(iconSvg(holder.getAttribute("data-icon")));
      }
    });
  }

  // ===================================================================== small helpers

  function $(id) {
    return document.getElementById(id);
  }

  function el(tag, text, className) {
    var node = document.createElement(tag);
    if (text !== undefined && text !== null) {
      node.textContent = String(text);
    }
    if (className) {
      node.className = className;
    }
    return node;
  }

  function svgEl(tag, attributes, className) {
    var node = document.createElementNS(SVG_NS, tag);
    Object.keys(attributes || {}).forEach(function (name) {
      node.setAttribute(name, String(attributes[name]));
    });
    if (className) {
      node.setAttribute("class", className);
    }
    return node;
  }

  function clear(node) {
    node.replaceChildren();
  }

  function pad(n) {
    return (n < 10 ? "0" : "") + n;
  }

  // ----- dates (all worked out from the pieces, so the browser's time zone can never shift a day)

  function dateText(d) {
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  }

  function todayText() {
    return dateText(new Date());
  }

  function parseDay(text) {
    var p = text.split("-");
    return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
  }

  function addDays(text, n) {
    var d = parseDay(text);
    d.setDate(d.getDate() + n);
    return dateText(d);
  }

  var WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
  var MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

  function longDay(text) {
    var d = parseDay(text);
    return WEEKDAYS[d.getDay()] + ", " + d.getDate() + " " + MONTHS[d.getMonth()] + " " + d.getFullYear();
  }

  function shortDay(text) {
    var d = parseDay(text);
    return WEEKDAYS[d.getDay()].slice(0, 3) + " " + d.getDate() + " " + MONTHS[d.getMonth()];
  }

  /** The day the app treats as "today": the real one, or the pretend one. */
  function baseToday() {
    return state.pretendNow ? state.pretendNow.slice(0, 10) : todayText();
  }

  function friendlyDay(text) {
    var base = baseToday();
    if (text === base) {
      return "Today";
    }
    if (text === addDays(base, 1)) {
      return "Tomorrow";
    }
    if (text === addDays(base, -1)) {
      return "Yesterday";
    }
    return shortDay(text);
  }

  function time12(hour, minute) {
    var suffix = hour >= 12 ? "PM" : "AM";
    var h = hour % 12 === 0 ? 12 : hour % 12;
    return h + ":" + pad(minute) + " " + suffix;
  }

  /** "2026-11-19T16:00" -> "Tomorrow, 4:00 PM" */
  function dueLabel(iso) {
    var day = iso.slice(0, 10);
    var hour = Number(iso.slice(11, 13));
    var minute = Number(iso.slice(14, 16));
    return friendlyDay(day) + ", " + time12(hour, minute);
  }

  function level(order) {
    if (order.late) {
      return "late";
    }
    return order.minutesLeft < 120 ? "soon" : "ok";
  }

  /** The time left, in plain words. */
  function leftWords(order) {
    var m = order.minutesLeft;
    if (m < 0) {
      var over = -m;
      return over < 60 ? "Late by " + over + " min" : "Late by " + Math.floor(over / 60) + " h " + (over % 60) + " min";
    }
    if (m < 60) {
      return "Due in " + m + " min";
    }
    if (m < 180) {
      return "Due in " + Math.floor(m / 60) + " h " + (m % 60) + " min";
    }
    if (m < 48 * 60) {
      return "Due in " + Math.floor(m / 60) + " hours";
    }
    return "Due in " + Math.floor(m / 1440) + " days";
  }

  function km(value) {
    return value.toFixed(1) + " km";
  }

  function plural(n, one, many) {
    return n + " " + (n === 1 ? one : many);
  }

  // ===================================================================== talking to the server

  function callApi(method, path, body) {
    var options = { method: method, headers: {} };
    if (body !== undefined) {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(body);
    }
    return fetch(path, options).then(function (response) {
      return response.text().then(function (text) {
        var data = null;
        try {
          data = text ? JSON.parse(text) : null;
        } catch (e) {
          data = null;
        }
        return { ok: response.ok, status: response.status, data: data };
      });
    }, function () {
      throw new Error("We cannot reach the KitchenHQ program. Please check that it is still running.");
    });
  }

  function errorText(result) {
    return result.data && result.data.error ? result.data.error : "Something went wrong (code " + result.status + ")";
  }

  function query(params) {
    var parts = [];
    Object.keys(params).forEach(function (key) {
      var value = params[key];
      if (value !== "" && value !== null && value !== undefined) {
        parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(value));
      }
    });
    return parts.length ? "?" + parts.join("&") : "";
  }

  function setBusy(isBusy) {
    $("loading").hidden = !isBusy;
  }

  function toast(text, kind) {
    var box = $("toasts");
    var node = el("div", null, "toast " + (kind || ""));
    node.appendChild(iconSvg(kind === "bad" ? "warning" : "check"));
    node.appendChild(el("span", text));
    function leave() {
      if (!node.parentNode || node.classList.contains("leaving")) {
        return;
      }
      node.classList.add("leaving");
      setTimeout(function () {
        if (node.parentNode) {
          node.parentNode.removeChild(node);
        }
      }, 300);
    }
    node.addEventListener("click", leave);
    box.appendChild(node);
    setTimeout(leave, 5000);
  }

  // ===================================================================== loading the day

  function setStatus(ok, text) {
    $("status").className = "status" + (ok ? "" : " bad");
    $("status-text").textContent = text;
  }

  function loadPlaces() {
    return callApi("GET", "/api/localities").then(function (r) {
      if (r.ok) {
        state.kitchen = r.data.kitchen;
        state.places = r.data.localities;
      }
    });
  }
  // ---------- Google Maps navigation links ----------
  // Google may limit how many stops one link accepts. If stops go missing
  // when you test, lower this number.
  var MAX_STOPS_PER_LINK = 9;

  function mapsPoint(s) {
    return s.latitude + "," + s.longitude;
  }

  // stops[0] is the Kitchen. Returns a list of Google Maps URLs
  // (one if the route is short, several if it must be split).
  function buildNavLinks(stops) {
    if (!stops || stops.length < 2) return [];
    var kitchen = stops[0];
    var visits = stops.slice(1).filter(function (s) {
      return isFinite(s.latitude) && isFinite(s.longitude);
    });
    var links = [];
    var start = kitchen;

    for (var i = 0; i < visits.length; i += MAX_STOPS_PER_LINK) {
      var chunk = visits.slice(i, i + MAX_STOPS_PER_LINK);
      var isLast = i + MAX_STOPS_PER_LINK >= visits.length;
      var destination = isLast ? kitchen : chunk[chunk.length - 1];
      var middle = isLast ? chunk : chunk.slice(0, chunk.length - 1);

      var url = "https://www.google.com/maps/dir/?api=1" +
          "&origin=" + encodeURIComponent(mapsPoint(start)) +
          "&destination=" + encodeURIComponent(mapsPoint(destination)) +
          "&travelmode=driving";
      if (middle.length > 0) {
        url += "&waypoints=" + middle.map(function (p) {
          return encodeURIComponent(mapsPoint(p));
        }).join("%7C");
      }
      links.push(url);
      start = destination;
    }
    return links;
  }

  /** Puts the "Start navigation" buttons into every place that has room for them. */
  function renderNavLinks() {
    var stops = state.plan && state.plan.route ? state.plan.route.stops : [];
    var links = buildNavLinks(stops);
    document.querySelectorAll(".nav-slot").forEach(function (slot) {
      clear(slot);
      var main = !!slot.closest("#view-planner");
      links.forEach(function (url, i) {
        var a = el("a", null, main ? "btn primary" : "btn");
        a.href = url;
        a.target = "_blank";
        a.rel = "noopener";
        a.appendChild(iconSvg("pin"));
        a.appendChild(document.createTextNode(links.length === 1
          ? "Start navigation in Google Maps"
          : "Navigate: part " + (i + 1) + " of " + links.length));
        slot.appendChild(a);
      });
    });
  }
  // ---------- end Google Maps navigation links ----------

  function loadDay() {
    setBusy(true);
    var now = state.pretendNow;
    return Promise.all([
      callApi("GET", "/api/plan" + query({ date: state.day, now: now, k: 5 })),
      callApi("GET", "/api/orders" + query({ date: state.day, now: now }))
    ]).then(function (results) {
      var planRes = results[0];
      var ordersRes = results[1];
      if (!planRes.ok || !ordersRes.ok) {
        showProblem(errorText(planRes.ok ? ordersRes : planRes));
        return;
      }
      showProblem("");
      state.plan = planRes.data;
      state.orders = ordersRes.data.orders;
      state.stats = {
        bloomHits: ordersRes.data.bloomHits,
        repeatsBlocked: ordersRes.data.repeatsBlocked,
        falseAlarms: ordersRes.data.falseAlarms
      };
      setStatus(true, "Connected");
      state.shown = PAGE_SIZE;
      renderAll();
      loadStock();
    }).catch(function (e) {
      setStatus(false, "Not connected");
      showProblem(e.message);
    }).then(function () {
      setBusy(false);
    });
  }

  function showProblem(text) {
    var box = $("problem");
    box.hidden = !text;
    box.textContent = text || "";
  }

  // ===================================================================== screens and menu

  var TITLES = { overview: "ChefBoard", orders: "All orders", planner: "Delivery planner", stock: "Stock", analytics: "Analytics", settings: "Settings" };
  var LEGACY = { today: "overview", kitchen: "overview", more: "settings" };

  function showView(name) {
    if (LEGACY[name]) {
      name = LEGACY[name];
    }
    if (VIEWS.indexOf(name) < 0) {
      name = "overview";
    }
    state.view = name;
    VIEWS.forEach(function (v) {
      $("view-" + v).hidden = v !== name;
    });
    document.querySelectorAll(".navitem").forEach(function (item) {
      if (item.getAttribute("data-view") === name) {
        item.setAttribute("aria-current", "page");
      } else {
        item.removeAttribute("aria-current");
      }
    });
    document.querySelector(".app").setAttribute("data-view", name);
    $("page-title").textContent = TITLES[name];
    $("daybar").hidden = name === "settings" || name === "stock";
    closeMenu();
    if (location.hash !== "#" + name) {
      history.replaceState(null, "", "#" + name);
    }
    window.scrollTo(0, 0);
    movePill();
    if (name === "planner") {
      renderMap();          // the map is only drawn while it is on screen, so it always gets the right size
    }
    if (name === "analytics") {
      renderAnalytics();    // the bars grow each time the page opens
    }
    if (name === "stock") {
      loadStock();
    }
  }

  /** The soft highlight in the menu slides to the page you are on. */
  function movePill() {
    var pill = document.querySelector(".nav-pill");
    var active = document.querySelector(".navitem[aria-current=\"page\"]");
    if (!pill || !active) {
      return;
    }
    pill.style.height = active.offsetHeight + "px";
    pill.style.transform = "translateY(" + active.offsetTop + "px)";
    pill.style.opacity = "1";
  }

  function openMenu() {
    $("sidebar").classList.add("open");
    $("scrim").hidden = false;
  }

  function closeMenu() {
    $("sidebar").classList.remove("open");
    $("scrim").hidden = true;
  }

  function setCollapsed(on) {
    if (on) {
      document.documentElement.setAttribute("data-collapsed", "1");
    } else {
      document.documentElement.removeAttribute("data-collapsed");
    }
    try {
      localStorage.setItem("rr-collapsed", on ? "1" : "0");
    } catch (e) { /* not saving is fine */ }
    $("collapse").setAttribute("aria-pressed", on ? "true" : "false");
    $("collapse").setAttribute("aria-label", on ? "Expand the menu" : "Collapse the menu");
    setTimeout(function () {
      movePill();
      if (leafletMap) {
        leafletMap.invalidateSize();
      }
    }, 420);
  }

  function goToDay(text) {
    state.day = text;
    state.selectedStop = null;
    state.animateMap = true;
    renderDayBar();
    return loadDay();
  }

  function renderAll() {
    renderDayBar();
    renderPretend();
    renderTiles();
    renderOverview();
    renderUrgent();
    renderPickups();
    renderNavLinks();
    renderMap();
    renderSteps();
    renderStopDetail();
    renderQuality();
    renderOrders();
    renderAnalytics();
    renderBehind();
    // The server counts all saved orders, not just today's, in /api/health.
    callApi("GET", "/api/health").then(function (r) {
      if (r.ok) {
        state.stats.total = r.data.orders;
        $("status-text").textContent = "Connected - " + plural(r.data.orders, "order", "orders") + " saved";
      }
    }, function () { /* the status already shows the problem */ });
  }

  function renderDayBar() {
    $("day-title").textContent = friendlyDay(state.day);
    $("day-sub").textContent = longDay(state.day);
    $("day-pick").value = state.day;
    $("page-date").textContent = longDay(baseToday());
  }

  function renderPretend() {
    var banner = $("pretend-banner");
    banner.hidden = !state.pretendNow;
    if (state.pretendNow) {
      var hour = Number(state.pretendNow.slice(11, 13));
      var minute = Number(state.pretendNow.slice(14, 16));
      $("pretend-text").textContent = "Pretending it is " + shortDay(state.pretendNow.slice(0, 10)) + ", " + time12(hour, minute);
    }
  }

  // ===================================================================== today: tiles

  /** A number that counts up to its value (only when the value changed, so it never flickers). */
  function countUp(node, target, animate) {
    if (reduceMotion || !animate || target === 0) {
      node.textContent = String(target);
      return;
    }
    var started = performance.now();
    node.textContent = "0";
    function step(now) {
      var t = Math.min(1, (now - started) / 800);
      node.textContent = String(Math.round(target * (1 - Math.pow(1 - t, 3))));
      if (t < 1) {
        requestAnimationFrame(step);
      }
    }
    requestAnimationFrame(step);
  }

  function renderTiles() {
    var plan = state.plan;
    var box = $("tiles");
    clear(box);
    if (!plan) {
      return;
    }
    var items = [
      { icon: "box", n: plan.ordersDue, label: "Orders", kind: "" },
      { icon: "truck", n: plan.deliveryCount, label: "To deliver", kind: "" },
      { icon: "bag", n: plan.pickupCount, label: "For pickup", kind: "" },
      plan.lateCount > 0
        ? { icon: "alert", n: plan.lateCount, label: "Late", kind: "late" }
        : { icon: "check", n: 0, label: "Late", kind: "good" }
    ];
    var before = state.tilesBefore || [];
    items.forEach(function (item, i) {
      var tile = el("div", null, "tile " + item.kind);
      tile.style.animationDelay = (i * 0.07) + "s";
      tile.appendChild(iconBox(item.icon, "icon"));
      var num = el("span", item.n, "num");
      countUp(num, item.n, before[i] !== item.n);
      tile.appendChild(num);
      tile.appendChild(el("span", item.label, "lab"));
      box.appendChild(tile);
    });
    state.tilesBefore = items.map(function (item) { return item.n; });
  }

  // ===================================================================== overview: welcome, route summary, recent activity

  function renderOverview() {
    var plan = state.plan;
    var hour = state.pretendNow ? Number(state.pretendNow.slice(11, 13)) : new Date().getHours();
    $("welcome-kicker").textContent = longDay(baseToday());
    $("welcome-title").textContent = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
    var sub = $("welcome-sub");
    if (!plan) {
      sub.textContent = "";
    } else if (plan.ordersDue === 0) {
      sub.textContent = "No orders for " + friendlyDay(state.day).toLowerCase() + " yet. Create one to get started.";
    } else {
      sub.textContent = plural(plan.ordersDue, "order", "orders") + " for " + friendlyDay(state.day).toLowerCase() + ": " +
        plan.deliveryCount + " to deliver, " + plan.pickupCount + " for pickup. " +
        (plan.lateCount > 0 ? plan.lateCount + " late." : "None are late.");
    }

    // delivery overview
    var box = $("ov-route");
    clear(box);
    if (!plan || plan.route.stops.length <= 1) {
      box.appendChild(emptyBlock("truck", "No deliveries on this day."));
    } else {
      var route = plan.route;
      var stops = route.stops;
      var facts = el("div", null, "route-facts");
      function fact(value, label) {
        var f = el("div", null, "fact");
        f.appendChild(el("b", value));
        f.appendChild(el("span", label));
        facts.appendChild(f);
      }
      fact(String(stops.length - 1), "stops");
      fact(km(route.totalKm), "round trip");
      var saving = route.arrivalOrderKm > route.totalKm + 0.05 ? Math.round((route.arrivalOrderKm - route.totalKm) / route.arrivalOrderKm * 100) : 0;
      fact(saving >= 1 ? saving + "%" : "-", "shorter than arrival order");
      box.appendChild(facts);

      var byId = ordersById();
      var list = el("ul", null, "next-stops");
      stops.slice(1, 5).forEach(function (stop, i) {
        var li = el("li");
        li.appendChild(el("span", String(i + 1), "badge " + stopLevel(stop, byId)));
        li.appendChild(el("span", stop.locality));
        li.appendChild(el("span", plural(stop.orderIds.length, "order", "orders"), "sub"));
        list.appendChild(li);
      });
      if (stops.length > 5) {
        var more = el("li");
        more.appendChild(el("span", "+" + (stops.length - 5) + " more stops", "sub"));
        list.appendChild(more);
      }
      box.appendChild(list);
    }

    // recent activity: the orders booked most recently, from the real order list
    var feed = $("ov-recent");
    clear(feed);
    if (state.orders.length === 0) {
      feed.appendChild(emptyBlock("list", "No orders for this day yet."));
    } else {
      state.orders.slice().sort(function (a, b) {
        return a.bookedAt < b.bookedAt ? 1 : a.bookedAt > b.bookedAt ? -1 : 0;
      }).slice(0, 5).forEach(function (o) {
        var type = TYPES[o.type] || { icon: "box", label: o.type };
        var row = el("button", null, "feed-row");
        row.type = "button";
        row.appendChild(iconBox(type.icon, "fi"));
        var text = el("span");
        text.appendChild(el("span", o.item, "ft"));
        text.appendChild(el("span", o.customerName + " · " + (o.fulfilment === "DELIVERY" ? o.locality : "Pickup"), "fs"));
        row.appendChild(text);
        row.appendChild(el("span", "Booked " + dueLabel(o.bookedAt), "fw"));
        row.addEventListener("click", function () { openOrder(o); });
        feed.appendChild(row);
      });
    }
  }

  // ===================================================================== today: the map

  function ordersById() {
    var map = {};
    state.orders.forEach(function (o) {
      map[o.id] = o;
    });
    return map;
  }

  /** The most pressing level among the orders at a stop. */
  function stopLevel(stop, byId) {
    var worst = "ok";
    stop.orderIds.forEach(function (id) {
      var o = byId[id];
      if (!o) {
        return;
      }
      var l = level(o);
      if (l === "late") {
        worst = "late";
      } else if (l === "soon" && worst !== "late") {
        worst = "soon";
      }
    });
    return worst;
  }

  function destroyStreetMap() {
    if (leafletMap) {
      leafletMap.remove();
      leafletMap = null;
    }
  }

  /** The same route on real street pictures (OpenStreetMap). The lines are still straight lines between stops. */
  function renderStreetMap(box) {
    var note = $("map-note");
    var holder = el("div", null, "street-map");
    box.appendChild(holder);

    var previous = null;
    if (leafletMap) {
      previous = { center: leafletMap.getCenter(), zoom: leafletMap.getZoom() };
    }
    destroyStreetMap();

    leafletMap = L.map(holder, { zoomControl: true, scrollWheelZoom: false });
    leafletMap.attributionControl.setPrefix(false);
    var tiles = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 18,
      attribution: "&copy; OpenStreetMap contributors"
    }).addTo(leafletMap);
    tiles.on("tileerror", function () {
      if (!tileProblemShown) {
        tileProblemShown = true;
        note.hidden = false;
        note.textContent = "The street pictures need the internet. Switch back to the schematic map if they do not appear.";
      }
    });
    tiles.on("tileload", function () {
      tileProblemShown = false;
      note.hidden = true;
    });

    var byId = ordersById();
    var stops = state.plan ? state.plan.route.stops : [];
    var activeNames = {};
    stops.forEach(function (s, i) {
      if (i > 0) {
        activeNames[s.locality] = true;
      }
    });
    var bounds = [[state.kitchen.latitude, state.kitchen.longitude]];

    state.places.forEach(function (place) {
      bounds.push([place.latitude, place.longitude]);
      if (activeNames[place.name]) {
        return;
      }
      var dot = L.circleMarker([place.latitude, place.longitude], {
        radius: 7, color: "#fff", weight: 2, fillColor: "#B98250", fillOpacity: 0.95
      }).addTo(leafletMap);
      dot.bindTooltip(place.name);
      dot.on("click", function () { selectStop(place.name); });
    });

    if (stops.length > 1) {
      var line = stops.map(function (s) { return [s.latitude, s.longitude]; });
      var routeLine = L.polyline(line, { color: "#C66B4E", weight: 6, opacity: 0.92 }).addTo(leafletMap);
      animateRoute(routeLine);
      L.polyline([line[line.length - 1], line[0]], { color: "#C66B4E", weight: 4, opacity: 0.7, dashArray: "4 10" }).addTo(leafletMap);
    }

    var kitchenIcon = L.divIcon({ className: "pin kitchen-pin", html: "<span class=\"kp\"></span>", iconSize: [44, 44], iconAnchor: [22, 22] });
    var kitchenMarker = L.marker([state.kitchen.latitude, state.kitchen.longitude], { icon: kitchenIcon, title: "The kitchen", keyboard: false }).addTo(leafletMap);
    var kp = kitchenMarker.getElement() && kitchenMarker.getElement().querySelector(".kp");
    if (kp) {
      kp.appendChild(iconSvg("utensils"));
    }

    stops.forEach(function (stop, i) {
      if (i === 0) {
        return;
      }
      var lvl = stopLevel(stop, byId);
      var icon = L.divIcon({
        className: "pin stop-pin " + lvl + (state.selectedStop === stop.locality ? " chosen" : ""),
        html: String(i), iconSize: [42, 42], iconAnchor: [21, 21]
      });
      var marker = L.marker([stop.latitude, stop.longitude], {
        icon: icon, title: stop.locality + ", " + plural(stop.orderIds.length, "order", "orders")
      }).addTo(leafletMap);
      marker.bindTooltip(stop.locality, { permanent: true, direction: "top", offset: [0, -20], className: "pin-label" });
      marker.on("click", function () { selectStop(stop.locality); });
      bounds.push([stop.latitude, stop.longitude]);
    });

    var key = state.day + "|" + state.pretendNow + "|" + stops.length;
    if (previous && state.streetFitKey === key) {
      leafletMap.setView(previous.center, previous.zoom, { animate: false });
    } else {
      leafletMap.fitBounds(bounds, { padding: [30, 30], animate: false });
      state.streetFitKey = key;
    }
  }

  function renderMap() {
    var box = $("map-box");
    if ($("view-planner").hidden) {
      return;               // drawn when the page is opened, so the map always has a real size
    }
    if (typeof L === "undefined") {
      destroyStreetMap();
      clear(box);
      box.appendChild(el("p", "The map could not be loaded. Please reload the page.", "msg"));
      return;
    }
    if (!state.kitchen || state.places.length === 0) {
      destroyStreetMap();
      clear(box);
      box.appendChild(el("p", "Loading the map...", "msg"));
      return;
    }
    $("map-note").hidden = true;
    clear(box);
    renderStreetMap(box);
    if (state.plan) {
      state.animateMap = false;   // the route draws itself once per day, not on every tap
    }
  }

  /** The route line draws itself from the kitchen round the stops. */
  function animateRoute(layer) {
    if (reduceMotion || !state.animateMap) {
      return;
    }
    var path = layer.getElement && layer.getElement();
    if (!path || !path.getTotalLength) {
      return;
    }
    path.style.setProperty("--len", String(Math.ceil(path.getTotalLength())));
    path.classList.add("route-draw");
    path.addEventListener("animationend", function () { path.classList.remove("route-draw"); });
  }

  function keyActivate(action) {
    return function (event) {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        action();
      }
    };
  }

  function selectStop(name) {
    state.selectedStop = state.selectedStop === name ? null : name;
    renderMap();
    renderSteps();
    renderStopDetail();
    if (state.selectedStop) {
      $("stop-detail").scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "nearest" });
    }
  }

  // ===================================================================== today: delivery order list

  function renderSteps() {
    var list = $("steps");
    var callouts = $("callouts");
    clear(list);
    clear(callouts);
    var plan = state.plan;
    if (!plan) {
      return;
    }
    var route = plan.route;
    var stops = route.stops;

    if (stops.length <= 1) {
      var item = el("li");
      var msg = el("div", null, "empty");
      msg.appendChild(iconBox("truck", "big"));
      msg.appendChild(el("span", "No deliveries on this day."));
      item.appendChild(msg);
      list.appendChild(item);
      return;
    }

    var c1 = el("div", null, "callout");
    c1.appendChild(iconSvg("route"));
    var t1 = el("span");
    t1.appendChild(document.createTextNode("Round trip: "));
    t1.appendChild(el("b", km(route.totalKm)));
    c1.appendChild(t1);
    callouts.appendChild(c1);
    if (route.arrivalOrderKm > route.totalKm + 0.05) {
      var saving = Math.round((route.arrivalOrderKm - route.totalKm) / route.arrivalOrderKm * 100);
      if (saving >= 1) {
        var c2 = el("div", null, "callout green");
        c2.appendChild(iconSvg("trend-down"));
        var t2 = el("span");
        t2.appendChild(el("b", saving + "% shorter"));
        t2.appendChild(document.createTextNode(" than going in the order the orders came in"));
        c2.appendChild(t2);
        callouts.appendChild(c2);
      }
    }

    var byId = ordersById();
    list.appendChild(stepItem("plain", "start", "Leave the kitchen", "Start here", false, null));
    stops.forEach(function (stop, i) {
      if (i === 0) {
        return;
      }
      var lvl = stopLevel(stop, byId);
      var sub = plural(stop.orderIds.length, "order", "orders") + " · " + km(stop.legKm) + " from the last stop";
      list.appendChild(stepItem(lvl, String(i), stop.locality, sub, state.selectedStop === stop.locality, stop.locality));
    });
    list.appendChild(stepItem("plain", "end", "Back at the kitchen", km(route.returnKm) + " to get home", false, null));
  }

  function stepItem(levelName, badgeText, name, sub, selected, stopName) {
    var li = el("li");
    var button = el("button", null, "step" + (selected ? " selected" : ""));
    button.type = "button";
    var badge = el("span", badgeText === "start" || badgeText === "end" ? null : badgeText, "badge " + levelName);
    if (badgeText === "start") {
      badge.appendChild(iconSvg("utensils"));
    } else if (badgeText === "end") {
      badge.appendChild(iconSvg("flag"));
    }
    button.appendChild(badge);
    var text = el("span");
    text.appendChild(el("span", name, "name"));
    text.appendChild(el("span", sub, "sub"));
    button.appendChild(text);
    if (stopName) {
      button.addEventListener("click", function () { selectStop(stopName); });
    } else {
      button.disabled = true;
      button.style.cursor = "default";
    }
    li.appendChild(button);
    return li;
  }

  // ===================================================================== today: detail of one stop

  function renderStopDetail() {
    var box = $("stop-detail");
    clear(box);
    var name = state.selectedStop;
    if (!name || !state.plan) {
      box.hidden = true;
      return;
    }
    box.hidden = false;

    var stops = state.plan.route.stops;
    var index = -1;
    stops.forEach(function (s, i) {
      if (i > 0 && s.locality === name) {
        index = i;
      }
    });

    var head = el("div", null, "detail-head");
    head.appendChild(el("h2", index > 0 ? "Stop " + index + ": " + name : name));
    var close = el("button", "Close", "softbtn");
    close.type = "button";
    close.addEventListener("click", function () { selectStop(name); });
    head.appendChild(close);
    box.appendChild(head);

    if (index < 0) {
      var none = el("div", null, "empty");
      none.appendChild(iconBox("check", "big"));
      none.appendChild(el("span", "Nothing to deliver here on " + friendlyDay(state.day).toLowerCase() + "."));
      box.appendChild(none);
      return;
    }
    var byId = ordersById();
    var cards = el("div", null, "cards");
    stops[index].orderIds.forEach(function (id) {
      if (byId[id]) {
        cards.appendChild(orderCard(byId[id], null));
      }
    });
    box.appendChild(cards);
  }

  // ===================================================================== order cards

  function orderCard(o, rank) {
    var type = TYPES[o.type] || { icon: "box", label: o.type };
    var lvl = level(o);
    var card = el("article", null, "ocard " + lvl + (state.freshId && state.freshId === o.id ? " fresh" : ""));
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.setAttribute("aria-label", "Order details: " + o.item + " for " + o.customerName);
    card.addEventListener("click", function () { openOrder(o); });
    card.addEventListener("keydown", keyActivate(function () { openOrder(o); }));

    card.appendChild(iconBox(type.icon, "oicon"));

    var title = el("h4");
    if (rank) {
      title.appendChild(el("span", rank, "rank"));
    }
    title.appendChild(document.createTextNode(o.item));
    card.appendChild(title);

    var who = el("p", null, "line");
    who.appendChild(el("b", o.customerName));
    card.appendChild(who);
    card.appendChild(addIcon(el("p", o.phone, "line phone"), "phone"));

    var foot = el("div", null, "foot");
    foot.appendChild(el("span", leftWords(o), "chip " + lvl));
    foot.appendChild(addIcon(el("span", dueLabel(o.dueAt), "chip plain"), "clock"));
    var how = HOW[o.fulfilment];
    foot.appendChild(addIcon(el("span", o.fulfilment === "DELIVERY" ? o.locality : "Pickup", "chip plain"), how.icon === "truck" ? "pin" : "bag"));
    foot.appendChild(el("span", type.label, "chip plain"));
    if (o.family) {
      foot.appendChild(addIcon(el("span", fmtNumber(o.quantity) + " " + o.familyUnit + " " + o.family, "chip plain"), "box"));
      if (o.made) {
        foot.appendChild(addIcon(el("span", "Made", "chip"), "check"));
      }
    }
    card.appendChild(foot);
    return card;
  }

  // ===================================================================== order details (pop-up)

  function openOrder(order) {
    var o = ordersById()[order.id] || order;
    var type = TYPES[o.type] || { icon: "box", label: o.type };
    $("od-title").textContent = o.item;
    var body = $("od-body");
    clear(body);

    var chips = el("div", null, "od-chips");
    if (o.minutesLeft !== undefined) {
      chips.appendChild(el("span", leftWords(o), "chip " + level(o)));
    }
    chips.appendChild(el("span", type.label, "chip plain"));
    chips.appendChild(el("span", o.fulfilment === "DELIVERY" ? "Delivery" : "Pickup", "chip plain"));
    body.appendChild(chips);

    var rows = [
      ["Customer", o.customerName],
      ["Phone", o.phone],
      ["Order number", o.id],
      ["Needed", dueLabel(o.dueAt)],
      ["Booked", dueLabel(o.bookedAt)],
      ["Where", o.fulfilment === "DELIVERY" ? o.locality : "Customer comes to the kitchen"]
    ];
    if (o.score !== undefined) {
      rows.push(["Urgency score", o.score]);
    }
    var list = el("dl", null, "od-list");
    rows.forEach(function (r) {
      var row = el("div");
      row.appendChild(el("dt", r[0]));
      row.appendChild(el("dd", r[1]));
      list.appendChild(row);
    });
    body.appendChild(list);

    if (o.family) {
      var prow = el("div");
      prow.appendChild(el("dt", "Product"));
      prow.appendChild(el("dd", fmtNumber(o.quantity) + " " + o.familyUnit + " " + o.family + (o.made ? " (made)" : "")));
      list.appendChild(prow);
      if (!o.made) {
        var made = el("button", "Mark as made", "btn primary block");
        made.type = "button";
        made.addEventListener("click", function () { markMade(o.id, made); });
        body.appendChild(made);
        body.appendChild(el("p", "This takes the ingredients for it off the kitchen shelf.", "note"));
      }
    }
    openDialog($("order-dialog"));
  }

  // ===================================================================== pop-ups and the slide-over

  function openDialog(d) {
    if (!d.open) {
      d.showModal();
    }
    requestAnimationFrame(function () {
      requestAnimationFrame(function () { d.classList.add("show"); });
    });
  }

  function closeDialog(d) {
    d.classList.remove("show");
    setTimeout(function () {
      if (d.open && !d.classList.contains("show")) {
        d.close();
      }
    }, reduceMotion ? 0 : 340);
  }

  function openDrawer() {
    renderForm();
    openDialog($("drawer"));
    setTimeout(function () { $("f-name").focus(); }, 420);
  }

  function closeDrawer() {
    closeDialog($("drawer"));
  }

  function emptyBlock(icon, text, buttonText, onClick) {
    var box = el("div", null, "empty");
    box.appendChild(iconBox(icon, "big"));
    box.appendChild(el("span", text));
    if (buttonText) {
      box.appendChild(document.createElement("br"));
      var b = el("button", buttonText, "softbtn");
      b.type = "button";
      b.addEventListener("click", onClick);
      box.appendChild(b);
    }
    return box;
  }

  function renderUrgent() {
    var box = $("urgent");
    clear(box);
    if (!state.plan) {
      return;
    }
    var top = state.plan.topUrgent;
    if (top.length === 0) {
      box.className = "";
      box.appendChild(emptyBlock("utensils", "No orders for this day yet.", "Add an order", function () { openDrawer(); }));
      return;
    }
    box.className = "stack";
    top.forEach(function (o, i) {
      box.appendChild(orderCard(o, i + 1));
    });
  }

  function renderPickups() {
    var box = $("pickups");
    clear(box);
    if (!state.plan) {
      return;
    }
    var list = state.plan.pickups;
    if (list.length === 0) {
      box.className = "";
      box.appendChild(emptyBlock("bag", "Nobody is coming to pick up on this day."));
      return;
    }
    box.className = "cards";
    var byId = ordersById();
    list.forEach(function (p) {
      box.appendChild(orderCard(byId[p.id] || scoredFallback(p), null));
    });
  }

  /** Pickups come without urgency numbers; this gives a card something sensible to show. */
  function scoredFallback(o) {
    var copy = {};
    Object.keys(o).forEach(function (k) { copy[k] = o[k]; });
    copy.minutesLeft = 9999;
    copy.late = false;
    return copy;
  }

  // ===================================================================== today: "for the curious"

  function renderQuality() {
    var box = $("quality");
    clear(box);
    var plan = state.plan;
    if (!plan || plan.route.stops.length <= 1) {
      box.appendChild(el("p", "There is no route to measure on this day.", "note"));
      return;
    }
    var route = plan.route;
    var exact = route.exactKm;
    function times(value) {
      return exact === null || exact === 0 ? "-" : (value / exact).toFixed(2) + " times";
    }
    var rows = [
      ["Our route (spanning-tree method)", km(route.totalKm), times(route.totalKm)],
      ["Best possible (exact search)", exact === null ? "too many stops" : km(exact), exact === null ? "-" : "1.00 times"],
      ["Always go to the nearest stop", km(route.nearestNeighbourKm), times(route.nearestNeighbourKm)],
      ["Go in the order orders came in", km(route.arrivalOrderKm), times(route.arrivalOrderKm)],
      ["Spanning tree (no route can be shorter)", km(route.mstKm), times(route.mstKm)]
    ];
    var table = el("table");
    var head = el("tr");
    ["Method", "Round trip", "Compared with best"].forEach(function (h, i) {
      head.appendChild(el("th", h, i > 0 ? "num" : ""));
    });
    var thead = el("thead");
    thead.appendChild(head);
    table.appendChild(thead);
    var tbody = el("tbody");
    rows.forEach(function (row) {
      var tr = el("tr");
      row.forEach(function (cell, i) {
        tr.appendChild(el("td", cell, i > 0 ? "num" : ""));
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    var wrap = el("div", null, "table-wrap");
    wrap.appendChild(table);
    box.appendChild(wrap);

    var verdict = el("div", null, "verdict " + (route.withinGuarantee ? "pass" : "fail"));
    verdict.textContent = route.withinGuarantee
      ? "Guarantee check passed: the route (" + km(route.totalKm) + ") is at most twice the spanning tree (" + km(route.mstKm) +
        "). No route can be shorter than the tree, so this route is within 2 times the best possible. That is a guarantee, not a claim that it is the very best."
      : "Guarantee check FAILED. This should never happen. Please report it.";
    box.appendChild(verdict);
  }

  // ===================================================================== orders screen

  function matchesSearch(o) {
    var q = state.search.trim().toLowerCase();
    if (!q) {
      return true;
    }
    var type = TYPES[o.type] ? TYPES[o.type].label : o.type;
    var hay = [o.customerName, o.phone, o.phone.replace(/\D/g, ""), o.item, o.locality || "pickup", type, o.id].join(" ").toLowerCase();
    return q.split(/\s+/).every(function (part) { return hay.indexOf(part) >= 0; });
  }

  function renderOrders() {
    var chips = $("filter-chips");
    var typeChips = $("type-chips");
    var list = $("orders-list");
    var more = $("orders-more");
    clear(chips);
    clear(typeChips);
    clear(list);

    var base = state.orders.filter(matchesSearch);
    var counts = { ALL: base.length, DELIVERY: 0, PICKUP: 0 };
    var typeCounts = { ALL: base.length, DAILY_FOOD: 0, BAKERY: 0, CATERING: 0 };
    base.forEach(function (o) {
      counts[o.fulfilment]++;
      typeCounts[o.type]++;
    });

    [["ALL", "All"], ["DELIVERY", "Deliveries"], ["PICKUP", "Pickups"]].forEach(function (f) {
      chips.appendChild(pressChip(f[1] + " (" + counts[f[0]] + ")", state.filter === f[0], function () {
        state.filter = f[0];
        state.shown = PAGE_SIZE;
        renderOrders();
      }));
    });
    typeChips.appendChild(pressChip("All kinds", state.typeFilter === "ALL", function () {
      state.typeFilter = "ALL";
      state.shown = PAGE_SIZE;
      renderOrders();
    }));
    Object.keys(TYPES).forEach(function (key) {
      typeChips.appendChild(addIcon(pressChip(TYPES[key].label + " (" + typeCounts[key] + ")", state.typeFilter === key, function () {
        state.typeFilter = key;
        state.shown = PAGE_SIZE;
        renderOrders();
      }), TYPES[key].icon));
    });

    var shown = base.filter(function (o) {
      return (state.filter === "ALL" || o.fulfilment === state.filter) && (state.typeFilter === "ALL" || o.type === state.typeFilter);
    });
    $("orders-count").textContent = "Showing " + shown.length + " of " + plural(state.orders.length, "order", "orders");

    if (state.orders.length === 0) {
      list.className = "";
      list.appendChild(emptyBlock("list", "No orders for " + friendlyDay(state.day).toLowerCase() + " yet.", "Add an order", function () { openDrawer(); }));
      more.hidden = true;
      return;
    }
    if (shown.length === 0) {
      list.className = "";
      list.appendChild(emptyBlock("search", "No orders match your search or filters."));
      more.hidden = true;
      return;
    }
    list.className = "cards wide";
    shown.slice(0, state.shown).forEach(function (o) {
      list.appendChild(orderCard(o, null));
    });
    more.hidden = shown.length <= state.shown;
    more.textContent = "Show more orders (" + (shown.length - state.shown) + " more)";
  }

  // ===================================================================== analytics (real numbers only)

  function barRow(label, valueText, percent, tone) {
    var row = el("div", null, "bar-row");
    row.appendChild(el("span", label, "bl"));
    var track = el("div", null, "bar-track");
    var fill = el("div", null, "bar-fill " + (tone || ""));
    track.appendChild(fill);
    row.appendChild(track);
    row.appendChild(el("span", valueText, "bv"));
    setTimeout(function () { fill.style.width = Math.max(percent, percent > 0 ? 3 : 0) + "%"; }, 40);
    return row;
  }

  function analyticsCard(title, note, wide) {
    var card = el("section", null, "panel" + (wide ? " wide" : ""));
    var head = el("div", null, "an-head");
    head.appendChild(el("h3", title));
    if (note) {
      head.appendChild(el("span", note));
    }
    card.appendChild(head);
    return card;
  }

  function renderAnalytics() {
    var grid = $("an-grid");
    clear(grid);
    var orders = state.orders;
    var plan = state.plan;
    var day = friendlyDay(state.day).toLowerCase();

    if (orders.length === 0) {
      var none = analyticsCard("Orders", null, true);
      none.appendChild(emptyBlock("chart", "No orders for " + day + " yet, so there is nothing to chart.", "Add an order", function () { openDrawer(); }));
      grid.appendChild(none);
    } else {
      // 1. by type
      var byType = analyticsCard("Orders by type", plural(orders.length, "order", "orders") + " " + day);
      var typeCount = { DAILY_FOOD: 0, BAKERY: 0, CATERING: 0 };
      orders.forEach(function (o) { typeCount[o.type]++; });
      var typeMax = Math.max(typeCount.DAILY_FOOD, typeCount.BAKERY, typeCount.CATERING, 1);
      var typeTone = { DAILY_FOOD: "", BAKERY: "caramel", CATERING: "gold" };
      var typeBars = el("div", null, "bars");
      Object.keys(TYPES).forEach(function (key) {
        typeBars.appendChild(barRow(TYPES[key].label, String(typeCount[key]), typeCount[key] / typeMax * 100, typeTone[key]));
      });
      byType.appendChild(typeBars);
      grid.appendChild(byType);

      // 2. delivery or pickup
      var split = analyticsCard("Delivery or pickup", "share of orders");
      var deliveries = orders.filter(function (o) { return o.fulfilment === "DELIVERY"; }).length;
      var pickups = orders.length - deliveries;
      var bar = el("div", null, "split-bar");
      var s1 = el("span");
      s1.style.background = "var(--terra)";
      s1.style.flexGrow = "0";
      var s2 = el("span");
      s2.style.background = "var(--gold)";
      s2.style.flexGrow = "0";
      bar.appendChild(s1);
      bar.appendChild(s2);
      split.appendChild(bar);
      setTimeout(function () { s1.style.flexGrow = String(deliveries); s2.style.flexGrow = String(pickups); }, 40);
      var legend = el("div", null, "split-legend");
      [["var(--terra)", "Delivery", deliveries], ["var(--gold)", "Pickup", pickups]].forEach(function (l) {
        var item = el("span");
        var dot = el("i");
        dot.style.background = l[0];
        item.appendChild(dot);
        item.appendChild(document.createTextNode(l[1] + ": "));
        item.appendChild(el("b", l[2] + " (" + Math.round(l[2] / orders.length * 100) + "%)"));
        legend.appendChild(item);
      });
      split.appendChild(legend);
      grid.appendChild(split);

      // 3. urgency mix
      var mix = analyticsCard("How urgent is the day", "from the time left on each order");
      var lv = { ok: 0, soon: 0, late: 0 };
      orders.forEach(function (o) { lv[level(o)]++; });
      var mixMax = Math.max(lv.ok, lv.soon, lv.late, 1);
      var mixBars = el("div", null, "bars");
      mixBars.appendChild(barRow("Plenty of time", String(lv.ok), lv.ok / mixMax * 100, "ok"));
      mixBars.appendChild(barRow("Due soon", String(lv.soon), lv.soon / mixMax * 100, "soon"));
      mixBars.appendChild(barRow("Late", String(lv.late), lv.late / mixMax * 100, "late"));
      mix.appendChild(mixBars);
      grid.appendChild(mix);

      // 4. busiest places
      var places = analyticsCard("Busiest places", "deliveries " + day);
      var perPlace = {};
      orders.forEach(function (o) {
        if (o.fulfilment === "DELIVERY" && o.locality) {
          perPlace[o.locality] = (perPlace[o.locality] || 0) + 1;
        }
      });
      var ranked = Object.keys(perPlace).sort(function (a, b) { return perPlace[b] - perPlace[a] || (a < b ? -1 : 1); }).slice(0, 6);
      if (ranked.length === 0) {
        places.appendChild(emptyBlock("truck", "No deliveries on this day."));
      } else {
        var placeBars = el("div", null, "bars");
        ranked.forEach(function (name) {
          placeBars.appendChild(barRow(name, String(perPlace[name]), perPlace[name] / perPlace[ranked[0]] * 100, "caramel"));
        });
        places.appendChild(placeBars);
      }
      grid.appendChild(places);
    }

    // 5. route comparison
    var routeCard = analyticsCard("Route length: our method against the others", "round trip, straight-line km", true);
    if (!plan || plan.route.stops.length <= 1) {
      routeCard.appendChild(emptyBlock("route", "There is no delivery route on this day to measure."));
    } else {
      var r = plan.route;
      var rows = [
        ["Our route (spanning tree)", r.totalKm, "espresso"],
        ["Nearest stop each time", r.nearestNeighbourKm, "caramel"],
        ["Order the orders came in", r.arrivalOrderKm, "caramel"]
      ];
      if (r.exactKm !== null) {
        rows.push(["Best possible (exact search)", r.exactKm, "ok"]);
      }
      rows.push(["Spanning tree (no route can be shorter)", r.mstKm, "gold"]);
      var routeMax = Math.max.apply(null, rows.map(function (row) { return row[1]; }));
      var routeBars = el("div", null, "bars");
      rows.forEach(function (row) {
        routeBars.appendChild(barRow(row[0], km(row[1]), routeMax > 0 ? row[1] / routeMax * 100 : 0, row[2]));
      });
      routeCard.appendChild(routeBars);
      var sentence = r.arrivalOrderKm > r.totalKm + 0.05
        ? "Our route is " + Math.round((r.arrivalOrderKm - r.totalKm) / r.arrivalOrderKm * 100) + "% shorter than going in the order the orders came in."
        : "Our route is about the same as going in the order the orders came in.";
      if (r.exactKm !== null && r.exactKm > 0) {
        sentence += " It is " + (r.totalKm / r.exactKm).toFixed(2) + " times the best possible.";
      }
      routeCard.appendChild(el("p", sentence, "note"));
      routeCard.lastChild.style.marginTop = "16px";
    }
    grid.appendChild(routeCard);

    // 6. duplicate screening
    var dup = analyticsCard("Repeat-order check", "all saved orders since the program started", true);
    var stats = el("div", null, "mini-stats");
    [[state.stats.repeatsBlocked, "repeat orders stopped"], [state.stats.bloomHits, "alerts from the quick check"], [state.stats.falseAlarms, "alerts that were wrong"]].forEach(function (s) {
      var f = el("div", null, "fact");
      f.appendChild(el("b", s[0]));
      f.appendChild(el("span", s[1]));
      stats.appendChild(f);
    });
    dup.appendChild(stats);
    var dnote = el("p", "The quick check (a Bloom filter) never misses a real repeat. Every alert is then checked exactly, so a wrong alert never blocks a real order.", "note");
    dnote.style.marginTop = "16px";
    dup.appendChild(dnote);
    grid.appendChild(dup);
  }

  // ===================================================================== new order screen

  function pressChip(label, pressed, onClick, className) {
    var b = el("button", label, className || "chip-btn");
    b.type = "button";
    b.setAttribute("aria-pressed", pressed ? "true" : "false");
    b.addEventListener("click", onClick);
    return b;
  }

  function bigChoice(icon, title, hint, pressed, onClick) {
    var b = el("button", null, "choice");
    b.type = "button";
    b.setAttribute("aria-pressed", pressed ? "true" : "false");
    b.appendChild(iconBox(icon, "big"));
    b.appendChild(el("span", title, "t"));
    b.appendChild(el("span", hint, "h"));
    b.addEventListener("click", onClick);
    return b;
  }

  function renderForm() {
    var d = state.draft;

    var types = $("type-choices");
    clear(types);
    Object.keys(TYPES).forEach(function (key) {
      var t = TYPES[key];
      types.appendChild(bigChoice(t.icon, t.label, t.hint, d.type === key, function () {
        d.type = key;
        renderForm();
      }));
    });

    var how = $("how-choices");
    clear(how);
    Object.keys(HOW).forEach(function (key) {
      var h = HOW[key];
      how.appendChild(bigChoice(h.icon, h.label, h.hint, d.fulfilment === key, function () {
        d.fulfilment = key;
        renderForm();
      }));
    });

    $("place-box").hidden = d.fulfilment !== "DELIVERY";
    var places = $("place-chips");
    clear(places);
    state.places.forEach(function (p) {
      places.appendChild(pressChip(p.name, d.locality === p.name, function () {
        d.locality = p.name;
        renderForm();
      }));
    });

    var days = $("day-chips");
    clear(days);
    var base = todayText();
    for (var i = 0; i < 7; i++) {
      (function (text) {
        var label = text === base ? "Today" : text === addDays(base, 1) ? "Tomorrow" : shortDay(text);
        days.appendChild(pressChip(label, d.date === text, function () {
          d.date = text;
          renderForm();
        }));
      })(addDays(base, i));
    }

    var times = $("time-chips");
    clear(times);
    TIME_CHOICES.forEach(function (t) {
      times.appendChild(pressChip(t[1], !d.otherTime && d.time === t[0], function () {
        d.time = t[0];
        d.otherTime = false;
        renderForm();
      }));
    });
    times.appendChild(pressChip("Other time", d.otherTime, function () {
      d.otherTime = true;
      renderForm();
      $("f-time").focus();
    }));
    $("other-time-box").hidden = !d.otherTime;
    if (d.otherTime) {
      $("f-time").value = d.time;
    }

    renderProduct();
    renderSummary();
  }

  function draftDueAt() {
    return state.draft.date + "T" + state.draft.time;
  }

  function renderSummary() {
    var d = state.draft;
    var box = $("order-summary");
    var name = $("f-name").value.trim();
    var item = $("f-item").value.trim();
    if (!d.type && !name && !item) {
      box.className = "summary empty-sum";
      box.textContent = "Your order will appear here as you fill in the steps above.";
      return;
    }
    box.className = "summary";
    var parts = [];
    if (d.type) {
      parts.push(item || TYPES[d.type].label);
    } else if (item) {
      parts.push(item);
    }
    if (name) {
      parts.push("for " + name);
    }
    parts.push(d.fulfilment === "DELIVERY" ? "delivery" + (d.locality ? " to " + d.locality : "") : "pickup");
    var hour = Number(d.time.slice(0, 2));
    var minute = Number(d.time.slice(3, 5));
    parts.push(friendlyDay(d.date) + ", " + time12(hour, minute));
    box.textContent = parts.join("  ·  ");
  }

  function markMissing(id, isMissing) {
    $(id).classList.toggle("missing", isMissing);
  }

  function showResult(kind, icon, title, text, buttons) {
    var box = $("order-result");
    clear(box);
    var card = el("div", null, "result " + kind);
    var heading = el("h3", title);
    heading.insertBefore(iconSvg(icon), heading.firstChild);
    card.appendChild(heading);
    card.appendChild(el("p", text));
    if (buttons && buttons.length) {
      var row = el("div", null, "row");
      buttons.forEach(function (b) {
        var button = el("button", b.label, "softbtn");
        if (b.icon) {
          addIcon(button, b.icon);
        }
        button.type = "button";
        button.addEventListener("click", b.action);
        row.appendChild(button);
      });
      card.appendChild(row);
    }
    box.appendChild(card);
    card.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "center" });
  }

  function saveOrder() {
    var d = state.draft;
    var name = $("f-name").value.trim();
    var phone = $("f-phone").value.trim();
    var item = $("f-item").value.trim();

    var missingWho = !name || !phone;
    var missingWhat = !d.type || !item;
    var missingHow = d.fulfilment === "DELIVERY" && !d.locality;
    markMissing("q-who", missingWho);
    markMissing("q-what", missingWhat);
    markMissing("q-how", missingHow);
    markMissing("q-when", false);

    if (missingWho || missingWhat || missingHow) {
      var first = missingWho ? "q-who" : missingWhat ? "q-what" : "q-how";
      var why = missingWho ? "Please write the customer's name and phone number."
        : missingWhat ? "Please choose the kind of order and say what they want."
          : "Please choose the place for the delivery.";
      showResult("bad", "alert", "Almost there", why, []);
      $(first).scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "center" });
      return;
    }

    var nowText = state.pretendNow || dateText(new Date()) + "T" + pad(new Date().getHours()) + ":" + pad(new Date().getMinutes());
    if (draftDueAt() < nowText) {
      markMissing("q-when", true);
      showResult("bad", "clock", "That time has already passed", "Please choose a day and time that is still to come.", []);
      $("q-when").scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "center" });
      return;
    }

    var body = {
      customerName: name,
      phone: phone,
      item: item,
      type: d.type,
      fulfilment: d.fulfilment,
      dueAt: draftDueAt()
    };
    if (d.fulfilment === "DELIVERY") {
      body.locality = d.locality;
    }
    if (d.family) {
      body.family = d.family;
      body.quantity = d.quantity;
    }
    if (state.pretendNow) {
      body.bookedAt = state.pretendNow;   // the order is "booked" at the pretend time, so the story stays consistent
    }

    var button = $("save-order");
    button.disabled = true;
    callApi("POST", "/api/orders", body).then(function (r) {
      if (r.status === 201) {
        state.freshId = r.data.order ? r.data.order.id : null;
        showResult("ok", "check", "Order saved", TYPES[d.type].label + " order for " + name + " on " + dueLabel(draftDueAt()) + ".", [
          { label: "Add another order", icon: "plus", action: resetForm },
          { label: "See the route", icon: "map", action: function () { var day = draftDueAt().slice(0, 10); resetForm(); closeDrawer(); goTo("planner"); goToDay(day); } }
        ]);
        toast("Order saved.", "ok");
        loadDay();
      } else if (r.status === 409) {
        showResult("warn", "repeat", "This looks like a repeat", r.data.message + ". We did not save it a second time.", [
          { label: "Change the order", icon: "edit", action: function () { clear($("order-result")); } }
        ]);
        loadDay();
      } else {
        showResult("bad", "warning", "We could not save this order", errorText(r), []);
      }
    }).catch(function (e) {
      showResult("bad", "warning", "We could not save this order", e.message, []);
    }).then(function () {
      button.disabled = false;
    });
  }

  function resetForm() {
    $("f-name").value = "";
    $("f-phone").value = "";
    $("f-item").value = "";
    state.draft.type = null;
    state.draft.locality = null;
    state.draft.family = null;
    state.draft.quantity = 1;
    clear($("stock-check"));
    ["q-who", "q-what", "q-how", "q-when"].forEach(function (id) { markMissing(id, false); });
    clear($("order-result"));
    renderForm();
    $("dr-body").scrollTop = 0;
  }

  // ===================================================================== more screen

  function renderMore() {
    var counts = $("demo-count");
    var days = $("demo-days");
    var dups = $("demo-dup");
    clear(counts);
    clear(days);
    clear(dups);
    [50, 100, 300, 1000].forEach(function (n) {
      counts.appendChild(pressChip(String(n), state.demo.count === n, function () { state.demo.count = n; renderMore(); }));
    });
    [1, 3, 7].forEach(function (n) {
      days.appendChild(pressChip(plural(n, "day", "days"), state.demo.days === n, function () { state.demo.days = n; renderMore(); }));
    });
    [[0, "None"], [10, "A few"], [25, "Many"]].forEach(function (p) {
      dups.appendChild(pressChip(p[1], state.demo.dup === p[0], function () { state.demo.dup = p[0]; renderMore(); }));
    });

    var pretend = $("pretend-chips");
    clear(pretend);
    [["08:00", "8 in the morning"], ["12:00", "12 noon"], ["17:00", "5 in the evening"]].forEach(function (t) {
      pretend.appendChild(pressChip(t[1], state.pretendNow === state.day + "T" + t[0], function () {
        state.pretendNow = state.day + "T" + t[0];
        state.animateMap = true;
        loadDay();
        renderMore();
      }));
    });
    if (state.pretendNow) {
      pretend.appendChild(pressChip("Use the real time", false, resetPretend, "chip-btn"));
    }
  }

  function renderBehind() {
    var box = $("behind");
    clear(box);
    var s = state.stats;
    var lines = [
      ["Repeat orders stopped", s.repeatsBlocked],
      ["Times the quick check raised an alert", s.bloomHits],
      ["Alerts that turned out to be wrong", s.falseAlarms]
    ];
    lines.forEach(function (line) {
      var p = el("p", null, "note");
      p.appendChild(document.createTextNode(line[0] + ": "));
      p.appendChild(el("b", line[1]));
      box.appendChild(p);
    });
  }

  function resetPretend() {
    state.pretendNow = "";
    state.animateMap = true;
    renderPretend();
    renderMore();
    loadDay();
  }

  function loadDemo() {
    var button = $("demo-load");
    button.disabled = true;
    var path = "/api/demo/generate" + query({ count: state.demo.count, days: state.demo.days, dupRate: state.demo.dup / 100 });
    callApi("POST", path).then(function (r) {
      if (!r.ok) {
        toast(errorText(r), "bad");
        return;
      }
      toast("Done! " + r.data.accepted + " orders loaded. " + r.data.repeatsBlocked + " repeats were stopped.", "ok");
      state.day = todayText();
      // Keep the pretend time, but only if it belongs to the day we are now showing
      if (state.pretendNow && state.pretendNow.slice(0, 10) !== state.day) {
        state.pretendNow = "";
      }
      renderPretend();
      renderMore();
      state.selectedStop = null;
      state.animateMap = true;
      goTo("overview");
      return loadDay();
    }).catch(function (e) {
      toast(e.message, "bad");
    }).then(function () {
      button.disabled = false;
    });
  }

  function clearAll() {
    openDialog($("confirm-dialog"));
  }

  function doClearAll() {
    closeDialog($("confirm-dialog"));
    callApi("DELETE", "/api/orders").then(function (r) {
      if (r.ok) {
        toast("All orders deleted.", "ok");
        state.selectedStop = null;
        state.animateMap = true;
        return loadDay();
      }
      toast(errorText(r), "bad");
    }).catch(function (e) {
      toast(e.message, "bad");
    });
  }

  // ===================================================================== stock

  var checkTimer = null;
  var checkTicket = 0;

  function fmtNumber(value) {
    var rounded = Math.round(Number(value) * 100) / 100;
    return String(rounded);
  }

  // Amounts are kept in g, ml or pcs. People think in kg and litres, so show those once it is a thousand or more.
  function fmtQty(value, unit) {
    var v = Number(value);
    if (unit === "g" && Math.abs(v) >= 1000) {
      return fmtNumber(v / 1000) + " kg";
    }
    if (unit === "ml" && Math.abs(v) >= 1000) {
      return fmtNumber(v / 1000) + " L";
    }
    return fmtNumber(v) + " " + unit;
  }

  // The unit people type in, and how many base units one of it is.
  function inputUnit(unit) {
    if (unit === "g") {
      return { label: "kg", factor: 1000 };
    }
    if (unit === "ml") {
      return { label: "L", factor: 1000 };
    }
    return { label: unit, factor: 1 };
  }

  var LEVEL_WORDS = { OK: "Fine", LOW: "Running low", SHORT: "Not enough", OUT: "Empty" };

  function loadStock() {
    return callApi("GET", "/api/stock" + query({ now: state.pretendNow, days: state.stock.days })).then(function (r) {
      if (r.ok) {
        state.stock.data = r.data;
        renderStock();
        renderProduct();
      }
    }, function () {
      // The main status line already tells the person the server cannot be reached.
    });
  }

  function renderStock() {
    var data = state.stock.data;
    if (!data) {
      return;
    }

    var days = $("stock-days");
    clear(days);
    [3, 7, 14].forEach(function (n) {
      days.appendChild(pressChip("Next " + n + " days", state.stock.days === n, function () {
        state.stock.days = n;
        loadStock();
      }));
    });

    var alertBox = $("stock-alert");
    var problems = data.counts.short + data.counts.out;
    var lows = data.counts.low;
    alertBox.hidden = false;
    if (problems > 0) {
      alertBox.className = "banner bad";
      alertBox.textContent = plural(problems, "supply is", "supplies are") + " not enough for the booked orders. See the shopping list below.";
    } else if (lows > 0) {
      alertBox.className = "banner warn";
      alertBox.textContent = plural(lows, "supply is", "supplies are") + " running low. Time to plan a shopping trip.";
    } else {
      alertBox.className = "banner good";
      alertBox.textContent = data.linkedOrders === 0
        ? "Everything is in stock. No booked orders are using the stock yet."
        : "Everything is in stock for the " + plural(data.linkedOrders, "booked order", "booked orders") + ".";
    }

    // orders the stock cannot cover
    var riskPanel = $("stock-risk-panel");
    var risk = $("stock-risk");
    clear(risk);
    riskPanel.hidden = data.atRisk.length === 0;
    data.atRisk.forEach(function (a) {
      var row = el("div", null, "riskrow");
      var title = el("p", null, "line");
      title.appendChild(el("b", a.item));
      title.appendChild(document.createTextNode("  for " + a.customerName + ", due " + dueLabel(a.dueAt)));
      row.appendChild(title);
      var parts = a.missing.map(function (m) { return m.name + " (short by " + fmtQty(m.amount, m.unit) + ")"; });
      row.appendChild(el("p", "Missing: " + parts.join(", "), "note"));
      risk.appendChild(row);
    });

    // shopping list
    var shopPanel = $("stock-shop-panel");
    var shop = $("stock-shop");
    clear(shop);
    shopPanel.hidden = data.shopping.length === 0;
    data.shopping.forEach(function (line) {
      var row = el("div", null, "shoprow");
      var left = el("div");
      left.appendChild(el("b", line.name));
      left.appendChild(el("p", line.reason + ". On the shelf " + fmtQty(line.onHand, line.unit) + ", booked orders need " + fmtQty(line.committed, line.unit) + ".", "note"));
      row.appendChild(left);
      row.appendChild(el("span", "Buy " + fmtQty(line.buyAmount, line.unit) + " (" + plural(line.packs, "pack", "packs") + ")", "chip soon"));
      shop.appendChild(row);
    });

    // the shelf, grouped
    var shelf = $("stock-shelf");
    clear(shelf);
    var groups = [];
    var byGroup = {};
    data.rows.forEach(function (r) {
      if (!byGroup[r.group]) {
        byGroup[r.group] = [];
        groups.push(r.group);
      }
      byGroup[r.group].push(r);
    });
    groups.forEach(function (g) {
      var panel = el("section", null, "panel");
      panel.appendChild(el("h3", g));
      byGroup[g].forEach(function (r) {
        panel.appendChild(stockRow(r));
      });
      shelf.appendChild(panel);
    });

    // for the curious
    var curious = $("stock-curious");
    clear(curious);
    curious.appendChild(el("p", "When stock is tight, the orders are served most urgent first (the same urgency score as the ChefBoard). " +
      "The usual way is first booked, first served.", "note"));
    curious.appendChild(el("p", "Urgency points kept covered: " + data.urgentFirstServed + ". In booking order: " + data.bookingOrderServed + ".", "note"));
    curious.appendChild(el("p", "Choosing the very best set of orders for many supplies at once is a hard problem, so this uses a simple rule that is easy to explain and runs in one pass.", "note"));
  }

  function stockRow(r) {
    var levelClass = r.level === "OK" ? "ok" : r.level === "LOW" ? "soon" : "late";
    var row = el("div", null, "stockrow-item " + levelClass);

    var head = el("div", null, "stockhead");
    head.appendChild(el("b", r.name));
    head.appendChild(el("span", LEVEL_WORDS[r.level] || r.level, "chip " + (r.level === "OK" ? "ok" : r.level === "LOW" ? "soon" : "late")));
    row.appendChild(head);

    var cap = Math.max(r.onHand, r.committed, r.lowLevel * 2, 1);
    var held = Math.min(r.committed, r.onHand);
    var free = Math.max(0, r.onHand - r.committed);
    var bar = el("div", null, "bar");
    var freePart = el("span", null, "bar-free " + levelClass);
    freePart.style.width = (free / cap * 100) + "%";
    var heldPart = el("span", null, "bar-held");
    heldPart.style.width = (held / cap * 100) + "%";
    var tick = el("span", null, "bar-tick");
    tick.style.left = (r.lowLevel / cap * 100) + "%";
    tick.title = "Reorder level";
    bar.appendChild(freePart);
    bar.appendChild(heldPart);
    bar.appendChild(tick);
    row.appendChild(bar);

    var numbers = "On the shelf " + fmtQty(r.onHand, r.unit);
    if (r.committed > 0) {
      numbers += "  ·  booked orders need " + fmtQty(r.committed, r.unit) + "  ·  left after " +
        (r.available < 0 ? "short by " + fmtQty(-r.available, r.unit) : fmtQty(r.available, r.unit));
    }
    numbers += "  ·  reorder at " + fmtQty(r.lowLevel, r.unit);
    row.appendChild(el("p", numbers, "note"));

    var unit = inputUnit(r.unit);
    var controls = el("div", null, "adjust");
    var input = el("input");
    input.type = "number";
    input.min = "0";
    input.step = "any";
    input.inputMode = "decimal";
    input.placeholder = "Amount";
    input.setAttribute("aria-label", "Amount of " + r.name + " in " + unit.label);
    controls.appendChild(input);
    controls.appendChild(el("span", unit.label, "qty-unit"));
    [["I bought more", 1, "plus"], ["Used some", -1, "trend-down"]].forEach(function (b) {
      var button = el("button", b[0], "btn soft compact");
      button.type = "button";
      addIcon(button, b[2]);
      button.addEventListener("click", function () {
        var amount = Number(input.value);
        if (!(amount > 0)) {
          toast("Type an amount first, for example 2.", "bad");
          input.focus();
          return;
        }
        adjustStock(r, b[1] * amount * unit.factor, button);
      });
      controls.appendChild(button);
    });
    row.appendChild(controls);
    return row;
  }

  function adjustStock(r, change, button) {
    button.disabled = true;
    callApi("POST", "/api/stock/adjust", { ingredientId: r.id, change: change }).then(function (res) {
      if (res.ok) {
        toast(r.name + ": now " + fmtQty(res.data.onHand, r.unit) + " on the shelf.", "ok");
        loadStock();
      } else {
        toast(errorText(res), "bad");
      }
    }).catch(function (e) {
      toast(e.message, "bad");
    }).then(function () {
      button.disabled = false;
    });
  }

  function resetStock() {
    callApi("POST", "/api/stock/reset", {}).then(function (res) {
      if (res.ok) {
        toast("Stock is back to the starting amounts.", "ok");
        loadStock();
      } else {
        toast(errorText(res), "bad");
      }
    }).catch(function (e) {
      toast(e.message, "bad");
    });
  }

  function markMade(id, button) {
    button.disabled = true;
    callApi("POST", "/api/orders/made", { id: id }).then(function (res) {
      if (res.ok) {
        toast("Marked as made. The ingredients came off the shelf.", "ok");
        closeDialog($("order-dialog"));
        loadDay();
      } else {
        toast(errorText(res), "bad");
        button.disabled = false;
      }
    }).catch(function (e) {
      toast(e.message, "bad");
      button.disabled = false;
    });
  }

  // ----- the product picker on the New order screen

  function currentFamilies() {
    var data = state.stock.data;
    if (!data) {
      return [];
    }
    var type = state.draft.type;
    return data.families.filter(function (f) { return !type || f.type === type; });
  }

  function renderProduct() {
    var d = state.draft;
    var chips = $("family-chips");
    if (!chips) {
      return;
    }
    clear(chips);
    var families = currentFamilies();
    if (d.family && !families.some(function (f) { return f.id === d.family; })) {
      d.family = null;                       // the kind of order changed, so the old product no longer fits
      clear($("stock-check"));
    }
    families.forEach(function (f) {
      chips.appendChild(pressChip(f.label, d.family === f.id, function () {
        if (d.family === f.id) {
          d.family = null;
          clear($("stock-check"));
        } else {
          d.family = f.id;
          d.quantity = f.minQty;
        }
        renderProduct();
        runCheck();
      }));
    });
    var chosen = families.filter(function (f) { return f.id === d.family; })[0];
    $("qty-row").hidden = !chosen;
    if (chosen) {
      $("qty-label").textContent = "How many?";
      $("qty-unit").textContent = chosen.unit;
      if (document.activeElement !== $("f-qty")) {
        $("f-qty").value = String(d.quantity);
      }
    }
  }

  function setQuantity(value) {
    var n = Math.floor(Number(value));
    if (!(n >= 1)) {
      n = 1;
    }
    state.draft.quantity = n;
    $("f-qty").value = String(n);
    scheduleCheck();
  }

  function scheduleCheck() {
    clearTimeout(checkTimer);
    checkTimer = setTimeout(runCheck, 250);
  }

  function runCheck() {
    var d = state.draft;
    var box = $("stock-check");
    if (!d.family) {
      clear(box);
      return;
    }
    var ticket = ++checkTicket;
    callApi("POST", "/api/orders/check" + query({ now: state.pretendNow }), { family: d.family, quantity: d.quantity }).then(function (res) {
      if (ticket !== checkTicket) {
        return;                                // a newer question was asked meanwhile
      }
      clear(box);
      var card = el("div", null, "result " + (res.ok ? (res.data.possible ? "ok" : "bad") : "bad"));
      var heading = el("h3", res.ok ? res.data.message : errorText(res));
      heading.insertBefore(iconSvg(res.ok && res.data.possible ? "check" : "alert"), heading.firstChild);
      card.appendChild(heading);
      if (res.ok) {
        if (res.data.unitsStillPossible >= 0 && res.data.limitedBy) {
          card.appendChild(el("p", "With what is free now, about " + res.data.unitsStillPossible + " more " + res.data.unit +
            " of " + res.data.family.toLowerCase() + " could still be made. Running out first: " + res.data.limitedBy + ".", "note"));
        }
        var list = el("ul", null, "checklist");
        res.data.lines.forEach(function (l) {
          var text = l.name + ": needs " + fmtQty(l.use, l.unit) + ", " +
            (l.availableAfter < 0 ? "short by " + fmtQty(-l.availableAfter, l.unit) : fmtQty(l.availableAfter, l.unit) + " left after");
          list.appendChild(el("li", text, l.availableAfter < 0 ? "short" : ""));
        });
        card.appendChild(list);
      }
      box.appendChild(card);
    }).catch(function (e) {
      clear(box);
      box.appendChild(el("p", e.message, "note"));
    });
  }

  // ===================================================================== start-up

  function goTo(view) {
    showView(view);
  }

  function init() {
    fillStaticIcons();

    document.querySelectorAll(".navitem").forEach(function (item) {
      item.title = item.querySelector(".lbl").textContent;
      item.addEventListener("click", function () { goTo(item.getAttribute("data-view")); });
    });
    document.querySelectorAll("[data-go]").forEach(function (b) {
      b.addEventListener("click", function () { goTo(b.getAttribute("data-go")); });
    });
    window.addEventListener("hashchange", function () {
      var wanted = location.hash.replace("#", "");
      wanted = LEGACY[wanted] || wanted;
      if (VIEWS.indexOf(wanted) >= 0 && wanted !== state.view) {
        showView(wanted);
      }
    });
    window.addEventListener("resize", movePill);

    // menu: collapse on a computer, slide-in drawer on a phone
    var collapsed = document.documentElement.getAttribute("data-collapsed") === "1";
    $("collapse").setAttribute("aria-pressed", collapsed ? "true" : "false");
    $("collapse").setAttribute("aria-label", collapsed ? "Expand the menu" : "Collapse the menu");
    $("collapse").addEventListener("click", function () {
      setCollapsed(document.documentElement.getAttribute("data-collapsed") !== "1");
    });
    $("menu-btn").addEventListener("click", openMenu);
    $("scrim").addEventListener("click", closeMenu);

    // new order slide-over, order details and the delete confirmation
    $("top-new").addEventListener("click", openDrawer);
    $("ov-new").addEventListener("click", openDrawer);
    ["drawer", "order-dialog", "confirm-dialog"].forEach(function (id) {
      var d = $(id);
      d.addEventListener("cancel", function (e) { e.preventDefault(); closeDialog(d); });
      d.addEventListener("click", function (e) { if (e.target === d) { closeDialog(d); } });
    });
    $("dr-close").addEventListener("click", closeDrawer);
    $("od-close").addEventListener("click", function () { closeDialog($("order-dialog")); });
    $("cd-cancel").addEventListener("click", function () { closeDialog($("confirm-dialog")); });
    $("cd-ok").addEventListener("click", doClearAll);

    $("day-prev").addEventListener("click", function () { goToDay(addDays(state.day, -1)); });
    $("day-next").addEventListener("click", function () { goToDay(addDays(state.day, 1)); });
    $("day-today").addEventListener("click", function () { goToDay(baseToday()); });
    $("day-pick").addEventListener("change", function () {
      if ($("day-pick").value) {
        goToDay($("day-pick").value);
      }
    });
    $("pretend-reset").addEventListener("click", resetPretend);

    $("order-search").addEventListener("input", function () {
      state.search = $("order-search").value;
      state.shown = PAGE_SIZE;
      renderOrders();
    });
    $("orders-more").addEventListener("click", function () {
      state.shown += PAGE_SIZE;
      renderOrders();
    });

    ["f-name", "f-phone", "f-item"].forEach(function (id) {
      $(id).addEventListener("input", renderSummary);
    });
    $("f-time").addEventListener("input", function () {
      if ($("f-time").value) {
        state.draft.time = $("f-time").value;
        renderSummary();
      }
    });
    $("save-order").addEventListener("click", saveOrder);
    $("qty-minus").addEventListener("click", function () { setQuantity(state.draft.quantity - 1); });
    $("qty-plus").addEventListener("click", function () { setQuantity(state.draft.quantity + 1); });
    $("f-qty").addEventListener("input", function () {
      var n = Math.floor(Number($("f-qty").value));
      if (n >= 1) {
        state.draft.quantity = n;
        scheduleCheck();
      }
    });
    $("f-qty").addEventListener("change", function () { setQuantity($("f-qty").value); });
    $("stock-reset").addEventListener("click", resetStock);
    $("demo-load").addEventListener("click", loadDemo);
    $("demo-clear").addEventListener("click", clearAll);

    var wanted = location.hash.replace("#", "");
    var opensForm = wanted === "new";
    wanted = LEGACY[wanted] || wanted;
    showView(VIEWS.indexOf(wanted) >= 0 ? wanted : "overview");
    renderDayBar();
    renderMore();
    renderBehind();

    loadPlaces().then(function () {
      renderForm();
      renderMap();
      if (opensForm) {
        openDrawer();
      }
    }, function (e) {
      showProblem(e.message);
    });
    loadDay();
  }

  init();
})();
