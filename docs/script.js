const openSwishButton = document.querySelector("#open-swish");
const swishStatus = document.querySelector("#swish-status");
const SWISH_PAYMENT_URL = "swish://payment?data=";

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

const normalizePhoneNumber = (phoneNumber) => {
  const trimmedPhoneNumber = phoneNumber.trim();
  const hasInternationalPrefix = trimmedPhoneNumber.startsWith("+");
  const digitsOnlyPhoneNumber = trimmedPhoneNumber.replace(/\D/g, "");

  if (!digitsOnlyPhoneNumber) {
    return "";
  }

  if (hasInternationalPrefix) {
    return `+${digitsOnlyPhoneNumber}`;
  }

  if (digitsOnlyPhoneNumber.startsWith("00")) {
    return `+${digitsOnlyPhoneNumber.slice(2)}`;
  }

  if (digitsOnlyPhoneNumber.startsWith("46")) {
    return `+${digitsOnlyPhoneNumber}`;
  }

  if (digitsOnlyPhoneNumber.startsWith("0")) {
    return `+46${digitsOnlyPhoneNumber.slice(1)}`;
  }

  return digitsOnlyPhoneNumber;
};

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

  const paymentData = {
    version: 1,
    payee: {
      value: phone
    },
    amount: {
      value: Number(amount)
    }
  };

  if (message) {
    paymentData.message = {
      value: message
    };
  }

  return {
    error: "",
    url: `${SWISH_PAYMENT_URL}${encodeURIComponent(JSON.stringify(paymentData))}`
  };
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
