const openSwishButton = document.querySelector("#open-swish");
const swishLoading = document.querySelector("#swish-loading");
const swishNotice = document.querySelector("#swish-notice");
const swishStatus = document.querySelector("#swish-status");
const SWISH_PAYMENT_REQUEST_URL = "swish://paymentrequest";
const SWISH_PAYMENT_URL = "swish://payment?data=";
const PAYMENT_REQUEST_QUERY_KEY = "request";
const SUPABASE_PAYMENT_REQUEST_LOOKUP_RPC = "get_payment_request_by_token";
const SUPABASE_HISTORY_PAYMENT_CARD_LOOKUP_RPC = "get_history_payment_card_by_short_id";
const SUPABASE_PAYMENT_REQUEST_OPENED_RPC = "mark_payment_request_opened";
const SUPABASE_PAYMENT_REQUEST_CALLBACK_RPC = "mark_payment_request_callback";

let cachedSupabasePaymentRequest = undefined;
let cachedSupabaseHistoryPaymentCard = undefined;
let compactHistoryLinkReady = true;

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
const getPaymentRequestToken = () =>
  getQueryParameter(PAYMENT_REQUEST_QUERY_KEY) || getQueryParameter("requestToken");
const getPaymentToken = () => getQueryParameter("PaymentToken") || getQueryParameter("Token");
const getReceiptShortId = () => getQueryParameter("R");
const getPaymentCardId = () => getQueryParameter("PC");
const hasCompactHistoryPaymentLink = () => !!(getReceiptShortId() && getPaymentCardId());

const getMissingPaymentDetailMessage = (phone, amount) => {
  if (!phone) {
    return "Missing Phone in the link.";
  }

  if (!amount || Number.isNaN(Number(amount)) || Number(amount) <= 0) {
    return "Missing or invalid Amount in the link.";
  }

  return "";
};

const showSwishLoading = (isLoading) => {
  if (swishLoading) {
    swishLoading.hidden = !isLoading;
  }

  if (openSwishButton) {
    openSwishButton.hidden = isLoading;
  }
};

const setSwishNotice = (message) => {
  if (swishNotice) {
    swishNotice.textContent = message;
  }
};

const buildCallbackUrl = () => {
  const callbackUrl = new URL(window.location.href);
  callbackUrl.searchParams.delete("PaymentToken");
  callbackUrl.searchParams.delete("paymenttoken");
  callbackUrl.searchParams.delete("Token");
  callbackUrl.searchParams.delete("token");
  callbackUrl.searchParams.set("swish-return", "1");
  return callbackUrl.toString();
};

const getSupabaseClient = () => {
  const config = window.KVITT_SUPABASE_CONFIG || {};
  const createClient = window.supabase?.createClient;

  if (typeof createClient !== "function" || !config.url || !config.publishableKey) {
    return null;
  }

  if (!window.kvittSupabaseClient) {
    window.kvittSupabaseClient = createClient(config.url, config.publishableKey);
  }

  return window.kvittSupabaseClient;
};

const loadSupabasePaymentRequest = async () => {
  if (cachedSupabasePaymentRequest !== undefined) {
    return cachedSupabasePaymentRequest;
  }

  const requestToken = getPaymentRequestToken();
  if (!requestToken) {
    cachedSupabasePaymentRequest = null;
    return cachedSupabasePaymentRequest;
  }

  const supabaseClient = getSupabaseClient();
  if (!supabaseClient) {
    cachedSupabasePaymentRequest = {
      found: false,
      error:
        "This payment link expects Supabase website config. Add your publishable key in docs/supabase-config.js."
    };
    return cachedSupabasePaymentRequest;
  }

  const { data, error } = await supabaseClient.rpc(SUPABASE_PAYMENT_REQUEST_LOOKUP_RPC, {
    request_token: requestToken
  });

  if (error) {
    cachedSupabasePaymentRequest = {
      found: false,
      error: "Unable to load payment details right now. Please try again in a moment."
    };
    return cachedSupabasePaymentRequest;
  }

  cachedSupabasePaymentRequest = data && data.found ? data : null;
  return cachedSupabasePaymentRequest;
};

const loadSupabaseHistoryPaymentCard = async () => {
  if (cachedSupabaseHistoryPaymentCard !== undefined) {
    return cachedSupabaseHistoryPaymentCard;
  }

  const receiptShortId = getReceiptShortId();
  const paymentCardId = getPaymentCardId();
  if (!receiptShortId || !paymentCardId) {
    cachedSupabaseHistoryPaymentCard = null;
    return cachedSupabaseHistoryPaymentCard;
  }

  const supabaseClient = getSupabaseClient();
  if (!supabaseClient) {
    cachedSupabaseHistoryPaymentCard = {
      found: false,
      error:
        "This payment link expects Supabase website config. Add your publishable key in docs/supabase-config.js."
    };
    return cachedSupabaseHistoryPaymentCard;
  }

  const { data, error } = await supabaseClient.rpc(
    SUPABASE_HISTORY_PAYMENT_CARD_LOOKUP_RPC,
    {
      receipt_short_id: receiptShortId,
      payment_card_id: paymentCardId
    }
  );

  if (error) {
    cachedSupabaseHistoryPaymentCard = {
      found: false,
      error: "Unable to load payment details right now. Please try again in a moment."
    };
    return cachedSupabaseHistoryPaymentCard;
  }

  cachedSupabaseHistoryPaymentCard = data && data.found ? data : null;
  return cachedSupabaseHistoryPaymentCard;
};

