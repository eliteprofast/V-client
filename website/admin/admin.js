"use strict";

const $ = (selector, root = document) => root.querySelector(selector);
const TOKEN_KEY = "vclient-admin-token";
let token = null;
try { token = sessionStorage.getItem(TOKEN_KEY); } catch { /* storage blocked: stay signed-in for this page only */ }

// All text goes through textContent, never innerHTML, so content typed by anyone can't inject markup.
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else if (value === true) node.setAttribute(key, "");
    else if (value !== false && value !== null && value !== undefined) node.setAttribute(key, value);
  }
  node.append(...children.flat().filter((child) => child !== null && child !== undefined && child !== false));
  return node;
}

let toastTimer;
function toast(message, isError = false) {
  const node = $("#toast");
  node.textContent = message;
  node.className = `toast${isError ? " error" : ""}`;
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.hidden = true; }, 4000);
}

async function api(method, path, body) {
  const response = await fetch(`/api${path}`, {
    method,
    headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    if (token) signOut();
    throw new Error(data.error || "Not authorised");
  }
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status})`);
  return data;
}

const run = async (action, success) => {
  try {
    await action();
    if (success) toast(success);
  } catch (error) {
    toast(error.message, true);
  }
};

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleString() : "—");
const fmtSize = (bytes) => (bytes ? `${(bytes / 1048576).toFixed(1)} MB` : "—");
const localInput = (iso) => (iso ? new Date(new Date(iso).getTime() - new Date(iso).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : "");
const isoFromLocal = (value) => (value ? new Date(value).toISOString() : null);

// ---- Sign in / out -------------------------------------------------------------
function signOut() {
  token = null;
  try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
  showLogin();
}

function showLogin() {
  $("#app-view").hidden = true;
  $("#login-view").hidden = false;
}

async function showApp() {
  const { user } = await api("GET", "/auth/me");
  if (user.role !== "admin") {
    signOut();
    throw new Error("This account is not an administrator");
  }
  $("#who").textContent = user.username;
  $("#login-view").hidden = true;
  $("#app-view").hidden = false;
  openTab(currentTab);
}

$("#login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  $("#login-error").textContent = "";
  try {
    const result = await api("POST", "/auth/login", { login: form.get("login"), password: form.get("password") });
    token = result.token;
    try { sessionStorage.setItem(TOKEN_KEY, token); } catch { /* ignore */ }
    event.target.reset();
    await showApp();
  } catch (error) {
    $("#login-error").textContent = error.message;
  }
});
$("#logout").addEventListener("click", signOut);

// ---- Tabs ----------------------------------------------------------------------
let currentTab = "dashboard";
const renderers = { dashboard: renderDashboard, news: renderNews, announcements: renderAnnouncements, releases: renderReleases, users: renderUsers };

function openTab(name) {
  currentTab = name;
  document.querySelectorAll(".admin-tabs button").forEach((button) => button.setAttribute("aria-selected", String(button.dataset.tab === name)));
  document.querySelectorAll(".tab").forEach((section) => { section.hidden = section.id !== `tab-${name}`; });
  run(() => renderers[name]($(`#tab-${name}`)));
}
document.querySelectorAll(".admin-tabs button").forEach((button) => button.addEventListener("click", () => openTab(button.dataset.tab)));

