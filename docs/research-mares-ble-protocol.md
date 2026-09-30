# Mares BLE Protocol Research

Research conducted 2026-09-30 for potential future feature: emulating a Mares dive
computer via BLE so the MySSI app can import full dive profiles directly.

## Status

The Mares Puck 4 BLE protocol is fully reverse-engineered by the open source community.
libdivecomputer v0.9.0 (June 2025) has working support.

## BLE GATT Details

| Type | UUID |
|---|---|
| Primary service | `544e326b-5b72-c6b0-1c46-41c1bc448118` |
| Write characteristic | `99a91ebd-b21f-1689-bb43-681f1f55e966` (WriteWithoutResponse) |
| Notify characteristic | `1d1aae28-d2a8-91a1-1242-9d2973fbe571` (Read/Notify) |
| CCCD descriptor | `00002902-0000-1000-8000-00805f9b34fb` (write `0x0100` to enable notify) |

Standard BLE services also advertised: Generic Access (`0x1800`), Device Information
(`0x180A`), Battery (`0x180F`).

## Device Advertisement

- Puck 4 advertises BLE name: `"Puck4"`
- No pairing required (bondless BLE connection)
- Short advertising window -- user must initiate BLE menu on device first

## Application Protocol (Icon HD over BLE)

The upper-layer protocol is the "Icon HD" serial protocol, framed for BLE.

### Framing

- `0xAA` = ACK/start byte
- `0xEA` = END byte
- Commands are 2 bytes: `[CMD] [CMD ^ 0xA5]`

### Key Commands

| Command | Bytes | Purpose |
|---|---|---|
| CMD_VERSION | `0xC2 0x67` | Handshake, returns model/firmware/serial |
| CMD_FLASHSIZE | `0xB3 0x16` | Query flash size |
| CMD_READ | `0xE7 0x42` | Download dive data |
| CMD_OBJ_INIT | `0xBF 0x7A` | Ring buffer object access init |
| CMD_OBJ_EVEN | `0xAC 0x09` | Ring buffer even object |
| CMD_OBJ_ODD | `0xFE 0x5B` | Ring buffer odd object |

### BLE Packet Modes

| Mode | Devices | Notes |
|---|---|---|
| FIXED | Smart, Quad, Puck Pro, Genius, Quad Air, Smart Air | 244-byte chunks, 20-byte ATT frames, wrapped by `dc_packet_open()` |
| VARIABLE | Puck 4, Puck Air 2, Sirius, Quad Ci, Quad 2, Sirius L, Puck Lite, Puck Pro EZ, Puck Pro Ultra | 244-byte variable-length framing, raw iostream |

The Puck 4 uses VARIABLE mode (Sirius variant). Defined by the `ISSIRIUS()` macro in
libdivecomputer's `mares_iconhd.c`.

### Handshake Sequence (observed from Mares Smart)

1. Write `0x0100` to CCCD of notify characteristic (enable notifications)
2. Write `0xC2 0x67` to write characteristic (version query)
3. Device responds via notify: `AA 00...` header + ASCII device name + firmware/serial

## Supported Mares BLE Models

| Model | Model ID | Transport |
|---|---|---|
| Smart / Smart Apnea | 0x10 | Serial + BLE |
| Puck Pro / Puck Pro + | 0x18 | Serial + BLE |
| Genius | 0x1C | Serial + BLE |
| Quad Air | 0x23 | Serial + BLE |
| Smart Air | 0x24 | Serial + BLE |
| Quad | 0x29 | Serial + BLE |
| Puck Air 2 | 0x2D | BLE only |
| Sirius | 0x2F | BLE only |
| Quad Ci | 0x31 | BLE only |
| Quad 2 | 0x32 | BLE only |
| Sirius L | 0x33 | BLE only |
| Puck 4 | 0x35 | BLE only |
| Puck Lite | 0x35 | BLE only |
| Puck Pro EZ | 0x35 | BLE only |
| Puck Pro Ultra | 0x35 | BLE only |

Puck 4, Puck Lite, Puck Pro EZ, and Puck Pro Ultra share model ID `0x35` and are
distinguished by BLE advertisement name.

## Potential Approach for DiveBridge

Emulate a Mares dive computer as a BLE peripheral:

1. Advertise as `"Puck4"` with service UUID `544e326b-...`
2. Accept the SSI app's connection
3. Respond to Icon HD protocol commands
4. Feed dive profile data converted from Garmin FIT file

This would give SSI full dive profiles (depth chart, gas, deco) instead of just the
summary data the QR code provides. Android supports BLE peripheral mode (API 21+).

### Open questions

- What exact sequence of commands does the SSI app send during import?
- What dive profile data format does it expect in the response?
- Can we sniff this using Android BLE HCI snoop logs with a real Puck 4?

## Sources

- libdivecomputer source (mares_iconhd.c, descriptor.c): https://github.com/libdivecomputer/libdivecomputer
- libdivecomputer NEWS/releases: https://github.com/libdivecomputer/libdivecomputer/blob/master/NEWS
- Subsurface BLE implementation: https://github.com/subsurface/subsurface/blob/master/core/qt-ble.cpp
- Subsurface BLE discovery: https://github.com/subsurface/subsurface/blob/master/core/btdiscovery.cpp
- Puck 4 support issue: https://github.com/subsurface/subsurface/issues/4358
- Puck 4 BLE name fix PR: https://github.com/subsurface/subsurface/pull/4981
- Puck 4 connection issue: https://github.com/subsurface/subsurface/issues/4626
- Mares Quad BLE discussion: https://groups.google.com/g/subsurface-divelog/c/YbuJnVytGUc
- Mares Smart discussion: https://subsurface.hohndel.narkive.com/xJEAMac1/mares-smart-dive-computer
- Mares Puck Pro EZ discussion: https://groups.google.com/g/subsurface-divelog/c/gvA3syVKc8k
- Mares Sirius discussion: https://groups.google.com/g/subsurface-divelog/c/MWRdzl6jqRk
- Diving Log BLE beta: https://www.divinglog.com/blog/?p=2531
- libdc-swift (iOS wrapper): https://github.com/deepsealabs/libdc-swift
- ISC DIVER Puck 4 support: https://iscdiver.com/app/dive-computers/mares-puck-4/