const reportPaymentRequestLifecycleEvent = async (rpcName) => {
  const requestToken = getPaymentRequestToken();
  const supabaseClient = getSupabaseClient();

  if (!requestToken || !supabaseClient) {
    return;
  }

  try {
    await supabaseClient.rpc(rpcName, {
      request_token: requestToken
    });
  } catch (error) {
    console.warn(`Failed to call ${rpcName}.`, error);
  }
};

const initializeCompactHistoryPaymentState = async () => {
  if (!openSwishButton || !hasCompactHistoryPaymentLink()) {
    return;
  }

  compactHistoryLinkReady = false;
  openSwishButton.disabled = true;
  setSwishNotice("");
  swishStatus.textContent = "";
  showSwishLoading(true);

  try {
    const historyPaymentCard = await loadSupabaseHistoryPaymentCard();
    showSwishLoading(false);

    if (historyPaymentCard?.error) {
      swishStatus.textContent = historyPaymentCard.error;
      return;
    }

    if (!historyPaymentCard) {
      setSwishNotice("Receipt ID not found.");
      return;
    }

    if (historyPaymentCard.hasPaid) {
      setSwishNotice(
        "This payment has been marked as paid. Before clicking the button, check your Swish history so you don't accidentally pay twice."
      );
    }

    compactHistoryLinkReady = true;
    openSwishButton.disabled = false;
  } catch (error) {
    showSwishLoading(false);
    swishStatus.textContent = "Unable to load payment details right now. Please try again in a moment.";
  }
};

const resolvePaymentDetails = async () => {
  const paymentRequest = await loadSupabasePaymentRequest();
  const historyPaymentCard = await loadSupabaseHistoryPaymentCard();

  if (paymentRequest?.error) {
    return { error: paymentRequest.error, message: "", amount: "", mode: "", phone: "", paymentToken: "" };
  }

  if (historyPaymentCard?.error) {
    return { error: historyPaymentCard.error, message: "", amount: "", mode: "", phone: "", paymentToken: "" };
  }

  if (paymentRequest) {
    return {
      error: "",
      message: paymentRequest.message || "",
      amount: String(paymentRequest.amount ?? ""),
      mode: paymentRequest.swishPaymentToken ? "paymentrequest" : "direct",
      phone: paymentRequest.payeePhone || "",
      paymentToken: paymentRequest.swishPaymentToken || ""
    };
  }

  if (historyPaymentCard) {
    return {
      error: "",
      message: historyPaymentCard.message || "",
      amount: String(historyPaymentCard.amount ?? ""),
      mode: "direct",
      phone: historyPaymentCard.recipientPhone || "",
      paymentToken: ""
    };
  }

  if (hasCompactHistoryPaymentLink()) {
    return {
      error: "This payment link is invalid or no longer available.",
      message: "",
      amount: "",
      mode: "",
      phone: "",
      paymentToken: ""
    };
  }

  return {
    error: "",
    message: getQueryParameter("Message"),
    amount: getQueryParameter("Amount"),
    mode: "",
    phone: getQueryParameter("Phone"),
    paymentToken: getPaymentToken()
  };
};

const buildSwishUrl = async () => {
  const paymentDetails = await resolvePaymentDetails();
  if (paymentDetails.error) {
    return { error: paymentDetails.error, mode: "", url: "" };
  }

  const paymentToken = paymentDetails.paymentToken;
  if (paymentToken) {
    return {
      error: "",
      mode: "paymentrequest",
      url:
        `${SWISH_PAYMENT_REQUEST_URL}?token=${encodeURIComponent(paymentToken)}` +
        `&callbackurl=${encodeURIComponent(buildCallbackUrl())}`
    };
  }

  const phone = normalizePhoneNumber(paymentDetails.phone);
  const amount = normalizeAmount(paymentDetails.amount);
  const message = paymentDetails.message;
  const validationMessage = getMissingPaymentDetailMessage(phone, amount);

  if (validationMessage) {
    return { error: validationMessage, mode: "", url: "" };
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
    mode: "direct",
    url: `${SWISH_PAYMENT_URL}${encodeURIComponent(JSON.stringify(paymentData))}`
  };
};

if (openSwishButton && swishStatus) {
  void reportPaymentRequestLifecycleEvent(SUPABASE_PAYMENT_REQUEST_OPENED_RPC);

  if (getQueryParameter("swish-return") === "1") {
    openSwishButton.hidden = true;
    if (swishLoading) {
      swishLoading.hidden = true;
    }
    swishStatus.textContent = "The receipt manager has been notified of your payment. Thank you for using Kvitt!";
    void reportPaymentRequestLifecycleEvent(SUPABASE_PAYMENT_REQUEST_CALLBACK_RPC);
  } else {
    void initializeCompactHistoryPaymentState();
  }

  openSwishButton.addEventListener("click", async () => {
    if (hasCompactHistoryPaymentLink() && !compactHistoryLinkReady) {
      return;
    }

    const swishLink = await buildSwishUrl();
    if (swishLink.error) {
      swishStatus.textContent = swishLink.error;
      return;
    }

    swishStatus.textContent = "Trying to open Swish...";

    const fallbackTimer = window.setTimeout(() => {
      if (document.visibilityState === "visible") {
        swishStatus.textContent =
          swishLink.mode === "paymentrequest"
            ? "If Swish did not open, make sure you are on a phone with Swish installed."
            : "If Swish opened, this direct-payment mode may not return here automatically.";
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
