/* ElectroKichel · Órdenes de servicio. Funciona dentro del WebView con el puente AndroidBridge
   (getItem/setItem/deleteItem/openWhatsApp/savePdfBase64/printPage/toast). Sin internet. */
(function () {
  "use strict";
  var B = window.AndroidBridge || {};
  var CUR = { COP: "COL$", USD: "US$", MXN: "MX$", ARS: "AR$", PEN: "S/", CLP: "CL$", EUR: "€", BRL: "R$" };

  // ---- almacenamiento (puente nativo con respaldo a localStorage) ----
  function dbGet(k) { try { return B.getItem ? B.getItem(k) : localStorage.getItem(k); } catch (e) { return localStorage.getItem(k); } }
  function dbSet(k, v) { try { if (B.setItem) B.setItem(k, v); else localStorage.setItem(k, v); } catch (e) { localStorage.setItem(k, v); } }
  function toast(m) { try { if (B.toast) B.toast(m); } catch (e) {} }

  function loadOrders() { try { return JSON.parse(dbGet("orders") || "[]"); } catch (e) { return []; } }
  function saveOrders(o) { dbSet("orders", JSON.stringify(o)); }
  function loadSettings() {
    var d = { bizName: "ElectroKichel", tagline: "Soluciones en tecnología", nit: "", phone: "", nextNum: 1, currency: "COP",
      clause: document.getElementById("s_clause").value };
    try { return Object.assign(d, JSON.parse(dbGet("settings") || "{}")); } catch (e) { return d; }
  }
  function saveSettings(s) { dbSet("settings", JSON.stringify(s)); }

  var $ = function (id) { return document.getElementById(id); };
  var settings = loadSettings();
  var pattern = [], pin = "", payState = "", faultSel = null, equipType = "";

  // ---- navegación ----
  document.querySelectorAll(".pill[data-view]").forEach(function (p) {
    p.addEventListener("click", function () {
      document.querySelectorAll(".pill[data-view]").forEach(function (x) { x.classList.remove("active"); });
      p.classList.add("active");
      ["form", "list", "acc", "settings"].forEach(function (v) { $("view-" + v).classList.add("viewhide"); });
      $("view-" + p.dataset.view).classList.remove("viewhide");
      if (p.dataset.view === "list") renderList();
      if (p.dataset.view === "acc") renderAcc();
    });
  });
  $("themeToggle").addEventListener("click", function () {
    document.documentElement.classList.toggle("light");
    this.textContent = document.documentElement.classList.contains("light") ? "🌙 Oscuro" : "☀️ Claro";
  });

  // ---- subtabs ----
  document.querySelectorAll(".subtab").forEach(function (t) {
    t.addEventListener("click", function () {
      document.querySelectorAll(".subtab").forEach(function (x) { x.classList.remove("on"); });
      document.querySelectorAll(".subpanel").forEach(function (x) { x.classList.remove("on"); });
      t.classList.add("on"); $("panel-" + t.dataset.tab).classList.add("on");
    });
  });

  // ---- tipo de equipo + diagnóstico ----
  document.querySelectorAll(".typebtn").forEach(function (b) {
    b.addEventListener("click", function () {
      document.querySelectorAll(".typebtn").forEach(function (x) { x.classList.remove("sel"); });
      b.classList.add("sel"); equipType = b.dataset.type; renderFaults(equipType);
    });
  });
  function renderFaults(type) {
    var grid = $("diagFaultsGrid"), empty = $("diagEmptyMsg");
    grid.innerHTML = ""; faultSel = null;
    var list = (window.DIAGNOSTICOS || {})[type] || [];
    if (!list.length) { empty.style.display = ""; return; }
    empty.style.display = "none";
    list.forEach(function (f) {
      var c = document.createElement("div"); c.className = "diagfault-card"; c.textContent = f.t;
      c.addEventListener("click", function () {
        document.querySelectorAll(".diagfault-card").forEach(function (x) { x.classList.remove("sel"); });
        c.classList.add("sel"); faultSel = f;
        var r = $("f_repair"); r.value = (r.value ? r.value + "\n" : "") + f.d;
      });
      grid.appendChild(c);
    });
  }

  // ---- patrón ----
  var pg = $("patternGrid");
  for (var i = 1; i <= 9; i++) {
    var d = document.createElement("div"); d.className = "pdot"; d.dataset.n = i;
    d.addEventListener("click", function () {
      var n = parseInt(this.dataset.n);
      if (pattern.indexOf(n) < 0) { pattern.push(n); this.classList.add("on"); drawPattern(); }
    });
    pg.appendChild(d);
  }
  function drawPattern() {
    var svg = $("patternLines"); svg.innerHTML = "";
    for (var k = 1; k < pattern.length; k++) {
      var a = pos(pattern[k - 1]), b = pos(pattern[k]);
      var l = document.createElementNS("http://www.w3.org/2000/svg", "line");
      l.setAttribute("x1", a.x); l.setAttribute("y1", a.y); l.setAttribute("x2", b.x); l.setAttribute("y2", b.y);
      l.setAttribute("stroke", "#a97bff"); l.setAttribute("stroke-width", "4");
      svg.appendChild(l);
    }
  }
  function pos(n) { var r = Math.floor((n - 1) / 3), c = (n - 1) % 3; return { x: 37 + c * 73, y: 37 + r * 73 }; }
  $("clearPattern").addEventListener("click", function () { pattern = []; $("patternLines").innerHTML = ""; document.querySelectorAll(".pdot").forEach(function (x) { x.classList.remove("on"); }); });
  $("tabPattern").addEventListener("click", function () { $("patternBox").classList.remove("viewhide"); $("pinBox").classList.add("viewhide"); });
  $("tabPin").addEventListener("click", function () { $("pinBox").classList.remove("viewhide"); $("patternBox").classList.add("viewhide"); });

  // ---- PIN ----
  var pad = $("pinPad");
  ["1","2","3","4","5","6","7","8","9","⌫","0","OK"].forEach(function (k) {
    var b = document.createElement("button"); b.textContent = k;
    b.addEventListener("click", function () {
      if (k === "⌫") pin = pin.slice(0, -1); else if (k === "OK") {} else pin += k;
      $("pinDisplay").textContent = pin.replace(/./g, "•");
    });
    pad.appendChild(b);
  });
  $("clearPin").addEventListener("click", function () { pin = ""; $("pinDisplay").textContent = ""; });

  // ---- pago ----
  $("btnPaid").addEventListener("click", function () { payState = "paid"; this.classList.add("on"); $("btnPending").classList.remove("on"); });
  $("btnPending").addEventListener("click", function () { payState = "pending"; this.classList.add("on"); $("btnPaid").classList.remove("on"); });

  // ---- cálculos ----
  ["f_budget", "f_deposit", "f_surchargePct"].forEach(function (id) { $(id).addEventListener("input", recalc); });
  function recalc() {
    var budget = +$("f_budget").value || 0, dep = +$("f_deposit").value || 0, pct = +$("f_surchargePct").value || 0;
    var sur = budget * pct / 100, total = budget + sur, bal = total - dep;
    $("f_surchargeAmt").value = sur.toFixed(2); $("f_total").value = total.toFixed(2); $("f_balance").value = bal.toFixed(2);
    $("bigTotal").textContent = money(bal);
  }
  function money(n) { return (CUR[settings.currency] || "$") + " " + (Math.round(n * 100) / 100).toLocaleString(); }

  $("btnCalcWarranty").addEventListener("click", function () {
    var out = $("f_dateOut").value, days = +$("f_warrantyDays").value || 0;
    if (!out) { toast("Cargá la fecha de entrega primero"); return; }
    var d = new Date(out); d.setDate(d.getDate() + days);
    $("f_warrantyUntil").value = d.toISOString().slice(0, 10);
  });

  // ---- orden nueva/guardar ----
  function newOrder() {
    document.querySelectorAll("#view-form input, #view-form textarea").forEach(function (x) { if (!x.readOnly) x.value = ""; });
    $("f_budget").value = "0"; $("f_deposit").value = "0"; $("f_surchargePct").value = "0";
    $("f_warrantyDays").value = "30"; $("f_date").value = new Date().toISOString().slice(0, 10);
    pattern = []; pin = ""; payState = ""; faultSel = null; equipType = "";
    $("patternLines").innerHTML = ""; $("pinDisplay").textContent = "";
    document.querySelectorAll(".pdot,.typebtn,.diagfault-card").forEach(function (x) { x.classList.remove("on", "sel"); });
    $("btnPaid").classList.remove("on"); $("btnPending").classList.remove("on");
    settings = loadSettings();
    $("f_orderNum").value = settings.nextNum; $("f_docNum").value = "FAC-" + String(settings.nextNum).padStart(5, "0");
    recalc();
  }
  $("btnNew").addEventListener("click", newOrder);

  function collect() {
    return {
      orderNum: +$("f_orderNum").value, date: $("f_date").value, client: $("f_client").value, phone: $("f_phone").value,
      dni: $("f_dni").value, docNum: $("f_docNum").value, address: $("f_address").value, payMethod: $("f_payMethod").value,
      type: equipType, brand: $("f_brand").value, model: $("f_model").value, imei: $("f_imei").value, access: $("f_access").value,
      damages: $("f_damages").value, fault: $("f_fault").value, repair: $("f_repair").value,
      pattern: pattern.slice(), pin: pin, dateIn: $("f_dateIn").value, dateOut: $("f_dateOut").value,
      budget: +$("f_budget").value || 0, deposit: +$("f_deposit").value || 0, surchargePct: +$("f_surchargePct").value || 0,
      total: +$("f_total").value || 0, balance: +$("f_balance").value || 0,
      warrantyUntil: $("f_warrantyUntil").value, warrantyDays: +$("f_warrantyDays").value || 0,
      paid: payState, currency: settings.currency,
      status: payState === "paid" ? "entregado" : "recibido",
    };
  }
  $("btnSave").addEventListener("click", function () {
    var o = collect();
    if (!o.client) { toast("Falta el nombre del cliente"); return; }
    var all = loadOrders(); var idx = all.findIndex(function (x) { return x.orderNum === o.orderNum; });
    if (idx >= 0) all[idx] = o; else all.push(o);
    saveOrders(all);
    settings.nextNum = Math.max(settings.nextNum, o.orderNum + 1); saveSettings(settings);
    toast("Orden #" + o.orderNum + " guardada ✅");
  });

  // ---- PDF / impresión (print-to-PDF nativo) ----
  $("btnPdf").addEventListener("click", function () {
    buildTicket(collect()); try { if (B.printPage) B.printPage(); else window.print(); } catch (e) { window.print(); }
  });

  // ---- WhatsApp ----
  $("btnWa").addEventListener("click", function () {
    var o = collect();
    if (!o.phone) { toast("Esta orden no tiene teléfono"); return; }
    var L = [];
    L.push("*" + settings.bizName + "* — Orden #" + o.orderNum);
    L.push("*Cliente:* " + o.client);
    L.push("*Equipo:* " + [o.type, o.brand, o.model].filter(Boolean).join(" "));
    if (o.fault) L.push("*Falla declarada:* " + o.fault);
    if (o.repair) L.push("*Trabajo realizado:* " + o.repair);
    L.push("*Presupuesto:* " + money(o.budget));
    if (o.surchargePct) L.push("*Recargo:* " + money(o.budget * o.surchargePct / 100) + " (" + o.surchargePct + "%)");
    if (o.deposit) L.push("*Abonado:* " + money(o.deposit));
    L.push("*Saldo a cobrar:* " + money(o.balance));
    L.push("*Estado del pago:* " + (o.paid === "paid" ? "PAGADO ✅" : "PENDIENTE ⏳"));
    if (o.dateOut) L.push("*Fecha de entrega:* " + o.dateOut);
    if (o.warrantyUntil) L.push("*Garantía (" + o.warrantyDays + " días):* válida hasta " + o.warrantyUntil);
    L.push(""); L.push(settings.clause || "");
    var text = L.join("\n");
    try { if (B.openWhatsApp) B.openWhatsApp(o.phone, text); else window.open("https://wa.me/" + o.phone.replace(/\D/g, "") + "?text=" + encodeURIComponent(text)); } catch (e) {}
  });

  function buildTicket(o) {
    var t = $("ticketPrint");
    t.innerHTML = "<h3 style='text-align:center;margin:2px'>" + settings.bizName + "</h3>" +
      "<div style='text-align:center'>" + (settings.tagline || "") + "</div>" +
      (settings.nit ? "<div style='text-align:center'>NIT: " + settings.nit + "</div>" : "") +
      "<hr style='border:none;border-top:1px dashed #000'>" +
      "<div>Orden N°: " + o.orderNum + "   " + o.docNum + "</div>" +
      "<div>Fecha: " + o.date + "</div>" +
      "<div>Cliente: " + o.client + "</div>" +
      (o.phone ? "<div>Tel: " + o.phone + "</div>" : "") +
      "<div>Equipo: " + [o.type, o.brand, o.model].filter(Boolean).join(" ") + "</div>" +
      (o.fault ? "<div>Falla: " + o.fault + "</div>" : "") +
      (o.repair ? "<div>Trabajo: " + o.repair + "</div>" : "") +
      "<hr style='border:none;border-top:1px dashed #000'>" +
      "<div>Presupuesto: " + money(o.budget) + "</div>" +
      (o.surchargePct ? "<div>Recargo: " + money(o.budget * o.surchargePct / 100) + "</div>" : "") +
      (o.deposit ? "<div>Abono: " + money(o.deposit) + "</div>" : "") +
      "<div><b>Saldo: " + money(o.balance) + "</b></div>" +
      "<div>Pago: " + (o.paid === "paid" ? "PAGADO" : "PENDIENTE") + "</div>" +
      (o.warrantyUntil ? "<div>Garantía: hasta " + o.warrantyUntil + "</div>" : "") +
      "<hr style='border:none;border-top:1px dashed #000'>" +
      "<div style='font-size:9px'>" + (settings.clause || "") + "</div>";
  }

  // ---- lista ----
  function renderList() {
    var cont = $("ordersContainer"); var q = ($("searchOrders").value || "").toLowerCase();
    var all = loadOrders().filter(function (o) {
      return !q || (o.client + " " + o.orderNum + " " + o.brand + " " + o.model).toLowerCase().indexOf(q) >= 0;
    });
    if (!all.length) { cont.innerHTML = "<div class='empty'>No hay órdenes</div>"; return; }
    cont.innerHTML = "";
    all.reverse().forEach(function (o) {
      var card = document.createElement("div"); card.className = "ordercard";
      card.innerHTML = "<div style='flex:1;min-width:180px'><b>#" + o.orderNum + " · " + o.client + "</b>" +
        "<span class='badge " + o.status + "'>" + o.status + "</span>" +
        "<div style='font-size:11.5px;color:var(--dim)'>" + [o.type, o.brand, o.model].filter(Boolean).join(" ") + " · " + money(o.balance) + "</div></div>";
      var acts = document.createElement("div");
      var edit = btn("Abrir", "btn-blue", function () { openOrder(o); });
      var del = btn("Eliminar", "btn-red", function () { if (confirm("¿Eliminar la orden #" + o.orderNum + "?")) { var a = loadOrders().filter(function (x) { return x.orderNum !== o.orderNum; }); saveOrders(a); renderList(); } });
      acts.appendChild(edit); acts.appendChild(del); card.appendChild(acts); cont.appendChild(card);
    });
  }
  function btn(txt, cls, fn) { var b = document.createElement("button"); b.className = "btn " + cls; b.textContent = txt; b.style.marginLeft = "4px"; b.addEventListener("click", fn); return b; }
  $("searchOrders").addEventListener("input", renderList);

  function openOrder(o) {
    document.querySelector(".pill[data-view=form]").click();
    $("f_orderNum").value = o.orderNum; $("f_date").value = o.date || ""; $("f_client").value = o.client || "";
    $("f_phone").value = o.phone || ""; $("f_dni").value = o.dni || ""; $("f_docNum").value = o.docNum || "";
    $("f_address").value = o.address || ""; $("f_brand").value = o.brand || ""; $("f_model").value = o.model || "";
    $("f_imei").value = o.imei || ""; $("f_access").value = o.access || ""; $("f_damages").value = o.damages || "";
    $("f_fault").value = o.fault || ""; $("f_repair").value = o.repair || ""; $("f_dateIn").value = o.dateIn || "";
    $("f_dateOut").value = o.dateOut || ""; $("f_budget").value = o.budget || 0; $("f_deposit").value = o.deposit || 0;
    $("f_surchargePct").value = o.surchargePct || 0; $("f_warrantyUntil").value = o.warrantyUntil || "";
    $("f_warrantyDays").value = o.warrantyDays || 30;
    equipType = o.type || ""; payState = o.paid || ""; pattern = o.pattern || []; pin = o.pin || "";
    if (payState === "paid") $("btnPaid").classList.add("on"); if (payState === "pending") $("btnPending").classList.add("on");
    recalc();
  }

  // ---- contabilidad ----
  function renderAcc() {
    var all = loadOrders();
    $("statCount").textContent = all.length;
    $("statTotal").textContent = money(all.reduce(function (s, o) { return s + (o.total || 0); }, 0));
    $("statPending").textContent = money(all.reduce(function (s, o) { return s + (o.balance || 0); }, 0));
    var body = $("accBody");
    body.innerHTML = all.map(function (o) {
      return "<div class='ordercard'><div style='flex:1'>#" + o.orderNum + " · " + o.client + "</div><div>" + money(o.total) + " · saldo " + money(o.balance) + "</div></div>";
    }).join("") || "<div class='empty'>Sin datos</div>";
  }

  // ---- configuración ----
  function fillSettings() {
    $("s_bizName").value = settings.bizName; $("s_tagline").value = settings.tagline; $("s_nit").value = settings.nit || "";
    $("s_phone").value = settings.phone || ""; $("s_nextNum").value = settings.nextNum; $("s_currency").value = settings.currency;
    if (settings.clause) $("s_clause").value = settings.clause;
    $("bizName").textContent = settings.bizName.toUpperCase(); $("bizTagline").textContent = settings.tagline;
  }
  $("btnSaveSettings").addEventListener("click", function () {
    settings = { bizName: $("s_bizName").value, tagline: $("s_tagline").value, nit: $("s_nit").value, phone: $("s_phone").value,
      nextNum: +$("s_nextNum").value || 1, currency: $("s_currency").value, clause: $("s_clause").value };
    saveSettings(settings); fillSettings(); toast("Configuración guardada ✅");
  });

  // ---- init ----
  fillSettings(); newOrder();
})();
