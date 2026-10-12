/* Refreshes the Spring Security masked CSRF token for buyer checkout and payment forms.
 * Does not disable CSRF and never sends a form without a valid same-origin token.
 */
(() => {
  "use strict";
  document.querySelectorAll("form[data-fresh-csrf]").forEach((form) => {
    form.addEventListener("submit", async (event) => {
      if (form.dataset.csrfReady === "yes") return;
      event.preventDefault();
      if (form.dataset.csrfChecking === "yes") return;
      form.dataset.csrfChecking = "yes";
      const submitter = event.submitter;
      try {
        const response = await fetch("/checkout/form-token", {
          method: "GET",
          credentials: "same-origin",
          cache: "no-store",
          headers: { "Accept": "application/json" }
        });
        if (response.redirected && new URL(response.url).pathname === "/login") {
          window.location.assign("/login");
          return;
        }
        if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) {
          throw new Error("Unable to refresh form security token");
        }
        const body = await response.json();
        if (!body.token || typeof body.token !== "string") {
          throw new Error("Missing form security token");
        }
        let hidden = form.querySelector('input[name="_csrf"]');
        if (!hidden) {
          hidden = document.createElement("input");
          hidden.type = "hidden";
          hidden.name = "_csrf";
          form.appendChild(hidden);
        }
        hidden.value = body.token;
        form.dataset.csrfReady = "yes";
        form.requestSubmit(submitter || undefined);
      } catch (error) {
        const host = form.querySelector(".form-card") || form.closest(".form-card") || form;
        let notice = host.querySelector("[data-csrf-submit-error]");
        if (!notice) {
          notice = document.createElement("p");
          notice.className = "field-error csrf-submit-error";
          notice.setAttribute("role", "alert");
          notice.dataset.csrfSubmitError = "";
          host.prepend(notice);
        }
        notice.textContent = "We could not verify this form. Refresh the page and try again. You may need to sign in again.";
      } finally {
        delete form.dataset.csrfChecking;
      }
    }, { capture: true });
  });
})();
