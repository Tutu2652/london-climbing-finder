# Account sync (Sign in with Google) — one-time setup, free

This gives every visitor their own **private** notes & ratings that sync across
their devices after they sign in with Google. It uses Firebase Authentication +
Firestore, both free on the Spark plan. Without this, the site still works and
still has the simpler code-based sync; accounts just won't appear.

## 1. Create a Firebase project
1. Go to https://console.firebase.google.com → **Add project** (any name). You can skip Google Analytics.

## 2. Turn on Google sign-in
1. Left menu → **Build → Authentication → Get started**.
2. **Sign-in method** tab → **Google** → Enable → set a support email → Save.
3. Still in Authentication → **Settings → Authorized domains** → **Add domain** →
   add your site domain, e.g. `london-climbing-finder.pages.dev` (localhost is already allowed).

## 3. Create the database
1. Left menu → **Build → Firestore Database → Create database** → Production mode → pick a region → Enable.
2. Go to the **Rules** tab and paste exactly this, then **Publish**:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       // Private notes & ratings — each user only their own doc.
       match /users/{uid} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
       // Community reviews (public, opt-in). Anyone can read; each signed-in
       // user may create/update/delete only their own review. Field types and
       // sizes are validated so nobody can store oversized or junk documents.
       match /reviews/{gymId}/users/{uid} {
         allow read: if true;
         allow create, update: if request.auth != null && request.auth.uid == uid
           && request.resource.data.keys().hasOnly(['rating', 'note', 'name', 'updatedAt'])
           && request.resource.data.rating is int
           && request.resource.data.rating >= 1
           && request.resource.data.rating <= 5
           && request.resource.data.note is string
           && request.resource.data.note.size() <= 500
           && request.resource.data.name is string
           && request.resource.data.name.size() <= 80
           && request.resource.data.updatedAt is number;
         allow delete: if request.auth != null && request.auth.uid == uid;
       }
       // Per-gym rating totals (sum + count), kept in sync by the app via a
       // transaction. Public read; signed-in users may write, but only a
       // sanely-shaped document. Note: a hostile signed-in user could still
       // skew these totals — full integrity would need Cloud Functions. The
       // per-user reviews above remain trustworthy either way.
       match /gymStats/{gymId} {
         allow read: if true;
         allow write: if request.auth != null
           && request.resource.data.keys().hasOnly(['sum', 'count'])
           && request.resource.data.count is int
           && request.resource.data.count >= 0
           && request.resource.data.count <= 100000
           && request.resource.data.sum is number
           && request.resource.data.sum >= 0
           && request.resource.data.sum <= request.resource.data.count * 5;
       }
     }
   }
   ```
   (Private notes stay per-user; community reviews are public but tied to a
   signed-in account, and each person can write only their own review.)

   > Already set up? Re-paste the rules above and Publish again — earlier
   > versions of this file had rules that blocked review deletion (the app
   > deletes your review when you untick "Share publicly").

## 4. Get your web config
1. **Project settings** (gear icon, top-left) → scroll to **Your apps** →
   click the **web** icon `</>` to register a web app (any nickname; you do NOT need Hosting).
2. Copy the `firebaseConfig` values it shows you.

## 5. Paste it into the site
1. Open **`firebase-config.js`** in your repo, uncomment the lines and paste your values.
2. Commit. Cloudflare Pages redeploys, and a **"Sign in with Google"** link appears at the top of the site.

That's it. Users click sign in, and their notes/ratings save privately to their
account and sync to any device where they sign in. The values in
`firebase-config.js` are safe to be public — security is enforced by the rules above.
