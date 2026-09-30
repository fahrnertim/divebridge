# takken.io FIT-to-SSI Research

Research conducted 2026-09-30. Web-based tool that converts Garmin and Suunto FIT files
to MySSI QR codes.

## Repository

https://github.com/webbertakken/takken.io (main branch)

Docusaurus-based personal website with dive tools embedded as React components.

## Key File Paths

| Purpose | Path |
|---|---|
| Dive interface (vendor-neutral) | `src/domain/diving/Dive.ts` |
| SSI QR format and conversion | `src/domain/diving/ssi/SsiDive.ts` |
| SSI enum definitions | `src/domain/diving/ssi/SsiParameters.ts` |
| FIT file processing | `src/domain/diving/fit/FitFiles.ts` |
| Vendor detection | `src/domain/diving/fit/FitVendor.ts` |
| Garmin adapter | `src/domain/diving/garmin/GarminDive.ts` |
| Suunto adapter | `src/domain/diving/suunto/SuuntoDive.ts` |
| QR code renderer | `src/components/QrCode/QrCode.tsx` |
| Tests | `src/domain/diving/ssi/SsiDive.spec.ts` |

## What We Learned

### SSI QR Format

- All SSI enum values (water type, dive sub-type, weather, entry, body of water,
  current, surface, decompression)
- Salt water type is 5 (not 1 as we originally guessed)
- `divetime` and temperatures are integers, not one-decimal
- `deco` field: omit entirely for no-deco dives (setting 0 opens deco UI in SSI app)
- Null values are bare keys (no colon), undefined values are omitted entirely

### FIT Parsing Approach

- Uses `@garmin-fit/sdk` (official Garmin JS SDK) for parsing
- Garmin: `bottom_time` from `diveSummaryMesgs` filtered by `referenceMesg === 'session'`
- Garmin: temperatures from `sessionMesgs[0].minTemperature` / `maxTemperature`
- Suunto: `totalTimerTime` from session (no dive summary available)
- Suunto: temperatures from min of all `recordMesgs[].temperature`
- Vendor detection via `fileIdMesgs[0].manufacturer`
- Sport-to-DiveType mapping: diving->0, freediving->6, extended_range->2,
  rebreather_scr->4, rebreather_ccr->8

### Libraries Used

- `@garmin-fit/sdk` v21.115.0 for FIT parsing
- `fflate` for zip extraction
- `qrcode.react` for QR rendering

## Impact on DiveBridge

This research directly informed:
- Complete SSI enum values in `SsiEnums.kt`
- Correct field formatting in `SsiPayloadBuilder.kt` (integers vs decimals)
- `dive_summary` filtering by `reference_mesg` in `FitDecoder.kt`
- Temperature source (session fields, not record iteration)
- Updated `docs/qr-format.md` with full field documentation
