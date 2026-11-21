# 07 – Convoy: protokoll och hotmodell

Baslinje v0.1 · 2026-11-20 · Status: REVISE (kandidat). Gäller etapp 6, två deltagare. Grupper större än två är parkerade.

**Convoy är inte en nödtjänst och ersätter inte 112.** Nödkortet och nödsignalen gör bara att rätt uppgifter finns framme när man ringer.

## 1. Sammanjämkning av underlagen (K-12)

| Krav | Byggplanen | Startprompten §8 | Baslinjen |
|---|---|---|---|
| Transport | Relä i mitten | Självhostad HTTPS/WebSocket med skyddad kommunikation | WSS (TLS) mot egen server |
| Vad servern ser | Bara krypterad data | – | Bara krypterad nyttolast, plus metadata (se T-10) |
| Tidsgräns | Avsändaren slutar sända | Avsändaren slutar **och** servern kontrollerar tillstånd och utgångstid | Båda |
| Nyckel | Delad hemlig nyckel i QR-kod | Granskade standardbibliotek. Dokumentera begränsningar om E2E används. | Delad sessionsnyckel i QR, libsodium (03, 5) |

Lagren kompletterar varandra. Servern kan inte läsa positioner, och en stulen nyckel räcker inte utan en giltig åtkomsttoken.

## 2. Gränssnitt

```kotlin
interface ConvoyTransport {               // :convoy:api, vet inget om kartan eller Illogical Engine
    val state: StateFlow<TransportState>  // CONNECTED, DEGRADED, OFFLINE
    suspend fun open(session: ConvoySessionCredentials)
    suspend fun send(envelope: SealedEnvelope)          // redan krypterad
    val incoming: Flow<SealedEnvelope>
    suspend fun revokeSelf()
    suspend fun close()
}
```

Kryptering, samtycke och tidsgräns ligger i `:convoy:api`s logik ovanför transporten. Därför kan en framtida transport (till exempel LoRa eller Meshtastic, parkerat) ersätta `:convoy:wss` utan att kart- eller presentationslagret skrivs om. Konvojpositioner tillhör Hard Truth Core och når Illogical Engine bara som händelserna `ConvoyGap` och `ConvoyContactLost` (05).

## 3. Protokoll

### 3.1 Skapa och gå med

1. Deltagare A skapar en session på telefonen: `sessionId` (128 bitar slump), `sessionKey` (256 bitar slump), `expiresAt` (vald sluttid, högst 24 h*), `inviteSecret` (128 bitar).
2. A registrerar sessionen hos servern med `sessionId`, `expiresAt` och hash av `inviteSecret`. Servern får aldrig `sessionKey`.
3. A visar en QR-kod med `serverUrl`, `sessionId`, `sessionKey`, `inviteSecret` och `expiresAt`. Koden gäller i 10 minuter* och kan bara lösas in en gång.
4. B skannar koden, ser sluttiden och **godkänner själv** sin delning. B kan välja en tidigare sluttid än A men aldrig en senare.
5. B löser in `inviteSecret` hos servern och får en egen åtkomsttoken knuten till B:s enhetsnyckel. Servern markerar inbjudan som använd.
6. Båda telefonerna visar en kort verifieringskod som härleds ur `sessionKey` och båda deltagarnas id. Deltagarna jämför den muntligt. Det fångar en inbjudan som någon annan hann lösa in först (T-01).

### 3.2 Positionsmeddelande

```
POSITION {
  sessionId, senderId, seq,               // seq ökar strikt per avsändare
  sentAtUtc,                              // avsändarens klocka
  nonce,                                  // 192 bitar, slump
  ciphertext = AEAD_Encrypt(sessionKey, nonce,
                 plaintext = {lat, lon, accuracyM, fixTimeUtc, fixElapsedRtNs, speed?, heading?},
                 aad = sessionId || senderId || seq || sentAtUtc)
}
```

AEAD: **AES-256-GCM ur plattformens JCA** (ägarbeslut 2026-11-21, [10](10-beslutslogg.md), avsnitt 11). Noncen är då 96 bitar, slumpad per meddelande, inte 192 som i skissen ovan. Kandidaten XChaCha20-Poly1305 i libsodium är parkerad. Fortfarande NOT_VERIFIED i G7: krypteringsdelen ska granskas av någon som inte har byggt den. Ingen egen kryptografi.

### 3.3 Mottagarens kontroller

Ett meddelande kastas om något av följande gäller:

- AEAD-kontrollen fallerar.
- `seq` är lägre än eller lika med senast accepterade `seq` från samma avsändare (återspelning, T-02).
- `sentAtUtc` ligger efter sessionens `expiresAt`, eller efter avsändarens återkallelse.
- `fixTimeUtc` är äldre än 15 minuter* vid mottagandet. Det sparas som historik men visas aldrig som aktuellt läge.

### 3.4 Tidsgräns och återkallelse

- **Avsändaren:** telefonen kontrollerar `expiresAt` och återkallelse före varje sändning och schemalägger stopp vid sluttiden. Kraschar appen skickas ingenting, eftersom ingen sändning sker utan aktiv process.
- **Servern:** servern vägrar ta emot och vidarebefordra meddelanden efter `expiresAt` eller efter återkallad token. Servern sparar högst det senaste meddelandet per deltagare och raderar allt vid sluttiden.
- **Återkallelse:** en deltagare kan när som helst trycka "Sluta dela". Telefonen slutar sända direkt, skickar `REVOKE` och raderar `sessionKey` lokalt. Servern spärrar token.
- **Två deltagare:** när en part lämnar är sessionen slut. En ny session kräver ny nyckel.

