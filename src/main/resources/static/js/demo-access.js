(() => {
  "use strict";

  const dialog = document.getElementById("demoAccessDialog");
  const trigger = document.getElementById("demoAccessOpen");
  const closeButton = document.getElementById("demoAccessClose");
  const useEmailButton = document.getElementById("demoUseEmail");
  const sellerSelect = document.getElementById("demoSellerSelect");
  const emailInput = document.getElementById("username");
  const passwordInput = document.getElementById("password");
  if (!dialog || !trigger || !closeButton || !useEmailButton || !sellerSelect ||
      !emailInput || !passwordInput) return;

  const tabs = [...dialog.querySelectorAll("[data-demo-role]")];
  const panels = [...dialog.querySelectorAll("[data-demo-panel]")];
  const demoEmails = {
    buyer: "buyer@foodmobo.local",
    admin: "admin@foodmobo.local"
  };
  let chosenRole = "buyer";

  function selectRole(role) {
    if (!["buyer", "seller", "admin"].includes(role)) return;
    chosenRole = role;
    tabs.forEach((tab) => {
      const active = tab.dataset.demoRole === role;
      tab.setAttribute("aria-selected", String(active));
      tab.tabIndex = active ? 0 : -1;
      tab.classList.toggle("active", active);
    });
    panels.forEach((panel) => {
      panel.hidden = panel.dataset.demoPanel !== role;
    });
  }

  trigger.addEventListener("click", () => {
    selectRole(chosenRole);
    dialog.showModal();
  });
  closeButton.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });

  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => selectRole(tab.dataset.demoRole));
    tab.addEventListener("keydown", (event) => {
      if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
      event.preventDefault();
      const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 :
        (i + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
      tabs[next].focus();
      selectRole(tabs[next].dataset.demoRole);
    });
  });

  useEmailButton.addEventListener("click", () => {
    const chosenEmail = chosenRole === "seller" ? sellerSelect.value : demoEmails[chosenRole];
    if (!chosenEmail) return;
    emailInput.value = chosenEmail;
    // Never transfer or retain credentials between demo roles.
    passwordInput.value = "";
    dialog.close();
    passwordInput.focus();
  });
  selectRole("buyer");
})();
