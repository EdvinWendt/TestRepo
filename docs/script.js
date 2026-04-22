const openSwishButton = document.querySelector("#open-swish");
const swishStatus = document.querySelector("#swish-status");

if (openSwishButton && swishStatus) {
  openSwishButton.addEventListener("click", () => {
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

    window.location.href = "swish://";
  });
}
