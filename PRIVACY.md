# ForgeQuest privacy policy

_Last updated: November 17, 2026_

ForgeQuest is a personal fitness app for Android, with a web app for iPhone at https://caelumop.github.io/ForgeQuest. This page explains what both do with your data. The web app has its own section below.

## What stays on your phone

Your workouts, meals, body weight, goals and coach chat are stored on your phone. They are not sent to any ForgeQuest server, and its developer cannot see them. The only server is the optional Friends feature below, which receives a small weekly summary and nothing else.

## Friends (optional)

If you sign in to Friends with Google, ForgeQuest's Friends server (Cloudflare) stores: your Google account ID, the username you pick, your Google profile photo (only while "Share photo" is on), your friend list, friend requests, blocks and "Go train" pings, a notification token so pings can reach your phone, and a weekly summary: workout days done and planned, protein-goal days, number of personal records, your Forge Score and streak, and when you reached that score. PR details (exercise and weight) are shared only while "Share PRs" is on. Your sets, meals, body weight and notes are never sent.

You can turn off photo and PR sharing, alerts and pings in Settings, and "Delete Friends account" in Settings removes your profile, friend list and weekly summaries from the server.

## Google sign-in and Google Drive

If you choose to connect Google Drive, ForgeQuest asks for two permissions:

- **App data folder** (`drive.appdata`): to save and restore a daily backup in a hidden folder that only ForgeQuest can see.
- **Files ForgeQuest creates** (`drive.file`): to create and update the Google Docs it makes itself ("ForgeQuest data", "ForgeQuest from Gemini" and "ForgeQuest plan"). ForgeQuest cannot see your other files.

When you sign in to Google Drive, a daily automatic backup is turned on (you can switch it off in Settings). Backups go only to your own Drive, never to a ForgeQuest server.

You can disconnect at any time in the app (Profile › Google Drive › Disconnect) or at https://myaccount.google.com/permissions.

## AI features

The coach and meal scanning send your messages, meal photos and a summary of your training to Google Gemini, using the API key you provide. Google handles the data under its own privacy policy; a free key may let Google use it to improve its models. The web app can also send chat messages to Groq, only if you add a Groq key there. Without any key, nothing is sent by the app itself: the "Ask Gemini" and "Copy skill" buttons only copy text, which you paste into the Gemini app yourself. In the Android app, your keys are stored encrypted on your phone.

## Web app (iPhone)

The web app works like the Android app, with these differences:

- **Where your data is kept**: in your browser's storage for the ForgeQuest site (IndexedDB), on your phone only. There is no server.
- **AI keys**: stored in the browser's storage **unencrypted**. They are never put in backups and never sent anywhere except to Gemini or Groq for your requests. Clearing the site's data (or deleting the Home Screen app) removes them.
- **Google Drive backup is optional**: it asks only for the app data folder permission (`drive.appdata`) and uses the same hidden backup file as the Android app, so a backup moves between the two. The sign-in token is kept in the browser for about an hour. The web app makes no Google Docs.
- **No Health Connect**: the web app does not read health data.
- **Crash reports**: if the app hits an error, the error message, app build, time and browser name are kept in the browser. The next time you open the app it offers to share that report; nothing is sent unless you choose to share it.
- **No tracking or analytics.**

## Health Connect

If you turn on Health Connect in Settings, ForgeQuest reads your daily steps, calories burned and body weight from Health Connect on your phone. It only reads this data to calculate your calorie target and log body weight in the app, and does not send it anywhere.

ForgeQuest's use of information received from Google APIs adheres to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements.

## Update check (Android)

When you tap "Check for updates" in Settings, the app downloads a small version file from this project's GitHub repository, and, if you choose to install, the new APK from its GitHub Releases. GitHub sees your IP address, as with any web request. Nothing about you or your data is sent.

## Sharing and selling

ForgeQuest does not sell or share your data, show ads, or use analytics or tracking.

## Deleting your data

Uninstalling the Android app deletes everything on your phone. In the web app, deleting the Home Screen app or clearing the site's data in your browser settings does the same. Backups and Docs in your Google Drive stay until you delete them there.

## Contact

Questions: darkboyhalo@gmail.com