// ---- Dashboard -----------------------------------------------------------------
async function renderDashboard(root) {
  const stats = await api("GET", "/admin/stats");
  const stat = (label, value) => el("div", { class: "stat" }, el("small", {}, label), el("b", {}, String(value)));
  const max = Math.max(1, ...stats.launchesPerDay.map((d) => d.launches));
  const chart = stats.launchesPerDay.length
    ? [el("div", { class: "chart" }, stats.launchesPerDay.map((d) => el("div", { class: "bar", style: `height:${Math.max(4, (d.launches / max) * 100)}%`, title: `${d.day}: ${d.launches} launches, ${d.installs} installs` }, el("span", {}, String(d.launches))))),
      el("div", { class: "chart-labels" }, stats.launchesPerDay.map((d) => el("span", {}, d.day.slice(5))))]
    : [el("p", { class: "muted" }, "No launches reported yet.")];
  const table = (headers, rows) => el("table", {}, el("thead", {}, el("tr", {}, headers.map((h) => el("th", {}, h)))),
    el("tbody", {}, rows.length ? rows.map((r) => el("tr", {}, r.map((c) => el("td", {}, String(c))))) : [el("tr", {}, el("td", { colspan: headers.length, class: "muted" }, "No data yet"))]));
  root.replaceChildren(
    el("h2", {}, "Dashboard"),
    el("div", { class: "stat-grid" },
      stat("Accounts", stats.users.total), stat("New accounts (7d)", stats.users.newLast7Days),
      stat("Active installs (24h)", stats.activeInstalls.last24Hours), stat("Active installs (7d)", stats.activeInstalls.last7Days),
      stat("Active installs (30d)", stats.activeInstalls.last30Days), stat("News posts", stats.content.newsPosts)),
    el("div", { class: "panel" }, el("h3", {}, "Launches, last 14 days"), ...chart),
    el("div", { class: "two-col", style: "margin-top:16px" },
      el("div", { class: "panel" }, el("h3", {}, "Installs by version (30d)"), table(["Platform", "Version", "Installs"], stats.byVersion.map((v) => [v.platform, v.version, v.installs]))),
      el("div", { class: "panel" }, el("h3", {}, "Downloads"), table(["Platform", "Version", "Downloads"], stats.downloads.map((d) => [d.platform, d.version, d.downloads])))),
  );
}

// ---- News ----------------------------------------------------------------------
async function renderNews(root) {
  const { items } = await api("GET", "/admin/news");
  const form = el("form", { class: "panel form-grid", onsubmit: (event) => {
    event.preventDefault();
    const data = new FormData(form);
    run(async () => {
      await api("POST", "/admin/news", { title: data.get("title"), body: data.get("body"), category: data.get("category"), pinned: data.get("pinned") === "on", published: data.get("published") === "on" });
      await renderNews(root);
    }, "News posted");
  } },
    el("h3", {}, "New post"),
    el("label", {}, "Title", el("input", { name: "title", maxlength: 140, required: true })),
    el("label", {}, "Body (blank line = new paragraph)", el("textarea", { name: "body", maxlength: 8000, required: true })),
    el("div", { class: "row" },
      el("label", {}, "Category", el("select", { name: "category" }, el("option", { value: "launcher" }, "Launcher"), el("option", { value: "server" }, "Server"))),
      el("label", { class: "check" }, el("input", { type: "checkbox", name: "pinned" }), "Pin to top"),
      el("label", { class: "check" }, el("input", { type: "checkbox", name: "published", checked: true }), "Publish now")),
    el("div", {}, el("button", { class: "button button-primary", type: "submit" }, "Post")));
  root.replaceChildren(el("h2", {}, "News"), form, ...items.map((post) => el("article", { class: "item" },
    el("h3", {}, post.title),
    el("div", { class: "meta" }, el("span", { class: `pill ${post.category === "server" ? "ok" : ""}` }, post.category),
      post.pinned && el("span", { class: "pill warn" }, "pinned"), !post.published && el("span", { class: "pill bad" }, "draft"), el("span", {}, fmtDate(post.createdAt))),
    el("p", {}, post.body),
    el("div", { class: "actions" },
      el("button", { class: "button ghost small", type: "button", onclick: () => run(async () => { await api("PUT", `/admin/news/${post.id}`, { published: !post.published }); await renderNews(root); }) }, post.published ? "Unpublish" : "Publish"),
      el("button", { class: "button ghost small", type: "button", onclick: () => run(async () => { await api("PUT", `/admin/news/${post.id}`, { pinned: !post.pinned }); await renderNews(root); }) }, post.pinned ? "Unpin" : "Pin"),
      el("button", { class: "button danger small", type: "button", onclick: () => { if (confirm(`Delete “${post.title}”?`)) run(async () => { await api("DELETE", `/admin/news/${post.id}`); await renderNews(root); }, "Deleted"); } }, "Delete")))));
}

