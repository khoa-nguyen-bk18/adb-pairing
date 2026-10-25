# Refactoring backlog

The first private release intentionally preserves the implementation extracted from Zola. Before declaring a stable API:

1. Introduce a public `AdbIdentity` abstraction so consumers never import key classes from `impl`.
2. Expose connection-service discovery through `api` and remove the legacy LiveData-based `AdbMdns` class.
3. Mark all implementation classes except the composition entry point as `internal`.
4. Remove or redact logs containing pairing requests, codes, and QR passwords.
5. Preserve coroutine cancellation and make blocking socket operations cancellation-aware.
6. Make persisted key storage genuinely alias-aware and remove all `zola` defaults.
7. Replace naked port discovery with a typed endpoint/result API that honors its options.
8. Rename packages and namespace to a project-neutral identity.
9. Add device tests for keystore persistence, JNI loading, pairing context lifecycle, and minified consumers.
10. Audit and document the provenance and licensing of `PairingContext` and the bundled native binaries.
