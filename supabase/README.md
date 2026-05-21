# Supabase Backend Setup

This project now includes a first-pass Supabase schema for:

- user profiles
- archives
- receipts
- history entries
- receipt participants
- receipt items
- item-to-participant assignments
- payment requests for the website

## Project URL

The Supabase project URL you shared:

`https://yrwedmdtiaftyvkuxujw.supabase.co`

is the right kind of base URL for a Supabase project.

You still need a client-safe key for the website and Android app:

- preferred: a `sb_publishable_...` key
- legacy alternative: the `anon` key

Do not put a `sb_secret_...` or `service_role` key in the app or website.

## Apply the schema

1. Open your Supabase dashboard.
2. Go to `SQL Editor`.
3. Copy the contents of [supabase/schema.sql](C:/Users/Administrator/Documents/TestRepo/supabase/schema.sql) into the editor and run it.

## Auth and URL settings

Before wiring the app to sign in users, configure these in Supabase:

- `Authentication` -> `URL Configuration`
- Site URL:
  `https://edvinwendt.github.io/TestRepo/`
- Additional Redirect URLs:
  `https://edvinwendt.github.io/TestRepo/*`

If you later host the website somewhere else, add that URL too.

## Website config

The website now supports two payment-link styles:

- direct query params like `?Phone=...&Amount=...`
- Supabase-backed request tokens like `?request=<public_token>`

To enable Supabase-backed request tokens on the website, edit
[docs/supabase-config.js](C:/Users/Administrator/Documents/TestRepo/docs/supabase-config.js)
and add your publishable key.

## Android config

The Android app now reads these optional values from `local.properties`:

```properties
SUPABASE_URL=https://yrwedmdtiaftyvkuxujw.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxxxxxxxxxxxxxxxxxxx
```

If `SUPABASE_URL` is omitted, the build falls back to your current project URL.

## Suggested next implementation steps

1. Add Supabase Auth to the Android app.
2. Replace `SharedPreferences` history/archive storage with a repository that syncs with Supabase.
3. Generate `payment_requests` rows when sending Swish requests so the website can resolve `?request=` tokens.
4. Optionally add an Edge Function later for privileged workflows.
