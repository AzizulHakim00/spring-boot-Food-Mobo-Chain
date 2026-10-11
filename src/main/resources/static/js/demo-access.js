(() => {
  "use strict";

  const dialog = document.getElementById("demoAccessDialog");
  const open = document.getElementById("demoAccessOpen");
  const close = document.getElementById("demoAccessClose");
  const sellerToggle = document.getElementById("demoSellerToggle");
  const sellerList = document.getElementById("demoSellerList");
  if (!dialog || !open || !close || !sellerToggle || !sellerList) return;

  open.addEventListener("click", () => dialog.showModal());
  close.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });

  sellerToggle.addEventListener("click", () => {
    const expanded = sellerToggle.getAttribute("aria-expanded") === "true";
    sellerToggle.setAttribute("aria-expanded", String(!expanded));
    sellerList.hidden = expanded;
    if (!expanded) sellerList.querySelector("button")?.focus();
  });
})();
