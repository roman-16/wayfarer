# Narration

Every book a player opens in Wayfarer is to be read aloud: the voice plays when the book opens - at a lectern, from the inventory or from the journal's Pages chapter - and stops when it closes, in the Voice/Speech sound category. The voice at the fire and Maren are read aloud as their lines appear in chat, and again when their entry opens in At the Fire. The audio comes from ElevenLabs, generated from the same text the books show. Building it is in `TODO.md`; this file holds the decisions.

## The cast

| Role | Who speaks | Reads | Voice | Effect |
|---|---|---|---|---|
| Stranger | The dying traveller at the Last Camp, the "last Wayfarer" - in truth Wend, a Gazebound captain | His journal at the Last Camp, *Orders for the Captains*, his two camp pages, *For Maren*, and the voice at the fire until the mask drops | Brian | none |
| Gaze | The dragon wearing the stranger's voice | The voice at the fire once the eleventh memory is told, and its lines in the End | Brian | undertone |
| Maren | The Gazebound evoker Wend raised | Her lines at the fire and the gate, *A Letter Never Sent* | not chosen | none |
| Shards | The Eye remembering: the first Wayfarers speaking as one | The twelve memories, the Twelve Peoples' shrine books, the ending line | Sarah | ethereal |
| Keepers | The Wayfarers who stayed to guard the empty gates | The three stronghold library books | Bill | none |
| Gazebound | The Illager cult that wants the dragon's sight back | *The Crown of the Grass King*, *The Halls of the Shadow Circle*, *The Captain's Ledger* | Harry | none |
| Archivist | A record-keeper of an Ancient City | The two Ancient City books | George | none |


## Why these choices

- **Effects only where the lore puts the voice somewhere other than a page.** Everyone except the shards wrote a book, and a dry recording says exactly that. The shards are the Eye remembering, so they get a chorus and a far echo.
- **The stranger reads the cult's orders.** *Orders for the Captains* is his; a player who knows his voice hears the twist before the Last Keeper names him. Any effect on either would hide that.
- **The gaze keeps his voice.** It learned Wend's voice by watching him, so it speaks as Brian; once the mask drops, a low undertone under the same voice tells the player what they already know.
- **Maren needs a voice of her own.** She is the only woman in the cast besides the shards' chorus; a built-in voice that suits her was not chosen yet.
- **The ending fades.** When the dragon dies, the chorus and echo fall away mid-sentence, and the last words are one plain voice: the ember going out.
- **Keep effects subtle.** The shard memories run about ten minutes in total; the effect must stay pleasant for two minutes at a time.

## What it costs

| Books | Characters |
|---|---|
| The stranger's journal | about 1,600 |
| Twelve memories | about 9,000 |
| Twelve shrine books | about 3,700 |
| Keepers, Gazebound, archivist, ending | about 3,200 |
| The voice at the fire | about 2,600 |
| Maren | about 800 |
| Pages found on the road | about 1,300 |
| The Twelve Peoples' places | about 2,100 |
| **Total** | **about 24,300** |

The account is on the free plan. It cannot use Voice Library voices or design voices through the API, so the cast uses ElevenLabs' built-in voices only. The free plan's monthly credit limit and its attribution and non-commercial terms were not verified; check them before generating. A paid plan would unlock library and designed voices.
