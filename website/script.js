"use strict";

// A single-file setup executable that installs the bundled app and starts it.
const DOWNLOAD_URL = "./VSMPClientSetup.exe";
// Replace this with your server's invite URL before publishing.
const DISCORD_URL = "https://discord.gg/";

document.querySelectorAll(".download-link").forEach((link) => {
  link.href = DOWNLOAD_URL;
});

document.querySelectorAll(".discord-link").forEach((link) => {
  link.href = DISCORD_URL;
});

const menuButton = document.querySelector(".menu-toggle");
const navigation = document.querySelector("#site-nav");

if (menuButton && navigation) {
  menuButton.addEventListener("click", () => {
    const isOpen = menuButton.getAttribute("aria-expanded") === "true";
    menuButton.setAttribute("aria-expanded", String(!isOpen));
    navigation.classList.toggle("is-open", !isOpen);
  });

  navigation.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => {
      menuButton.setAttribute("aria-expanded", "false");
      navigation.classList.remove("is-open");
    });
  });
}

const year = document.querySelector("#current-year");
if (year) year.textContent = String(new Date().getFullYear());
