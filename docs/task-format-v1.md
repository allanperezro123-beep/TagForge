# Tag Forge task format, version 1

A task tag is an NDEF message with two records, in this order:

| # | Record | Notes |
|---|--------|-------|
| 0 | MIME record, type `application/x-tf` (16 bytes), payload = the task bytes described below | Android dispatches on record 0, so this one must be first. |
| 1 | AAR (`android.com:pkg`) holding the app id (`com.tagforge.app`; `.debug` for debug builds) | Always last. A phone without Tag Forge offers to install it. |

Size on the tag: `19 + P` (task record, short form) `+ 34` (AAR) plus the NDEF TLV header (2 B) and terminator (1 B).
An NTAG213 holds `P <= 83` task bytes against the 136 B preset budget (84 against Android's 137 B `maxSize`, an exact fit).

The format is implemented by `com.tagforge.domain.task.TaskCodec` and pinned by `TaskCodecTest`.

## Encoding rules

* All integers are **big-endian**.
* `u8` = one byte, `u16` = two bytes.
* `str` = `u8 length (0..255)` followed by that many bytes of **UTF-8**.
* `url` = `u8 NFC Forum URI prefix code` + `str` of the rest (`0x04` = `https://`, `0x00` = no abbreviation; table in `UriPrefixes`).
* `flag` = one byte, `0` or `1`; any other value is malformed.

## Layout

```
header  : 1 byte   high nibble = format version (1)
                   bit 0      = HAS_NAME
                   bits 1-3   = reserved, MUST be 0
name    : u8 length (<= 32) + UTF-8           only when HAS_NAME
count   : u8 number of actions (0..16)
actions : count x ( u8 opcode + fixed-arity arguments )
```

There is **no per-action length byte**: every opcode has a fixed argument shape, which is what keeps an empty task at
2 bytes (`10 00`) and a single `SetRinger(vibrate)` at 4 bytes (`10 01 03 01`).

## Opcodes

| Opcode | Action | Arguments |
|--------|--------|-----------|
| `01` | OpenUrl | `url` |
| `02` | LaunchApp | `str` package name |
| `03` | SetRinger | `u8` 0 silent, 1 vibrate, 2 normal |
| `04` | SetVolume | `u8` stream (0 media, 1 ring, 2 alarm, 3 notification), `u8` percent 0..100 |
| `05` | SetBrightness | `u8` level 0..255 |
| `06` | Flashlight | `flag` on |
| `07` | Alarm | `u8` hour, `u8` minute, `str` label |
| `08` | Timer | `u16` seconds, `str` label |
| `09` | SmsCompose | `str` number, `str` body |
| `0A` | Dial | `str` number |
| `0B` | Speak | `str` text |
| `0C` | Notify | `str` title, `str` text |
| `0E` | HttpRequest | `u8` method (0 GET, 1 POST), `url`. **No headers, no body.** |
| `0F` | OpenSettings | `u8` screen (0 wifi, 1 bluetooth, 2 nfc, 3 location, 4 airplane, 5 mobile data, 6 hotspot, 7 internet panel, 8 volume, 9 dnd) |
| `10` | WifiJoin | `str` ssid, `str` key, `u8` security (0 open, 1 WPA2, 2 WPA3) |
| `11` | CalendarEvent | `str` title, `u16` duration in minutes |
| `12` | MediaKey | `u8` 0 play/pause, 1 next, 2 previous |
| `16` | WifiToggle | `flag` enabled |
| `17` | BluetoothToggle | `flag` enabled |

`0D` and `13..15` (the design's Vibrate, Wait, Dnd, ScreenTimeout) are **not** part of v1 and are rejected.

## Examples

| Task | Bytes |
|------|-------|
| empty | `10 00` |
| SetRinger(vibrate) | `10 01 03 01` |
| OpenUrl `https://example.com` | `10 01 01 04 0B 65 78 61 6D 70 6C 65 2E 63 6F 6D` (16 B) |
| named `Hi`, empty | `11 02 48 69 00` |
| Alarm 07:30 "A" | `10 01 07 07 1E 01 41` |
| Timer 300 s "t" | `10 01 08 01 2C 01 74` |
| Speak `ñ€` | `10 01 0B 05 C3 B1 E2 82 AC` |

## Decoding is strict and never executes anything

| Input | Result |
|-------|--------|
| empty / cut short | `Truncated` |
| version nibble other than 1 | `UnsupportedVersion(n)` |
| unknown opcode | `UnknownOpcode(op)` (an unknown argument shape cannot be skipped safely) |
| more than 16 actions, a string over 255 B, a name over 32 B, or more than 1024 bytes | `TooLarge` |
| reserved bits set, bad enum code, bad flag, invalid UTF-8, unknown URI prefix, trailing bytes | `Malformed` |

Decoding is **structural**. Whether a value is sensible (hour 99, an `intent:` URL, a phone number with letters) is
`ActionValidator`'s job; such actions are shown as blocked and never run. Nothing in a task runs without an explicit
confirmation (`ConfirmedTask`).

## Evolution

A new action means a new opcode (never reuse one). A change to an existing argument shape means a new version nibble;
older builds then answer `UnsupportedVersion` instead of misreading the bytes.
