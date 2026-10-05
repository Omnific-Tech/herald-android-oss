# Android subscription bridge

The bridge is opt-in through `web_subscription_bridge` in `herald.json`.
The browser must verify `use_as_origin` for the app's configured HTTPS origin.
The PWA uses Herald Web to open the message port; device credentials stay native.

After each navigation the APK opens a new port and sends:

```json
{"type":"herald.ready","subscriptions":[{"channel":"alerts","topic":"updates.status"}],"saved":true,"notifications":"granted"}
```

`saved` distinguishes an explicit saved set from initial whole-channel defaults.
An explicit empty set means unsubscribe from everything. `notifications` is
`granted` or `denied`, reflecting Android notification settings.

```text
{"type":"herald.subscriptions.get","id":"r1"}
{"type":"herald.subscriptions.result","id":"r1","ok":true,"subscriptions":[...]}

{"type":"herald.subscriptions.set","id":"r2","subscriptions":[{"channel":"alerts","topic":"updates.status"}]}
{"type":"herald.subscriptions.result","id":"r2","ok":true}
```

Set replaces the complete list and succeeds after the broker accepts it. At most
100 unique entries may name configured channels; each topic is at most 200
characters. IDs must be 1–64 characters. A missing topic means an empty topic.
Sets are processed in order. Failures return `ok:false` and `error` equal to
`invalid_subscriptions`, `notifications_denied` or `herald_unavailable`.
Malformed messages, unknown types and invalid IDs are ignored.

## Digital Asset Links

Serve `/.well-known/assetlinks.json` on your own PWA origin:

```json
[
  {
    "relation": [
      "delegate_permission/common.handle_all_urls",
      "delegate_permission/common.use_as_origin"
    ],
    "target": {
      "namespace": "android_app",
      "package_name": "com.example.product",
      "sha256_cert_fingerprints": ["YOUR_SIGNING_CERTIFICATE_SHA256"]
    }
  }
]
```

Without URL handling verification the app opens a Custom Tab. Without origin
verification the subscription bridge remains disabled. Use your actual app ID
and certificate; never publish the signing private key.
