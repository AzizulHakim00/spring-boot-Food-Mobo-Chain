(() => {
  "use strict";

  const $all = (selector, root = document) => [...root.querySelectorAll(selector)];

  function initializeNavigation() {
    const toggle = document.querySelector(".nav-toggle");
    const menu = document.querySelector(".nav-links");
    if (!toggle || !menu) return;

    const setOpen = (open) => {
      menu.classList.toggle("open", open);
      document.body.classList.toggle("nav-open", open);
      toggle.setAttribute("aria-expanded", String(open));
    };

    toggle.addEventListener("click", () => setOpen(!menu.classList.contains("open")));
    $all("a", menu).forEach((link) => link.addEventListener("click", () => setOpen(false)));
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") setOpen(false);
    });
    window.addEventListener("resize", () => {
      if (window.innerWidth > 1000) setOpen(false);
    });
  }

  function initializeToasts() {
    $all("[data-toast]").forEach((toast) => {
      requestAnimationFrame(() => toast.classList.add("show"));
      window.setTimeout(() => toast.classList.remove("show"), 4200);
    });
  }

  function createConfirmationDialog() {
    const root = document.createElement("div");
    root.className = "confirm-dialog";
    root.setAttribute("aria-hidden", "true");
    root.innerHTML = `
      <div class="confirm-dialog-backdrop" data-confirm-close></div>
      <section class="confirm-dialog-card" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-message">
        <button class="confirm-dialog-close" type="button" aria-label="Close confirmation" data-confirm-close>&times;</button>
        <div class="confirm-dialog-icon" aria-hidden="true">!</div>
        <div class="confirm-dialog-copy">
          <span class="confirm-dialog-kicker">Please confirm</span>
          <h2 id="confirm-dialog-title">Confirm action</h2>
          <p id="confirm-dialog-message">Are you sure?</p>
        </div>
        <div class="confirm-dialog-actions">
          <button class="button ghost confirm-dialog-cancel" type="button" data-confirm-close>Keep current state</button>
          <button class="button confirm-dialog-confirm" type="button">Confirm</button>
        </div>
      </section>`;
    document.body.appendChild(root);
    return root;
  }

  function initializeConfirmations() {
    const forms = $all("form[data-confirm]");
    if (!forms.length) return;

    const dialog = createConfirmationDialog();
    const card = dialog.querySelector(".confirm-dialog-card");
    const title = dialog.querySelector("#confirm-dialog-title");
    const message = dialog.querySelector("#confirm-dialog-message");
    const confirmButton = dialog.querySelector(".confirm-dialog-confirm");
    const cancelButton = dialog.querySelector(".confirm-dialog-cancel");
    let pendingForm = null;
    let pendingSubmitter = null;
    let returnFocus = null;

    const close = () => {
      dialog.classList.remove("open", "danger");
      dialog.setAttribute("aria-hidden", "true");
      document.body.classList.remove("modal-open");
      pendingForm = null;
      pendingSubmitter = null;
      if (returnFocus && typeof returnFocus.focus === "function") returnFocus.focus();
      returnFocus = null;
    };

    const open = (form, submitter) => {
      pendingForm = form;
      pendingSubmitter = submitter || form.querySelector("button[type='submit'], button:not([type]), input[type='submit']");
      returnFocus = pendingSubmitter || document.activeElement;

      const actionText = (form.dataset.confirmAction || pendingSubmitter?.textContent || "Confirm").trim();
      const confirmMessage = (form.dataset.confirm || "Are you sure you want to continue?").trim();
      const dangerous = form.dataset.confirmVariant === "danger" ||
        pendingSubmitter?.classList.contains("danger") ||
        /cancel|delete|remove|clear|archive|suspend|hide|sign out|logout/i.test(actionText + " " + confirmMessage);

      title.textContent = form.dataset.confirmTitle || (actionText ? `${actionText.replace(/[?.!]$/, "")}?` : "Confirm action");
      message.textContent = confirmMessage;
      confirmButton.textContent = actionText || "Confirm";
      cancelButton.textContent = form.dataset.confirmCancel || "Keep current state";
      dialog.classList.toggle("danger", dangerous);
      confirmButton.classList.toggle("danger", dangerous);
      dialog.classList.add("open");
      dialog.setAttribute("aria-hidden", "false");
      document.body.classList.add("modal-open");
      window.setTimeout(() => cancelButton.focus(), 0);
    };

    forms.forEach((form) => {
      form.addEventListener("submit", (event) => {
        if (form.dataset.confirmed === "true") {
          delete form.dataset.confirmed;
          return;
        }
        event.preventDefault();
        open(form, event.submitter);
      });
    });

    confirmButton.addEventListener("click", () => {
      if (!pendingForm) return;
      const form = pendingForm;
      const submitter = pendingSubmitter;
      dialog.classList.remove("open");
      dialog.setAttribute("aria-hidden", "true");
      document.body.classList.remove("modal-open");
      pendingForm = null;
      pendingSubmitter = null;
      returnFocus = null;
      form.dataset.confirmed = "true";
      if (typeof form.requestSubmit === "function") form.requestSubmit(submitter || undefined);
      else form.submit();
    });

    $all("[data-confirm-close]", dialog).forEach((element) => element.addEventListener("click", close));
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && dialog.classList.contains("open")) close();
    });
    card.addEventListener("click", (event) => event.stopPropagation());
  }

  function initializePasswordToggle() {
    $all("[data-password-toggle]").forEach((button) => {
      button.addEventListener("click", () => {
        const input = document.getElementById(button.dataset.passwordToggle);
        if (!input) return;
        input.type = input.type === "password" ? "text" : "password";
        button.textContent = input.type === "password" ? "Show" : "Hide";
        button.setAttribute("aria-pressed", String(input.type !== "password"));
      });
    });
  }

  function initializeSubmitLoading() {
    $all("form[data-loading]").forEach((form) => {
      form.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return;
        const button = event.submitter || form.querySelector("button[type='submit'], button:not([type])");
        if (!button) return;
        button.disabled = true;
        button.dataset.originalText = button.textContent;
        button.textContent = button.dataset.loadingText || "Processing…";
      });
    });
  }

  function initializeImagePreview() {
    $all("input[data-image-preview]").forEach((input) => {
      input.addEventListener("input", () => {
        const preview = document.getElementById(input.dataset.imagePreview);
        if (preview && input.value) preview.src = input.value;
      });
    });
  }

  function initializeImageFallbacks() {
    const applyFallback = (image) => {
      if (image.dataset.fallbackApplied === "true") return;
      const raw = image.getAttribute("src") || "";
      const isBrandAsset = /\/images\/(payment|logo|icons)\//.test(raw);
      if (isBrandAsset) return;

      image.dataset.fallbackApplied = "true";
      if (raw.includes("/images/carts/") || image.closest(".vendor-card, .vendor-hero")) {
        image.src = "/images/carts/cart-default.webp";
      } else {
        image.src = "/images/ui/food-placeholder.svg";
      }
    };

    $all("img").forEach((image) => {
      image.addEventListener("error", () => applyFallback(image));
      if (image.complete && image.naturalWidth === 0) applyFallback(image);
    });
  }

  function initializeCharacterCounters() {
    $all("[data-max-counter]").forEach((input) => {
      const target = document.getElementById(input.dataset.maxCounter);
      if (!target) return;
      const refresh = () => target.textContent = `${input.value.length}/${input.maxLength}`;
      input.addEventListener("input", refresh);
      refresh();
    });
  }

  function initializeYear() {
    $all("[data-year]").forEach((element) => element.textContent = new Date().getFullYear());
  }

  function initializeReveal() {
    if (!("IntersectionObserver" in window)) {
      $all(".reveal").forEach((element) => element.classList.add("visible"));
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.08 });
    $all(".reveal").forEach((element) => observer.observe(element));
  }

  initializeNavigation();
  initializeToasts();
  initializeConfirmations();
  initializePasswordToggle();
  initializeSubmitLoading();
  initializeImagePreview();
  initializeImageFallbacks();
  initializeCharacterCounters();
  initializeYear();
  initializeReveal();
})();

