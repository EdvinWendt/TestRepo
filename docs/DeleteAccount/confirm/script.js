(function () {
  const loadingView = document.getElementById("delete-confirm-loading");
  const statusView = document.getElementById("delete-confirm-status");

  function setLoading(isLoading) {
    if (loadingView) {
      loadingView.hidden = !isLoading;
    }
  }

  function setStatus(message, tone) {
    statusView.textContent = message || "";
    statusView.dataset.tone = tone || "";
  }

  function getSupabaseClient() {
    const config = window.KVITT_SUPABASE_CONFIG || {};
    const createClient = window.supabase?.createClient;

    if (!createClient || !config.url || !config.publishableKey) {
      return null;
    }

    return createClient(config.url, config.publishableKey);
  }

  async function waitForSession(supabaseClient, timeoutMs) {
    const startedAt = Date.now();

    while (Date.now() - startedAt < timeoutMs) {
      const { data, error } = await supabaseClient.auth.getSession();
      if (error) {
        throw error;
      }

      if (data.session?.access_token) {
        return data.session;
      }

      await new Promise((resolve) => window.setTimeout(resolve, 150));
    }

    return null;
  }

  async function verifyLinkAndDeleteAccount() {
    const supabaseClient = getSupabaseClient();
    if (!supabaseClient) {
      throw new Error(
        "Delete account confirmation is not configured yet. Add your Supabase publishable key in docs/supabase-config.js."
      );
    }

    const url = new URL(window.location.href);
    const tokenHash = url.searchParams.get("token_hash");
    const verificationType = url.searchParams.get("type") || "email";

    if (tokenHash) {
      const { error: verifyError } = await supabaseClient.auth.verifyOtp({
        token_hash: tokenHash,
        type: verificationType
      });

      if (verifyError) {
        throw verifyError;
      }
    } else {
      const session = await waitForSession(supabaseClient, 4000);
      if (!session?.access_token) {
        throw new Error("This account deletion link is invalid, expired, or incomplete.");
      }
    }

    const { data, error: deleteError } = await supabaseClient.rpc("delete_current_user");
    if (deleteError) {
      throw deleteError;
    }

    if (!data?.deleted) {
      throw new Error("We could not confirm the account deletion request.");
    }

    try {
      await supabaseClient.auth.signOut();
    } catch (error) {
      // Ignore sign-out cleanup errors after the user has already been deleted.
    }

    window.history.replaceState({}, "", window.location.pathname);
  }

  async function run() {
    setLoading(true);
    setStatus("Verifying your confirmation link...", "");

    try {
      await verifyLinkAndDeleteAccount();
      setStatus(
        "Your Kvitt account and its synced backend data have been deleted.",
        "success"
      );
    } catch (error) {
      const message = error instanceof Error
        ? error.message
        : "Unable to complete the account deletion right now.";
      setStatus(message, "error");
    } finally {
      setLoading(false);
    }
  }

  run();
})();
