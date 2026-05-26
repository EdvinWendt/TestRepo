(function () {
  const statusView = document.getElementById("delete-account-status");
  const formView = document.getElementById("delete-account-form");
  const emailInput = document.getElementById("delete-account-email");
  const nextButton = document.getElementById("delete-account-next");
  const confirmUrl = "https://edvinwendt.github.io/TestRepo/DeleteAccount/confirm/";

  function setStatus(message, tone) {
    statusView.textContent = message || "";
    statusView.dataset.tone = tone || "";
  }

  function setBusy(isBusy) {
    nextButton.disabled = isBusy;
    nextButton.textContent = isBusy ? "Sending..." : "Next";
  }

  function getSupabaseClient() {
    const config = window.KVITT_SUPABASE_CONFIG || {};
    const createClient = window.supabase?.createClient;

    if (!createClient || !config.url || !config.publishableKey) {
      return null;
    }

    return createClient(config.url, config.publishableKey);
  }

  function isValidEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  async function requestDeletionEmail(email) {
    const supabaseClient = getSupabaseClient();
    if (!supabaseClient) {
      throw new Error(
        "Delete account email is not configured yet. Add your Supabase publishable key in docs/supabase-config.js."
      );
    }

    const { error } = await supabaseClient.auth.signInWithOtp({
      email,
      options: {
        emailRedirectTo: confirmUrl,
        shouldCreateUser: false
      }
    });

    if (error) {
      throw error;
    }
  }

  formView?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = (emailInput?.value || "").trim();

    if (!isValidEmail(email)) {
      setStatus("Enter a valid email address.", "error");
      emailInput?.focus();
      return;
    }

    setBusy(true);
    setStatus("", "");

    try {
      await requestDeletionEmail(email);
      setStatus(
        "Check your email for the account deletion confirmation link.",
        "success"
      );
      formView.reset();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to send the confirmation email right now.";
      setStatus(message, "error");
    } finally {
      setBusy(false);
    }
  });
})();
