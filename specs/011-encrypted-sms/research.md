# Research: Encrypted SMS

## Transport profiles

| | Profile A: plain SMS + code sheet (v1, 003) | Profile B: encrypted binary SMS (this spec) |
|--|------------------------|-------------------------|
| Dumbphone needs | Nothing | A client app |
| Confidentiality | None | Content encrypted; metadata visible |
| Authentication | Code per command | Authenticated encryption + counter + client PIN; sheet as second factor for risk 5 |

## Choosing the dumbphone

| Platform | Verdict | Why |
|----------|---------|-----|
| Java ME (MIDP 2.0 / CLDC 1.1, WMA) | Preferred; test on real hardware | MIDlets can send and listen for port SMS; push registration for a closed app may be restricted for unsigned apps |
| KaiOS | Ruled out | SMS permission needs a certified app (2.5); 3.x/4.0 cannot sideload |
| Nokia S30+ and similar | Not viable | No third-party runtime |
| Locked-down Android phone | Fallback | Easiest to build; costs battery and temptation |

No reliable list of currently sold Java ME phones was found (reseller guides were wrong, e.g.
calling KaiOS phones `.jar` capable). Pick a candidate advertising MIDP 2.0/CLDC 1.1, WMA, 4G
VoLTE and a way to load apps, then run Spike 8 before buying.

## Decision: Port-addressed binary SMS, no Base64

Keeps ~115 bytes for the command per SMS. A data SMS to a port is invisible in the inbox, so the
agent also sends a plain "reply ready" notice.

## Sources

- [KaiOS App Permissions](https://developer.kaiostech.com/docs/getting-started/main-concepts/permissions/)
- [KaiOS developer FAQ](https://kaios.dev/faq/)
- [What's missing from KaiOS development](https://kaios.dev/2024/01/whats-missing-from-kaios-development/)
- [Java ME (Legacy Portable Computing Wiki)](https://lpcwiki.miraheze.org/wiki/Java_ME)
