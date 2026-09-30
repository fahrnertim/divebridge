# MySSI QR Code Format

Sources: reverse-engineered from real MySSI share codes and
[takken.io](https://github.com/webbertakken/takken.io).

## Format

Plain text payload, no signature, no encryption, no checksum.

Structure: `dive;noid;key:value;key:value;...`

- Starts with the literal tokens `dive;noid;` (bare keys, no colon/value)
- Followed by `key:value` pairs separated by `;`
- Empty values are allowed (e.g., `user_leader_id:`)
- Undefined/optional fields are omitted entirely
- Keys can repeat (e.g., `var_divetype_id` for multi-select)
- Integer fields: no decimal (e.g., `divetime:30`, `watertemp_c:16`)
- Decimal fields: dot separator, one decimal place (e.g., `depth_m:13.1`)
- `datetime` format is `yyyyMMddHHmm`, local time of the dive

## Example

```
dive;noid;dive_type:0;divetime:30;datetime:202608231353;depth_m:13.1;site:16887;var_watertype_id:4;var_divetype_id:24;user_master_id:3664600;user_firstname:Tim;user_lastname:Fahrner;user_leader_id:;watertemp_c:16;watertemp_max_c:22
```

## Fields

### Always present

| QR key | Source | Format | Notes |
|---|---|---|---|
| `dive` | literal | bare key | Marker |
| `noid` | literal | bare key | Marker |
| `dive_type` | FIT sport field | int | See `DiveType` enum |
| `divetime` | `dive_summary.bottom_time` | int (minutes) | Rounded. Use entries where `referenceMesg == session`. |
| `datetime` | `session.start_time` + local offset | `yyyyMMddHHmm` | FIT epoch is 1989-12-31 00:00:00 UTC. |
| `depth_m` | `dive_summary.max_depth` | 1 decimal | Meters. FIT stores it scaled (x1000). |
| `user_firstname` | Settings | string | Can be empty |
| `user_lastname` | Settings | string | Can be empty |
| `watertemp_c` | session min temperature | int | Celsius, rounded |
| `watertemp_max_c` | session max temperature | int | Celsius, rounded |

### Optional

| QR key | Source | Format | Notes |
|---|---|---|---|
| `site` | User input | int | SSI dive spot database ID |
| `var_weather_id` | User input | int | See `Weather` enum |
| `var_entry_id` | User input | int | See `EntryType` enum |
| `var_water_body_id` | User input | int | See `BodyOfWater` enum (incomplete) |
| `var_watertype_id` | User input | int | See `WaterType` enum |
| `var_current_id` | User input | int | See `Current` enum |
| `var_surface_id` | User input | int | See `Surface` enum |
| `var_divetype_id` | User input | int | See `DiveSubType` enum. Repeatable. |
| `user_master_id` | Settings | int | SSI user ID. Omit if empty. |
| `user_leader_id` | Unknown | string | Dive guide or instructor? |
| `airtemp_c` | User input | 1 decimal | Air temperature in Celsius |
| `vis_m` | User input | int | Visibility in meters |
| `deco` | Computed | int | `1` = yes. Omit entirely for no-deco dives (setting `0` still opens deco UI in SSI app). |

## Enum Values

### `dive_type` (DiveType)

| Value | Name |
|---|---|
| 0 | Scuba |
| 2 | Extended Range |
| 4 | Rebreather (SCR) |
| 6 | Freediving |
| 8 | Rebreather (CCR) |

### `var_watertype_id` (WaterType)

| Value | Name |
|---|---|
| 4 | Fresh |
| 5 | Salt |

### `var_divetype_id` (DiveSubType)

| Value | Name |
|---|---|
| 23 | Education |
| 24 | Fun Dive |
| 138 | Scientific |
| 139 | Work |

### `var_weather_id` (Weather)

| Value | Name |
|---|---|
| 1 | Cloudless |
| 2 | Cloudy |
| 3 | Rainy |
| 121 | Snow |

### `var_entry_id` (EntryType)

| Value | Name |
|---|---|
| 21 | Shore / Beach |
| 22 | Boat |
| 35 | Other |

### `var_water_body_id` (BodyOfWater) -- incomplete

| Value | Name |
|---|---|
| 13 | Ocean |
| 14 | River |
| 15 | Quarry |
| 16 | Lake |
| 17 | Indoor |
| 54 | Open Water |

### `var_current_id` (Current)

| Value | Name |
|---|---|
| 6 | No Current |
| 7 | Light |
| 8 | Strong |
| 9 | Ripping |

### `var_surface_id` (Surface)

| Value | Name |
|---|---|
| 10 | Calm |
| 11 | Moving |
| 12 | Stormy |

### `deco` (Decompression)

| Value | Name |
|---|---|
| 0 | No (but omit this field entirely -- see notes) |
| 1 | Yes |

## FIT Sport to DiveType Mapping

| FIT sport value | SSI dive_type |
|---|---|
| `diving` | 0 (Scuba) |
| `freediving` | 6 (Freediving) |
| `extended_range` | 2 (Extended Range) |
| `rebreather_scr` | 4 (Rebreather SCR) |
| `rebreather_ccr` | 8 (Rebreather CCR) |

## Open Questions

1. Does MySSI accept a self-generated QR for the user's own logbook?
2. Can MySSI scan a QR from a gallery image, or only via camera?
3. `BodyOfWater` enum is known to be incomplete.
4. Dive site: manual numeric ID for MVP, later possibly matched by GPS.