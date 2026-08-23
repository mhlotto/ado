# Ado app website

Static website for Ado. No JavaScript or build step is required.

## Files

- `index.html` - landing/marketing page
- `privacy.html` - app privacy policy
- `support.html` - basic support page
- `style.css` - shared styles
- `assets/ado-icon.png` - copied from the Android launcher icon
- `assets/ado-splash.png` - copied from the Android splash artwork

## Local preview

From this directory:

```bash
python3 -m http.server 8000
```

Then open `http://localhost:8000/`.

## Store URLs

Once deployed over HTTPS, use stable public URLs such as:

- Marketing / landing page: `https://YOUR-DOMAIN/`
- Privacy Policy URL: `https://YOUR-DOMAIN/privacy.html`
- Apple Support URL: `https://YOUR-DOMAIN/support.html`

Google Play requires the privacy policy to be public, active, non-geofenced, non-editable, and available as a normal webpage. The policy also needs a privacy contact or inquiry mechanism.

Apple requires a public Privacy Policy URL. For an iOS App Store listing, Apple also requires a Support URL that leads to actual contact information.

## Before public store submission

The current pages intentionally do **not** publish a contact address yet. Before submitting Ado to Google Play or the Apple App Store:

1. Add the developer/store-listing identity if needed so the policy matches the entity used in the store listing.
2. Add a real privacy/support contact method to `privacy.html` and `support.html`.
3. Ensure `support.html` contains actual contact information suitable for Apple's Support URL requirement.
4. Add a Privacy Policy link inside the Ado app itself. Google Play and Apple both require the privacy policy to be accessible from within the app.
5. Review the policy whenever app permissions, SDKs, networking, analytics, ads, accounts, sync, or other data practices change.
6. Complete Google Play's Data safety form consistently with the actual released build and this policy.
7. If an iOS version is created, complete App Store Connect's App Privacy disclosures consistently with the iOS build and third-party SDKs used there.

The policy currently reflects the Android source reviewed on 2026-08-23: local Room/DataStore storage, optional read-only calendar import, user-directed sharing/printing/file export, no Internet permission, and standard Android backup enabled.
