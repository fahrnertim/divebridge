# Handoff: DiveBridge (working title)

## Goal

A mobile app that receives dive logs exported as `.fit` files from the Garmin Dive app and converts them into a QR code that the MySSI app can scan to log the dive.

Flow:

1. User opens a dive in the Garmin Dive app and taps "Export" (FIT file).
2. User picks this app in the system share sheet.
3. App parses the FIT file, lets the user confirm or adjust a few fields (dive site, water type, dive type).
4. App renders a QR code in the MySSI format.
5. MySSI (on a second device, or via image import if supported) scans the QR code.

Android first. iOS must be possible later without a rewrite.

## Constraints

- Repository, code, comments, commits and documentation: **English only**.
- Do not use the trademarks "Garmin" or "SSI" in the app name or icon. Mentioning compatibility in the description is fine.
- No backend. Everything runs on device. No account, no network required.
- Avoid em dashes and en dashes in written docs and UI copy.

## Tech stack

- **Kotlin Multiplatform** with **Compose Multiplatform** for shared UI.
- Targets: `androidTarget` now, `iosArm64` / `iosSimulatorArm64` prepared in the Gradle setup but not required for the MVP.
- Business logic (FIT parsing, mapping, QR payload building) lives in `commonMain` and must have no platform dependencies.
- Gradle version catalog (`libs.versions.toml`).

### FIT parsing

The official Garmin FIT SDK has a Java version, but it cannot run on iOS. Options:

1. **Preferred:** write a minimal pure Kotlin FIT decoder in `commonMain` that only handles what we need (file header, definition and data messages, compressed timestamp headers, developer fields skipped). Only decode messages: `file_id`, `session`, `activity`, `dive_summary`, `dive_settings`, `record`. FIT is a simple binary format, so this is a few hundred lines and fully unit testable.
2. Fallback: `expect/actual` with the Java SDK on Android and the C/Swift SDK on iOS.

Go with option 1 unless it turns out to be unexpectedly complex. Verify field numbers, types and scales against the current FIT SDK profile (`Profile.xlsx`) rather than trusting this document.

### QR generation

Evaluate a Compose Multiplatform QR library (for example `qrose`). Fallback: `expect/actual` with ZXing on Android and CoreImage on iOS. Check that the library is maintained before adopting it.

### Receiving files

- Android: `ACTION_SEND` and `ACTION_VIEW` intent filters. Accept `application/octet-stream`, `application/vnd.ant.fit` and `*/*`, then validate by extension and the FIT header signature (`.FIT` at bytes 8..11). Apps are inconsistent with MIME types for FIT.
- Also offer a manual "Open file" button (Storage Access Framework).
- iOS later: Share Extension plus registered document type for `.fit`.

## MySSI QR format (reverse engineered)

Decoded from a real MySSI share code (dive #90, Attersee):

```
dive;noid;dive_type:0;divetime:30.0;datetime:202608231353;depth_m:13.1;site:16887;var_watertype_id:4;var_divetype_id:24;var_divetype_id:24;user_master_id:3664600;user_firstname:Tim;user_lastname:Fahrner;user_leader_id:;watertemp_c:16.0;watertemp_max_c:22.0
```

Observations:

- Plain text, no signature, no encryption, no checksum.
- Starts with the literal tokens `dive;noid;`, then `key:value` pairs separated by `;`.
- Empty values are allowed (`user_leader_id:`).
- Keys can repeat (`var_divetype_id` appears twice, likely multi select).
- Numbers use a dot as decimal separator and one decimal place.
- `datetime` format is `yyyyMMddHHmm`, apparently **local time** of the dive.
- The original QR uses dot style modules and an SSI logo in the center. We do not need to copy that styling; a standard QR code should be enough. Verify by scanning.

### Field mapping

| QR key | Source | Notes |
|---|---|---|
| `dive_type` | FIT sport/sub sport or dive mode | `0` = scuba (assumed). Other values unknown. |
| `divetime` | `dive_summary.bottom_time` or session elapsed time | Minutes, one decimal. Check which value MySSI expects. |
| `datetime` | `session.start_time` + local offset | Local offset from `activity.local_timestamp` minus `activity.timestamp`. FIT epoch is 1989-12-31 00:00:00 UTC. |
| `depth_m` | `dive_summary.max_depth` | Meters, one decimal. FIT stores it scaled. |
| `site` | User input | SSI dive spot database ID. Not in FIT. |
| `var_watertype_id` | User input | Enum, `4` seen for a lake. Needs mapping. |
| `var_divetype_id` | User input | Enum, `24` seen. Repeatable. Needs mapping. |
| `user_master_id` | Settings | Static per user. |
| `user_firstname`, `user_lastname` | Settings | Static per user. |
| `user_leader_id` | Empty | Unknown purpose (dive guide or instructor?). |
| `watertemp_c` | min of `record.temperature` | Celsius, one decimal. |
| `watertemp_max_c` | max of `record.temperature` | Celsius, one decimal. |

## Open questions (resolve early)

1. **Does MySSI accept a self generated code for the user's own logbook?** The share QR seems meant for buddies. Test: generate the exact payload above with a plain QR generator and scan it with MySSI on another account and on the same account. This is the go/no-go test and should happen before any app code.
2. Can MySSI scan a QR from a gallery image, or only via camera? Determines whether a second device is needed.
3. Enum values for `dive_type`, `var_watertype_id`, `var_divetype_id`: create test dives in MySSI with different settings and decode their QR codes. Document results in `docs/qr-format.md`.
4. `divetime`: bottom time or total elapsed time?
5. Are there optional keys we have not seen yet (visibility, gas, tank, weights, notes)? Check by filling in more fields in MySSI before sharing.
6. Dive site: manual numeric ID entry for the MVP. Later: a small local list of favorite sites, possibly matched by GPS start position from the FIT file.

## MVP scope

- Receive a FIT file via share intent or file picker.
- Parse and show: date/time, max depth, dive time, min/max water temperature.
- Settings screen: first name, last name, SSI user master ID.
- Per dive inputs: site ID, water type, dive type (with defaults remembered from the last dive).
- Render the QR code full screen with high brightness.
- Show the raw payload string (debug toggle) for troubleshooting.

Out of scope for the MVP: iOS build, dive site search, dive history, profile charts, batch conversion.

## Suggested project structure

```
/composeApp
  /src/commonMain/kotlin/.../
    fit/        # minimal FIT decoder
    dive/       # domain model (Dive), FIT -> Dive mapping
    ssi/        # Dive -> MySSI payload builder, enums
    ui/         # Compose screens
  /src/commonTest/kotlin/...
  /src/androidMain/kotlin/...   # intent handling, file access
  /src/iosMain/kotlin/...       # placeholder
/docs
  qr-format.md   # the format spec above, kept up to date
/testdata
  *.fit          # real exported dives (add a few)
```

## Testing

- Unit tests for the FIT decoder with real `.fit` files in `/testdata`.
- Golden test for the payload builder: given the values from dive #90, the builder must produce exactly the string shown above.
- Payload builder must be pure and deterministic (no current time, no locale dependent formatting; use explicit `.` decimal formatting).

## First steps for Claude Code

1. Scaffold the KMP project (Android + iOS targets, Compose Multiplatform).
2. Write `docs/qr-format.md` from this document.
3. Implement the payload builder with the golden test.
4. Implement the minimal FIT decoder with tests against real files.
5. Build the Android share intent flow and QR screen.
