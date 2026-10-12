/* Promo previews are advisory. The server recalculates discounts in a MongoDB transaction at checkout. */
(() => {
  "use strict";
  const summary = document.getElementById("checkoutSummary");
  const codeInput = document.getElementById("discountCode");
  const applyButton = document.getElementById("applyPromo");
  const feedback = document.getElementById("promoFeedback");
  const amount = document.getElementById("checkoutDiscountAmount");
  const row = document.getElementById("checkoutDiscountRow");
  const total = document.getElementById("checkoutFinalTotal");
  if (!summary || !codeInput || !applyButton || !feedback || !amount || !row || !total) return;

  const base = Number(summary.dataset.baseTotal);
  if (!Number.isFinite(base)) return;
  const displayMoney = (value) => "৳" + new Intl.NumberFormat("en-BD", {
    minimumFractionDigits: value % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2
  }).format(value);
  let requestVersion = 0;
  let feedbackTimeout = null;

  function resetPreview() {
    row.hidden = true;
    amount.textContent = "−" + displayMoney(0);
    total.textContent = displayMoney(base);
  }
  function message(text, kind) {
    if (feedbackTimeout !== null) clearTimeout(feedbackTimeout);
    feedbackTimeout = null;
    feedback.textContent = text;
    feedback.classList.toggle("is-error", kind === "error");
    feedback.classList.toggle("is-success", kind === "success");
    feedback.classList.toggle("is-loading", kind === "loading");
    if (text && kind !== "loading") {
      feedbackTimeout = setTimeout(() => {
        feedback.textContent = "";
        feedback.classList.remove("is-error", "is-success");
      }, 5500);
    }
  }

  codeInput.addEventListener("input", () => {
    requestVersion++;
    applyButton.disabled = false;
    resetPreview();
    message("", "");
  });

  applyButton.addEventListener("click", async () => {
    const version = ++requestVersion;
    const code = codeInput.value.trim();
    resetPreview();
    if (!code) {
      message("Enter a promo code before applying.", "error");
      return;
    }
    if (code.length > 40) {
      message("A promo code must be 40 characters or fewer.", "error");
      return;
    }

    applyButton.disabled = true;
    message("Checking promo code…", "loading");
    try {
      const response = await fetch("/checkout/discount-preview?code=" + encodeURIComponent(code), {
        method: "GET", credentials: "same-origin", headers: { "Accept": "application/json" },
        cache: "no-store"
      });
      const result = await response.json();
      if (version !== requestVersion) return;
      if (!response.ok || !result.valid) {
        message(result.message || "This promo code is not available.", "error");
        return;
      }

      const discount = Number(result.discount);
      const finalTotal = Number(result.total);
      if (!Number.isFinite(discount) || !Number.isFinite(finalTotal) ||
          discount < 0 || finalTotal < 0) {
        throw new Error("Unexpected promo calculation.");
      }
      row.hidden = discount === 0;
      amount.textContent = "−" + displayMoney(discount);
      total.textContent = displayMoney(finalTotal);
      message(result.message || "Promo applied.", "success");
    } catch (error) {
      if (version === requestVersion) {
        message("Could not verify the promo. Try again or continue without a code.", "error");
      }
    } finally {
      if (version === requestVersion) applyButton.disabled = false;
    }
  });
})();
