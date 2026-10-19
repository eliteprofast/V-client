"use strict";

// Admin grant tool. The token only lives in memory, so closing the tab signs you out.
const $ = (id) => document.getElementById(id);
// The page may be opened from the static site, which has no API, so always talk to the backend.
const BACKEND = "https://vsmp-client-backend.onrender.com";
const API = ["localhost", "127.0.0.1"].includes(location.hostname) || location.hostname.includes("vsmp-client-backend") ? "" : BACKEND;
let token = "";
let names = {};
const ADMIN_UUID = "1331c81d33844eb6baf6a511c8298af3";

function say(text, ok) {
  const box = $("message");
  box.textContent = text;
  box.className = ok ? "ok" : "bad";
}

async function call(method, path, body) {
  const response = await fetch(`${API}/api${path}`, {
    method,
    headers: { "Content-Type": "application/json", Accept: "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status})`);
  return data;
}

const label = (id) => (names[id] ? `${names[id]} (${id})` : id);

async function signIn() {
  try {
    const data = await call("POST", "/auth/login", { login: $("user").value.trim(), password: $("pass").value });
    if (data.user.role !== "admin") throw new Error("That account is not an administrator");
    token = data.token;
    $("pass").value = "";
    $("login").hidden = true;
    $("tools").hidden = false;
    await loadLists();
    say("Signed in.", true);
  } catch (error) {
    say(error.message, false);
  }
}

async function loadLists() {
  const ids = await (await fetch("cosmetic-ids.json")).json();
  const select = $("cosmetic");
  select.replaceChildren();
  for (const item of ids.filter((entry) => entry.tier !== "free")) {
    names[item.id] = item.name;
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent = `${item.name} - ${item.category} (${item.tier})`;
    select.append(option);
  }
  const rewards = (await call("GET", "/admin/cosmetics/rewards")).items;
  const invites = $("invites");
  const ladder = $("ladder");
  invites.replaceChildren();
  ladder.replaceChildren();
  for (const tier of rewards) {
    const option = document.createElement("option");
    option.value = tier.invites;
    option.textContent = `${tier.invites} invites`;
    invites.append(option);
    const row = ladder.insertRow();
    row.insertCell().textContent = `${tier.invites} invites`;
    row.insertCell().textContent = tier.cosmetics.map(label).join(", ");
  }
}

async function showOwned() {
  const player = $("player").value.trim();
  if (!player) return say("Enter a player first.", false);
  try {
    // Granting nothing resolves the name; use the grants list for the UUID once we have it.
    const uuids = await resolve(player);
    const lines = [];
    for (const uuid of uuids) {
      const data = await call("GET", `/admin/cosmetics/grants?uuid=${uuid}`);
      const owned = data.items.length ? data.items.map((item) => label(item.cosmeticId)).join(", ") : "Nothing granted yet.";
      lines.push(`${uuids.length > 1 ? uuid.slice(0, 8) + "…: " : ""}${uuid === ADMIN_UUID ? "Admin account: every cosmetic is unlocked automatically. " : ""}${owned}`);
    }
    $("owned").textContent = lines.join("  |  ");
    say(`Player ${uuids.join(" and ")}`, true);
  } catch (error) {
    say(error.message, false);
  }
}

// A name can be an offline account and an Ely.by account, which have different UUIDs; both are returned.
async function resolve(player) {
  const compact = player.replace(/-/g, "").toLowerCase();
  if (/^[0-9a-f]{32}$/.test(compact)) return [compact];
  const data = await call("POST", "/admin/cosmetics/grants", { player, invites: 0 });
  return data.uuids;
}

async function grant() {
  try {
    const data = await call("POST", "/admin/cosmetics/grants", { player: $("player").value.trim(), cosmeticId: $("cosmetic").value });
    say(`Unlocked ${label(data.cosmeticId)} for ${data.uuids.length > 1 ? "both accounts of " : ""}${$("player").value.trim()} (${data.uuids.join(", ")})`, true);
    showOwned();
  } catch (error) {
    say(error.message, false);
  }
}

async function revoke() {
  try {
    const uuids = await resolve($("player").value.trim());
    for (const uuid of uuids) {
      try {
        await call("DELETE", "/admin/cosmetics/grants", { uuid, cosmeticId: $("cosmetic").value });
      } catch (error) {
        if (!/not found/i.test(error.message)) throw error;
      }
    }
    say(`Took ${label($("cosmetic").value)} away from ${uuids.join(" and ")}`, true);
    showOwned();
  } catch (error) {
    say(error.message, false);
  }
}

async function reward() {
  try {
    const data = await call("POST", "/admin/cosmetics/grants", { player: $("player").value.trim(), invites: Number($("invites").value) });
    say(`Gave ${data.granted.map(label).join(", ")} to ${data.uuids.join(" and ")}`, true);
    showOwned();
  } catch (error) {
    say(error.message, false);
  }
}

$("signin").addEventListener("click", signIn);
$("pass").addEventListener("keydown", (event) => { if (event.key === "Enter") signIn(); });
$("show").addEventListener("click", showOwned);
$("grant").addEventListener("click", grant);
$("revoke").addEventListener("click", revoke);
$("reward").addEventListener("click", reward);
