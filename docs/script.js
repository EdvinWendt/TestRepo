const openSwishButton = document.querySelector("#open-swish");
const swishStatus = document.querySelector("#swish-status");
const SWISH_PAYMENT_URL = "https://app.swish.nu/1/p/sw/";

const getQueryParameter = (name) => {
  const requestedName = name.toLowerCase();
  const queryParameters = new URLSearchParams(window.location.search);

  for (const [key, value] of queryParameters.entries()) {
    if (key.toLowerCase() === requestedName) {
      return value.trim();
    }
  }

  return "";
};

const normalizePhoneNumber = (phoneNumber) => phoneNumber.replace(/\D/g, "");

const normalizeAmount = (amount) => amount.trim().replace(",", ".");

const getMissingPaymentDetailMessage = (phone, amount) => {
  if (!phone) {
    return "Missing Phone in the link.";
  }

  if (!amount || Number.isNaN(Number(amount)) || Number(amount) <= 0) {
    return "Missing or invalid Amount in the link.";
  }

  return "";
};

const buildSwishUrl = () => {
  const phone = normalizePhoneNumber(getQueryParameter("Phone"));
  const amount = normalizeAmount(getQueryParameter("Amount"));
  const message = getQueryParameter("Message");
  const validationMessage = getMissingPaymentDetailMessage(phone, amount);

  if (validationMessage) {
    return { error: validationMessage, url: "" };
  }

  const swishUrl = new URL(SWISH_PAYMENT_URL);
  swishUrl.searchParams.set("sw", phone);
  swishUrl.searchParams.set("amt", amount);
  swishUrl.searchParams.set("cur", "SEK");
  swishUrl.searchParams.set("msg", message);
  swishUrl.searchParams.set("src", "qr");

  return { error: "", url: swishUrl.toString() };
};

if (openSwishButton && swishStatus) {
  openSwishButton.addEventListener("click", () => {
    const swishLink = buildSwishUrl();
    if (swishLink.error) {
      swishStatus.textContent = swishLink.error;
      return;
    }

    swishStatus.textContent = "Trying to open Swish...";

    const fallbackTimer = window.setTimeout(() => {
      if (document.visibilityState === "visible") {
        swishStatus.textContent = "If Swish did not open, make sure you are on a phone with Swish installed.";
      }
    }, 1600);

    const clearFallback = () => {
      window.clearTimeout(fallbackTimer);
      swishStatus.textContent = "";
    };

    window.addEventListener("pagehide", clearFallback, { once: true });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") {
        clearFallback();
      }
    }, { once: true });

    window.location.href = swishLink.url;
  });
}