// ---- Announcements -------------------------------------------------------------
async function renderAnnouncements(root) {
  const { items } = await api("GET", "/admin/announcements");
  const form = el("form", { class: "panel form-grid", onsubmit: (event) => {
    event.preventDefault();
    const data = new FormData(form);
    run(async () => {
      await api("POST", "/admin/announcements", { message: data.get("message"), level: data.get("level"), startsAt: isoFromLocal(data.get("startsAt")), endsAt: isoFromLocal(data.get("endsAt")) });
      await renderAnnouncements(root);
    }, "Announcement created");
  } },
    el("h3", {}, "New announcement"),
    el("p", { class: "muted" }, "Shown as a banner on the website and inside the Windows and Android launchers while active."),
    el("label", {}, "Message", el("input", { name: "message", maxlength: 300, required: true })),
    el("div", { class: "row" },
      el("label", {}, "Level", el("select", { name: "level" }, el("option", { value: "info" }, "Info"), el("option", { value: "warning" }, "Warning"), el("option", { value: "critical" }, "Critical"))),
      el("label", {}, "Starts (optional)", el("input", { type: "datetime-local", name: "startsAt" })),
      el("label", {}, "Ends (optional)", el("input", { type: "datetime-local", name: "endsAt" }))),
    el("div", {}, el("button", { class: "button button-primary", type: "submit" }, "Create")));
  root.replaceChildren(el("h2", {}, "Announcements"), form, ...items.map((a) => el("article", { class: "item" },
    el("h3", {}, a.message),
    el("div", { class: "meta" }, el("span", { class: `pill ${a.level === "critical" ? "bad" : a.level === "warning" ? "warn" : ""}` }, a.level),
      el("span", { class: `pill ${a.active ? "ok" : "bad"}` }, a.active ? "active" : "off"),
      el("span", {}, `${a.startsAt ? fmtDate(a.startsAt) : "now"} → ${a.endsAt ? fmtDate(a.endsAt) : "no end"}`)),
    el("div", { class: "actions" },
      el("button", { class: "button ghost small", type: "button", onclick: () => run(async () => { await api("PUT", `/admin/announcements/${a.id}`, { active: !a.active }); await renderAnnouncements(root); }) }, a.active ? "Turn off" : "Turn on"),
      el("button", { class: "button danger small", type: "button", onclick: () => { if (confirm("Delete this announcement?")) run(async () => { await api("DELETE", `/admin/announcements/${a.id}`); await renderAnnouncements(root); }, "Deleted"); } }, "Delete")))));
}

// ---- Launcher updates ----------------------------------------------------------
function uploadFile(releaseId, file, onProgress) {
  return new Promise((resolve, reject) => {
    const ext = file.name.slice(file.name.lastIndexOf(".")).toLowerCase();
    const request = new XMLHttpRequest();
    request.open("PUT", `/api/admin/releases/${releaseId}/file?ext=${encodeURIComponent(ext)}`);
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    request.setRequestHeader("Content-Type", "application/octet-stream");
    request.upload.onprogress = (event) => event.lengthComputable && onProgress(event.loaded / event.total);
    request.onload = () => {
      let data = {};
      try { data = JSON.parse(request.responseText); } catch { /* not json */ }
      if (request.status >= 200 && request.status < 300) resolve(data); else reject(new Error(data.error || `Upload failed (${request.status})`));
    };
    request.onerror = () => reject(new Error("Network error during upload"));
    request.send(file);
  });
}

