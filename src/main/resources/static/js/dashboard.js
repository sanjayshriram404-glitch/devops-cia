// Kubernetes control panel: polls /api/k8s/status and drives scale / restart / delete-pod.
(() => {
    "use strict";

    const POLL_MS = 3000;
    const FAST_POLL_MS = 1000;
    const FAST_POLL_FOR_MS = 20000;

    const $ = (id) => document.getElementById(id);
    const els = {
        live: $("live"), liveText: $("live-text"), rack: $("rack"), offline: $("offline"),
        offlineText: $("offline-text"), podsSummary: $("pods-summary"), count: $("count"),
        minus: $("minus"), plus: $("plus"), presets: $("presets"), scaleNote: $("scale-note"),
        restart: $("restart"), tokenRow: $("token-row"), token: $("token"),
        deployFacts: $("deploy-facts"), svcFacts: $("svc-facts"), events: $("events"), toast: $("toast"),
    };
    const servedBy = document.getElementById("served-by")?.textContent?.trim();

    let state = null;          // last ClusterStatus
    let target = null;         // replica count we asked for and are waiting on
    let lastOk = 0;
    let fastUntil = 0;
    let timer = null;

    els.token.value = sessionStorage.getItem("dashboardToken") || "";
    els.token.addEventListener("change", () => sessionStorage.setItem("dashboardToken", els.token.value));

    // ---------- API ----------
    async function api(method, path, body) {
        const headers = { "Content-Type": "application/json" };
        if (els.token.value) headers["X-Dashboard-Token"] = els.token.value;
        const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || `Request failed (${res.status}).`);
        return data;
    }

    async function refresh() {
        clearTimeout(timer);
        try {
            state = await fetch("/api/k8s/status", { cache: "no-store" }).then((r) => r.json());
            lastOk = Date.now();
            render();
        } catch (e) {
            els.live.className = "live bad";
            els.liveText.textContent = "Lost contact with the app. Retrying…";
        }
        timer = setTimeout(refresh, Date.now() < fastUntil ? FAST_POLL_MS : POLL_MS);
    }

    function pollFast() {
        fastUntil = Date.now() + FAST_POLL_FOR_MS;
        refresh();
    }

    // ---------- Actions ----------
    async function scaleTo(n) {
        if (!state?.connected) return;
        n = Math.max(1, Math.min(state.maxReplicas, n));
        if (n === state.deployment.desired && target === null) return;
        target = n;
        renderControls();
        try {
            await api("POST", "/api/k8s/scale", { replicas: n });
            toast(`Scaling to ${n} ${n === 1 ? "replica" : "replicas"}`);
            pollFast();
        } catch (e) {
            target = null;
            renderControls();
            toast(e.message, true);
        }
    }

    async function restartAll() {
        els.restart.disabled = true;
        try {
            await api("POST", "/api/k8s/restart");
            toast("Restarting pods one by one");
            pollFast();
        } catch (e) {
            toast(e.message, true);
        } finally {
            els.restart.disabled = !state?.connected;
        }
    }

    async function deletePod(name, button) {
        button.disabled = true;
        try {
            await api("DELETE", `/api/k8s/pods/${encodeURIComponent(name)}`);
            toast(`Deleted ${name}. Kubernetes will start a replacement.`);
            pollFast();
        } catch (e) {
            button.disabled = false;
            toast(e.message, true);
        }
    }

    els.minus.addEventListener("click", () => scaleTo(currentTarget() - 1));
    els.plus.addEventListener("click", () => scaleTo(currentTarget() + 1));
    els.restart.addEventListener("click", restartAll);

    function currentTarget() {
        return target ?? state?.deployment?.desired ?? 1;
    }

    // ---------- Rendering ----------
    function render() {
        els.live.className = state.connected ? "live ok" : "live bad";
        updateLiveText();

        els.offline.hidden = state.connected;
        els.rack.hidden = !state.connected;
        els.offlineText.textContent = state.connected ? "" : state.message;
        els.tokenRow.hidden = !state.tokenRequired;

        if (state.connected) {
            const d = state.deployment;
            if (target !== null && d.desired === target && d.ready === target) target = null;
            renderRack(state.pods);
            els.podsSummary.textContent = `${d.ready} of ${d.desired} ready`;
            renderFacts(els.deployFacts, [
                ["Ready", `${d.ready} / ${d.desired}`],
                ["Up to date", String(d.updated)],
                ["Image", imageTag(d.image), true],
                ["Revision", d.revision || "–"],
                ["Strategy", d.strategy || "–"],
            ]);
            const s = state.service;
            renderFacts(els.svcFacts, s ? [
                ["Type", s.type],
                ["Address", s.externalIp ? link(`http://${s.externalIp}/`) : "Waiting for a public IP"],
                ["Ports", s.ports.join(", ")],
            ] : [["Status", "No Service found"]]);
        } else {
            els.podsSummary.textContent = "";
            renderFacts(els.deployFacts, [["Status", "Unavailable"]]);
            renderFacts(els.svcFacts, [["Status", "Unavailable"]]);
        }
        renderControls();
        renderEvents(state.events || []);
    }

    function renderControls() {
        const connected = !!state?.connected;
        const max = state?.maxReplicas ?? 5;
        const shown = connected ? currentTarget() : null;

        els.count.textContent = shown ?? "–";
        els.count.classList.toggle("pending", target !== null);
        els.minus.disabled = !connected || shown <= 1;
        els.plus.disabled = !connected || shown >= max;
        els.restart.disabled = !connected;

        if (els.presets.childElementCount !== max) {
            els.presets.replaceChildren(...Array.from({ length: max }, (_, i) => {
                const b = document.createElement("button");
                b.type = "button";
                b.className = "preset";
                b.textContent = String(i + 1);
                b.setAttribute("aria-label", `Scale to ${i + 1}`);
                b.addEventListener("click", () => scaleTo(i + 1));
                return b;
            }));
        }
        [...els.presets.children].forEach((b, i) => {
            b.disabled = !connected;
            b.setAttribute("aria-pressed", String(shown === i + 1));
        });

        if (!connected) {
            els.scaleNote.textContent = "Scaling works once the app runs in the cluster.";
        } else if (target !== null) {
            els.scaleNote.textContent = `Scaling to ${target}. ${state.deployment.ready} ready so far.`;
        } else {
            els.scaleNote.textContent = `Pick a number to scale the Deployment. Limit on this cluster: ${max}.`;
        }
    }

    function renderRack(pods) {
        const existing = new Map([...els.rack.children].map((li) => [li.dataset.name, li]));
        const keep = new Set();
        pods.forEach((p) => {
            keep.add(p.name);
            let li = existing.get(p.name);
            if (!li) {
                li = buildSlot(p.name);
                els.rack.append(li);
            }
            updateSlot(li, p);
        });
        existing.forEach((li, name) => { if (!keep.has(name)) li.remove(); });
        // Keep DOM order equal to server order (oldest first). Only touch the DOM when the
        // order really changed: re-appending a node restarts its entrance animation.
        const order = pods.map((p) => p.name);
        const current = [...els.rack.children].map((li) => li.dataset.name);
        if (order.join() !== current.join()) {
            const byName = new Map([...els.rack.children].map((li) => [li.dataset.name, li]));
            order.forEach((name) => els.rack.append(byName.get(name)));
        }
    }

    function buildSlot(name) {
        const li = document.createElement("li");
        li.className = "slot";
        li.dataset.name = name;
        li.innerHTML = `<span class="led" aria-hidden="true"></span>
            <div><div class="pod-name"></div><div class="pod-status"></div></div>
            <div class="pod-meta"></div>
            <button type="button" class="kill">Delete pod</button>`;
        li.querySelector(".pod-name").textContent = name;
        const btn = li.querySelector(".kill");
        btn.setAttribute("aria-label", `Delete pod ${name}`);
        btn.addEventListener("click", () => deletePod(name, btn));
        if (name === servedBy) li.classList.add("served");
        return li;
    }

    function updateSlot(li, p) {
        li.dataset.state = podState(p);
        const status = li.querySelector(".pod-status");
        status.replaceChildren();
        const strong = document.createElement("strong");
        strong.textContent = p.status;
        status.append(strong, document.createTextNode(
            p.name === servedBy ? ", served this page" : p.ready ? ", ready" : ", not ready yet"));

        const meta = li.querySelector(".pod-meta");
        meta.replaceChildren(...[
            p.node ? `on ${p.node}` : "not scheduled yet",
            p.ip ? `IP ${p.ip}` : null,
            `${p.restarts} ${p.restarts === 1 ? "restart" : "restarts"}`,
            p.startTime ? `${ago(p.startTime)} old` : null,
        ].filter(Boolean).map((t) => { const s = document.createElement("span"); s.textContent = t; return s; }));

        li.querySelector(".kill").disabled = p.status === "Terminating";
    }

    function podState(p) {
        if (p.status === "Terminating") return "stopping";
        if (p.status === "Running" && p.ready) return "running";
        if (/Err|BackOff|Failed|Crash/i.test(p.status)) return "failed";
        return "starting";
    }

    function renderFacts(dl, rows) {
        dl.replaceChildren(...rows.flatMap(([k, v, code]) => {
            const dt = document.createElement("dt");
            dt.textContent = k;
            const dd = document.createElement("dd");
            if (v instanceof Node) dd.append(v); else dd.textContent = v;
            if (code) dd.className = "code";
            return [dt, dd];
        }));
    }

    function renderEvents(events) {
        if (!events.length) {
            const li = document.createElement("li");
            li.className = "event-empty";
            li.textContent = state.connected
                ? "No recent events. Scale or delete a pod and they will appear here."
                : "Events appear once the app runs in the cluster.";
            els.events.replaceChildren(li);
            return;
        }
        els.events.replaceChildren(...events.map((e) => {
            const li = document.createElement("li");
            const t = document.createElement("span");
            t.className = "event-time";
            t.textContent = e.time ? `${ago(e.time)} ago` : "";
            const r = document.createElement("span");
            r.className = "event-reason" + (e.type === "Warning" ? " warning" : "");
            r.textContent = e.reason;
            const m = document.createElement("span");
            m.className = "event-msg";
            const obj = document.createElement("span");
            obj.className = "code";
            obj.textContent = e.object + ": ";
            m.append(obj, document.createTextNode(e.message || ""));
            li.append(t, r, m);
            return li;
        }));
    }

    function updateLiveText() {
        if (!state) return;
        if (!state.connected) {
            els.liveText.textContent = "Not connected to Kubernetes";
            return;
        }
        const s = Math.round((Date.now() - lastOk) / 1000);
        els.liveText.textContent = `Live from namespace ${state.namespace}, updated ${s < 2 ? "just now" : s + "s ago"}`;
    }
    setInterval(updateLiveText, 1000);

    // ---------- Helpers ----------
    function imageTag(image) {
        const i = image.lastIndexOf("/");
        return i >= 0 ? image.slice(i + 1) : image;
    }

    function link(href) {
        const a = document.createElement("a");
        a.href = href;
        a.textContent = href;
        return a;
    }

    function ago(iso) {
        const s = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000));
        if (s < 60) return `${s}s`;
        if (s < 3600) return `${Math.floor(s / 60)}m`;
        if (s < 86400) return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
        return `${Math.floor(s / 86400)}d`;
    }

    let toastTimer = null;
    function toast(text, isError = false) {
        els.toast.textContent = text;
        els.toast.className = "toast" + (isError ? " error" : "");
        els.toast.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { els.toast.hidden = true; }, 3500);
    }

    refresh();
})();
