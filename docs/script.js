(function () {
  const totalInput = document.querySelector("#receipt-total");
  const participantInput = document.querySelector("#participant-count");
  const amountOutput = document.querySelector("#amount-per-person");
  const messageOutput = document.querySelector("#payment-message");
  const calculateButton = document.querySelector("#calculate-split");
  const copyButton = document.querySelector("#copy-message");
  const copyStatus = document.querySelector("#copy-status");
  const year = document.querySelector("#year");
  const scene = document.querySelector("[data-scene]");

  const formatter = new Intl.NumberFormat("sv-SE", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  function formatAmount(value) {
    return `${formatter.format(value)} kr`;
  }

  function calculateSplit() {
    const total = Math.max(0, Number(totalInput.value) || 0);
    const participants = Math.min(20, Math.max(1, Math.round(Number(participantInput.value) || 1)));
    participantInput.value = participants;

    const share = total / participants;
    const formattedShare = formatAmount(share);
    amountOutput.textContent = formattedShare;
    messageOutput.textContent = `Your share of the receipt is ${formattedShare}. Please send it via Swish.`;
    copyStatus.textContent = "";
  }

  function copyMessage() {
    const text = messageOutput.textContent.trim();

    function fallbackCopy() {
      const field = document.createElement("textarea");
      field.value = text;
      field.setAttribute("readonly", "");
      field.style.left = "-9999px";
      field.style.position = "fixed";
      document.body.appendChild(field);
      field.select();

      const copied = document.execCommand("copy");
      field.remove();
      return copied;
    }

    if (!navigator.clipboard) {
      copyStatus.textContent = fallbackCopy() ? "Message copied." : "Copy failed.";
      return;
    }

    navigator.clipboard
      .writeText(text)
      .then(() => {
        copyStatus.textContent = "Message copied.";
      })
      .catch(() => {
        copyStatus.textContent = fallbackCopy() ? "Message copied." : "Copy failed.";
      });
  }

  function moveScene(event) {
    if (!scene || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }

    const bounds = scene.getBoundingClientRect();
    const x = ((event.clientX - bounds.left) / bounds.width - 0.5) * 2;
    const y = ((event.clientY - bounds.top) / bounds.height - 0.5) * 2;

    scene.querySelectorAll("[data-depth]").forEach((element) => {
      const depth = Number(element.dataset.depth) || 0;
      element.style.setProperty("--scene-x", `${x * depth}px`);
      element.style.setProperty("--scene-y", `${y * depth}px`);
    });
  }

  if (year) {
    year.textContent = new Date().getFullYear();
  }

  if (calculateButton) {
    calculateButton.addEventListener("click", calculateSplit);
  }

  if (totalInput && participantInput) {
    totalInput.addEventListener("input", calculateSplit);
    participantInput.addEventListener("input", calculateSplit);
    calculateSplit();
  }

  if (copyButton) {
    copyButton.addEventListener("click", copyMessage);
  }

  if (scene) {
    scene.addEventListener("pointermove", moveScene);
  }
})();
