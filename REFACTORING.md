# Refactoring Status (v0.1.0)

Completed refactorings prior to public release:

- [x] 1. Introduce a public `AdbIdentity` abstraction so consumers never import key classes from `impl`.
- [x] 2. Expose connection-service discovery through `api` and remove the legacy LiveData-based `AdbMdns` class.
- [x] 3. Mark all implementation classes except `AdbPairing` / `AdbPairingOptions` as `internal`.
- [x] 4. Redact logs containing pairing requests, codes, and QR passwords; route logging through pluggable `AdbLogger`.
- [x] 5. Preserve coroutine cancellation (re-throwing `CancellationException`) and make socket operations cancellation-aware with timeouts.
- [x] 6. Make persisted key storage alias-aware (`AdbKeyStore.load(alias)` / `save(alias, bytes)`) and remove hardcoded defaults.
- [x] 7. Replace naked port discovery with typed `discoverPairingEndpoints()` flow.
- [x] 8. Rename packages to project-neutral identity `io.github.khoanguyenbk18.adbpairing`.
- [x] 9. Raise `minSdk` to 30 and ensure release lint passes with 0 errors.
- [x] 10. Configure precise consumer ProGuard rules for JNI preservation of `PairingContext`.
