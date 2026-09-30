# MySSI QR Code Format

Reverse-engineered from real MySSI share codes.

## Format

Plain text payload, no signature, no encryption, no checksum.

Structure: `dive;noid;key:value;key:value;...`

- Starts with the literal tokens `dive;noid;`
- Followed by `key:value` pairs separated by `;`
- Empty values are allowed (e.g., `user_leader_id:`)
- Keys can repeat (e.g., `var_divetype_id` appears twice for multi-select)
- Numbers use dot as decimal separator, one decimal place
- `datetime` format is `yyyyMMddHHmm`, apparently local time of the dive

## Example (dive #90, Attersee)

```
dive;noid;dive_type:0;divetime:30.0;datetime:202608231353;depth_m:13.1;site:16887;var_watertype_id:4;var_divetype_id:24;var_divetype_id:24;user_master_id:3664600;user_firstname:Tim;user_lastname:Fahrner;user_leader_id:;watertemp_c:16.0;watertemp_max_c:22.0
```

## Fields

| QR key | Source | Notes |
|---|---|---|
| `dive_type` | FIT sport/sub sport or dive mode | `0` = scuba (assumed). Other values unknown. |
| `divetime` | `dive_summary.bottom_time` or session elapsed time | Minutes, one decimal. |
| `datetime` | `session.start_time` + local offset | Local time. FIT epoch is 1989-12-31 00:00:00 UTC. |
| `depth_m` | `dive_summary.max_depth` | Meters, one decimal. FIT stores it scaled. |
| `site` | User input | SSI dive spot database ID. Not in FIT. |
| `var_watertype_id` | User input | Enum. Known values: `1` = salt, `4` = fresh (lake). |
| `var_divetype_id` | User input | Enum. Known values: `24` = recreational. Repeatable. |
| `user_master_id` | Settings | Static per user. |
| `user_firstname` | Settings | Static per user. |
| `user_lastname` | Settings | Static per user. |
| `user_leader_id` | Empty | Unknown purpose (dive guide or instructor?). |
| `watertemp_c` | min of `record.temperature` | Celsius, one decimal. |
| `watertemp_max_c` | max of `record.temperature` | Celsius, one decimal. |

## Open Questions

1. Does MySSI accept a self-generated QR for the user's own logbook?
2. Can MySSI scan a QR from a gallery image, or only via camera?
3. Complete list of enum values for `dive_type`, `var_watertype_id`, `var_divetype_id`.
4. Is `divetime` bottom time or total elapsed time?
5. Are there optional keys not seen yet (visibility, gas, tank, weights, notes)?
6. Dive site: manual numeric ID for MVP, later possibly matched by GPS.