async function renderReleases(root) {
  const { items } = await api("GET", "/admin/releases");
  const form = el("form", { class: "panel form-grid", onsubmit: (event) => {
    event.preventDefault();
    const data = new FormData(form);
    run(async () => {
      await api("POST", "/admin/releases", { platform: data.get("platform"), version: data.get("version"), notes: data.get("notes"), minSupported: data.get("minSupported") || "0.0.0", mandatory: data.get("mandatory") === "on" });
      await renderReleases(root);
    }, "Release created. Upload the installer, then publish it.");
  } },
    el("h3", {}, "New launcher release"),
    el("div", { class: "row" },
      el("label", {}, "Platform", el("select", { name: "platform" }, el("option", { value: "windows" }, "Windows (.exe)"), el("option", { value: "android" }, "Android (.apk)"))),
      el("label", {}, "Version (e.g. 1.2.0)", el("input", { name: "version", pattern: "\\d+(\\.\\d+){0,3}", required: true })),
      el("label", {}, "Minimum supported version", el("input", { name: "minSupported", placeholder: "0.0.0", pattern: "\\d+(\\.\\d+){0,3}" }))),
    el("label", {}, "Release notes", el("textarea", { name: "notes", maxlength: 4000 })),
    el("label", { class: "check" }, el("input", { type: "checkbox", name: "mandatory" }), "Mandatory update (users must install before continuing)"),
    el("div", {}, el("button", { class: "button button-primary", type: "submit" }, "Create release")));

  root.replaceChildren(el("h2", {}, "Launcher updates"), form, ...items.map((release) => {
    const progress = el("progress", { max: 1, value: 0, hidden: true });
    const picker = el("input", { type: "file", accept: release.platform === "android" ? ".apk" : ".exe,.msi,.zip", hidden: true, onchange: () => {
      const file = picker.files[0];
      if (!file) return;
      progress.hidden = false;
      run(async () => {
        await uploadFile(release.id, file, (fraction) => { progress.value = fraction; });
        await renderReleases(root);
      }, "Installer uploaded");
    } });
    return el("article", { class: "item" },
      el("h3", {}, `${release.platform === "windows" ? "Windows" : "Android"} ${release.version}`),
      el("div", { class: "meta" },
        el("span", { class: `pill ${release.published ? "ok" : "warn"}` }, release.published ? "published" : "draft"),
        release.mandatory && el("span", { class: "pill bad" }, "mandatory"),
        el("span", {}, release.hasFile ? `${fmtSize(release.size)} · ${release.downloads} downloads` : "no installer uploaded"),
        el("span", {}, `min supported ${release.minSupported}`)),
      release.sha256 && el("p", { class: "muted", style: "font:11px Consolas,monospace;word-break:break-all" }, `SHA-256 ${release.sha256}`),
      release.notes && el("p", {}, release.notes),
      progress,
      el("div", { class: "actions" }, picker,
        el("button", { class: "button ghost small", type: "button", onclick: () => picker.click() }, release.hasFile ? "Replace installer" : "Upload installer"),
        el("button", { class: "button ghost small", type: "button", disabled: !release.hasFile, onclick: () => run(async () => { await api("PUT", `/admin/releases/${release.id}`, { published: !release.published }); await renderReleases(root); }, release.published ? "Unpublished" : "Published") }, release.published ? "Unpublish" : "Publish"),
        el("button", { class: "button danger small", type: "button", onclick: () => { if (confirm(`Delete ${release.platform} ${release.version} and its installer?`)) run(async () => { await api("DELETE", `/admin/releases/${release.id}`); await renderReleases(root); }, "Deleted"); } }, "Delete")));
  }));
}

// ---- Users ---------------------------------------------------------------------
async function renderUsers(root) {
  const { items } = await api("GET", "/admin/users");
  root.replaceChildren(el("h2", {}, `Users (${items.length})`), el("div", { class: "panel", style: "overflow-x:auto" },
    el("table", {}, el("thead", {}, el("tr", {}, ["Username", "Email", "Role", "Minecraft", "Ely.by", "Joined", "Last login"].map((h) => el("th", {}, h)))),
      el("tbody", {}, items.map((u) => el("tr", {}, [u.username, u.email, u.role, u.minecraftName || "—", u.elybyName || "—", fmtDate(u.createdAt), fmtDate(u.lastLoginAt)].map((c) => el("td", {}, c))))))));
}

// ---- Start ---------------------------------------------------------------------
if (token) showApp().catch(() => showLogin()); else showLogin();