### 3.5 Aktualitet

Ingen gammal position får presenteras som aktuell.

| Ålder (mottagarens tid minus `fixTimeUtc`, korrigerad för uppskattad klockskillnad) | Visning |
|---|---|
| ≤ 30 s* | Prick med ålder i sekunder |
| 30 s – 2 min* | Blekare prick, "för N s sedan" |
| 2 – 15 min* | Ihålig prick, "senast sedd HH:MM" |
| > 15 min* | Ingen prick. Bara "senast sedd HH:MM" i listan. |
| Klockskillnad okänd | Den högre av avsändar- och serverbaserad ålder visas |

Åldern visas alltid, även när den är kort (byggplanen: "ärlig ålder").

### 3.6 Nödsignal

`EMERGENCY` skickas som ett krypterat meddelande med högsta prioritet. Mottagaren går in i `EMERGENCY` i SafetyGate (06). Appen visar "Ring 112" och nödkortet. Utan mobilnät kommer signalen inte fram, och det står i gränssnittet.

## 4. Hotmodell

| Id | Hot | Exempel | Motåtgärd | Kvarstående risk |
|---|---|---|---|---|
| T-01 | Stulen inbjudan | Någon fotograferar QR-koden | Engångskod, 10 min* giltighet, verifieringskod som jämförs muntligt, A ser deltagarlistan och kan avsluta | Om angriparen hinner först **och** deltagarna hoppar över verifieringskoden, kan angriparen läsa positioner till sluttiden |
| T-02 | Återspelning | Ett gammalt meddelande skickas om | Strikt ökande `seq`, `sentAtUtc` i AAD, kontroll mot `expiresAt` | Ingen känd |
| T-03 | Nyckelkompromettering | `sessionKey` läcker från en telefon | Kort sessionstid, ny nyckel per session, token krävs också hos servern, nyckeln raderas vid sessionsslut | Positioner under sessionen kan läsas av den som har både nyckel och servertrafik |
| T-04 | Tidigare mottagna positioner | Den andra parten sparar eller skärmdumpar | Mottagarens app raderar konvojhistorik vid sessionsslut | **Kan inte förhindras tekniskt.** Det som har tagits emot kan inte dras tillbaka. Står i samtyckesdialogen. |
| T-05 | Komprometterad server | Någon tar över reläet | Servern har bara krypterad nyttolast | Metadata (T-10), och servern kan stoppa trafik |
| T-06 | Avlyssning i nätet | Wi-Fi på ett café | TLS plus AEAD | Ingen känd |
| T-07 | Gammal position visas som aktuell | Nätet försvinner | Åldersregler i 3.5, `ConvoyContactLost` till SafetyGate | Klockskillnad mellan telefoner, se 3.5 |
| T-08 | Förlorad eller stulen telefon | Telefonen glöms under en tur | Skärmlås krävs för att starta Convoy*, återkallelse från den andra telefonen avslutar sessionen | Under sessionen syns den andra partens position för den som har telefonen |
| T-09 | Glömd delning | Delning pågår efter turen | Obligatorisk sluttid, notis under hela delningen, stopp hos både avsändare och server | Ingen känd |
| T-10 | Metadata | Servern ser IP, tidpunkter och meddelandestorlek | Fast meddelandestorlek*, ingen loggning av IP utöver drift | Servern vet när och hur ofta två enheter kommunicerar |
| T-11 | Tramslagret påverkar delningen | En svans startar eller stoppar delning | `:engine` har inget beroende på `:convoy:*` (03) | Ingen känd, om arkitekturtesten finns |
| T-12 | Ingen täckning | Skoter i fjällen | Gränssnittet säger det ärligt. Egen karta, spår, hemväg och avtalad mötespunkt fungerar utan nät. | Ingen konvojinformation utan nät |
| T-13 | Tvingat samtycke | Någon pressas att dela | Varje deltagare godkänner på sin egen telefon och kan sluta när som helst utan att den andra får veta varför | Socialt, inte tekniskt |

## 5. Begränsningar med end-to-end-kryptering (startprompten §8)

- En delad nyckel kan inte dras in för en enskild deltagare. Vid fler än två deltagare kräver varje utträde en ny nyckel och en ny session. Det är en av anledningarna till att större grupper är parkerade.
- Återkallelse hindrar nya positioner men inte de som redan har tagits emot (T-04).
- Servern kan kontrollera åtkomst och tidsgräns men inte innehållet. Servern kan därför inte filtrera en felaktig position.
- Gruppmedlemskapet bestäms av vem som har nyckeln och en giltig token. Servern kan inte bevisa vem som sitter bakom en enhet.

## 6. Grind (etapp 6, G7)

Sändningen upphör vid sluttiden med appen i bakgrunden, och den andra telefonen visar då "senast sedd" med rätt ålder (byggplanen). Dessutom: SG-13 grönt, återspelningstest grönt, krypteringsdelen granskad av någon som inte byggt den. Status: NOT_VERIFIED.
