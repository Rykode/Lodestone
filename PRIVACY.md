# Privacy

Lodestone is built to keep what you save on your phone and nowhere else.

- **No account, no server, no analytics, no ads, no crash reporting.** Lodestone sends nothing to its author or to anyone who runs a Lodestone service, because there is no such service.
- **Your library** (items, pictures, files, tags, spaces) is stored in the app's private storage. Uninstalling the app deletes it. Use Settings → Back up & import to keep a copy.
- **Network use.** Lodestone connects to the internet only to:
  - fetch the page of a link you saved or imported (its title, text and preview picture). The request goes to that site, and to the site hosting its preview picture, directly from your phone;
  - ask a platform for the details of a post you saved, so the card can show its title and picture. For some platforms this is a small public request to that platform's own "embed" service (for example X, Bluesky and Vimeo). For Instagram and Threads, Lodestone opens the platform's embed page in a hidden browser view for a moment, which loads that page's own scripts and images. This happens when you save the link, and again at app start for links whose details are still missing;
  - load a post from a platform such as YouTube or Instagram when you tap play on it. That platform then sees the request as it would from any browser.
  - These requests carry no account, cookies or identifiers from you. They do reveal your IP address and that you saved that link, as any web request would.
- **Reading text inside pictures** runs on the phone with Tesseract. The pictures are not uploaded.
- **Permissions.** Internet (for the above) and notifications (progress of backups, imports and text reading; asked for only when needed and optional).
- **Backups and exports** are files you choose where to save. Lodestone does not upload them.
