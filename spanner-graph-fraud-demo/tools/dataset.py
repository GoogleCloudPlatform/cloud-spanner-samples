# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Cymbal Bank P2P fraud demo dataset: the single source of truth.

Story: a fake "investment pool" scam. Wealthy Premium customers (victims) send
money to recently created mule accounts. The mules forward 92% of it to the
ringleader within minutes. The ring's profiles share a templated get-rich bio
and the same AI-generated headshot style. Two "sleeper" mules have not moved
any money yet, so only their profile similarity gives them away.

Everything is deterministic: the demo queries always return the same numbers.
"""

# setup.sh uploads profile_pics/ to a Cloud Storage bucket in your project and
# replaces this placeholder with the bucket's base URL while loading the data.
PHOTO_URL = "@PHOTO_BASE_URL@/{}.jpg"

# (PersonId, Name, Segment, SignupTime, ProfileBio)
# The photo file stem is the PersonId with "." replaced by "_".
RING = [
    ("robin.locksley", "Robin Locksley", "Standard", "2026-08-29T21:14:03Z",
     "Founder of an exclusive investment club. Helping everyday people build "
     "passive income and financial freedom. DM me to join the pool."),
    # Active mules: signup burst on 2026-09-14 between 02:11 and 02:24 UTC.
    ("will.scarlett", "Will Scarlett", "Standard", "2026-09-14T02:11:07Z",
     "Earning passive income from home. Financial freedom is a DM away."),
    ("ana.little", "Ana Little", "Standard", "2026-09-14T02:12:13Z",
     "My money works while I sleep. Ask me how to join the pool."),
    ("alan.dale", "Alan Dale", "Standard", "2026-09-14T02:13:02Z",
     "Weekly returns, zero effort. Message me to get started today."),
    ("mei.miller", "Mei Miller", "Standard", "2026-09-14T02:14:21Z",
     "Quit the 9 to 5 thanks to one simple investment club. DM me."),
    ("arthur.bland", "Arthur Bland", "Standard", "2026-09-14T02:15:09Z",
     "Crypto pool member. Guaranteed returns every Friday. Inbox me."),
    ("dev.doncaster", "Dev Doncaster", "Standard", "2026-09-14T02:16:44Z",
     "Side hustle turned full-time income. Spots in the pool are limited!"),
    ("grace.whitehand", "Grace Whitehand", "Standard", "2026-09-14T02:17:30Z",
     "Helping friends grow their savings fast. DM for an invite."),
    ("reynold.greenleaf", "Reynold Greenleaf", "Standard",
     "2026-09-14T02:18:52Z",
     "Financial freedom coach. Passive income for everyone who joins."),
    ("sam.stutely", "Sam Stutely", "Standard", "2026-09-14T02:19:38Z",
     "Turn $500 into a weekly paycheck. Ask me how."),
    ("tess.tuck", "Tess Tuck", "Standard", "2026-09-14T02:20:15Z",
     "Living proof that passive income works. Join us, DM me."),
    # Sleepers: same signup burst, but no transfers and no risk signals yet.
    ("gwen.gamwell", "Gwen Gamwell", "Standard", "2026-09-14T02:22:41Z",
     "Just joined the best investment pool. Passive income incoming. DM me."),
    ("kai.sherwood", "Kai Sherwood", "Standard", "2026-09-14T02:24:03Z",
     "Want guaranteed weekly returns? I can get you into the pool. DM."),
]

# Long-time Premium customers who fell for the "investment pool" pitch.
VICTIMS = [
    ("eleanor.whitfield", "Eleanor Whitfield", "Premium", "2016-03-14T15:22:10Z",
     "Retired cardiologist. Garden roses, Sunday concerts, and spoiling my "
     "grandchildren."),
    ("harold.chen", "Harold Chen", "Premium", "2017-06-02T18:05:44Z",
     "Sailing the coast whenever I can. Semi-retired engineer and amateur "
     "astronomer."),
    ("margaret.oconnell", "Margaret O'Connell", "Premium",
     "2016-11-21T10:41:37Z",
     "Rare book collector and trustee of the city library foundation."),
    ("rajesh.kapoor", "Rajesh Kapoor", "Premium", "2018-02-08T09:13:52Z",
     "Managing partner at a logistics firm. Cricket on weekends, chai always."),
    ("sofia.marchetti", "Sofia Marchetti", "Premium", "2019-05-17T12:30:05Z",
     "Owner of a family vineyard. Wine, olive oil, and long dinners with "
     "friends."),
    ("william.ashford", "William Ashford", "Premium", "2016-08-30T08:02:19Z",
     "Retired barrister. Golf every morning, crossword every afternoon."),
    ("aiko.tanaka", "Aiko Tanaka", "Premium", "2020-09-12T14:48:33Z",
     "Gallery owner and patron of emerging artists. Tea ceremony enthusiast."),
    ("frederick.osei", "Frederick Osei", "Premium", "2021-01-25T16:27:11Z",
     "Founder of a construction company. Proud father of three. Highlife "
     "music fan."),
]

# Everyday customers. Marian (partner) and Owen (roommate) know Robin, but
# their payments with him are small, older, and unrelated to the scam.
REGULARS = [
    ("marian.fitzwalter", "Marian Fitzwalter", "Standard",
     "2019-04-03T19:10:22Z",
     "Elementary school teacher. Farmers market regular and terrible "
     "gardener."),
    ("owen.hughes", "Owen Hughes", "Standard", "2020-07-15T20:44:09Z",
     "Bouldering most evenings. Physical therapist by day."),
    ("priya.raman", "Priya Raman", "Standard", "2019-02-11T13:05:48Z",
     "UX designer. Splitting brunch bills since 2019."),
    ("marcus.johnson", "Marcus Johnson", "Standard", "2021-03-29T06:58:31Z",
     "Training for my third marathon. Personal trainer and coffee addict."),
    ("elena.petrova", "Elena Petrova", "Standard", "2018-10-07T05:41:16Z",
     "Pastry chef. Morning person. Always testing a new recipe."),
    ("kwame.mensah", "Kwame Mensah", "Standard", "2024-09-02T17:22:40Z",
     "Computer science student. Building apps and playing pickup soccer."),
    ("lucia.fernandez", "Lucía Fernández", "Standard", "2022-06-18T11:36:27Z",
     "Marine biologist. Happiest near the ocean."),
    ("tom.becker", "Tom Becker", "Standard", "2018-05-23T07:15:03Z",
     "Management consultant. Living out of a carry-on, 4 cities a week."),
    ("hannah.kim", "Hannah Kim", "Standard", "2023-01-14T23:48:55Z",
     "Night owl, karaoke queen, and junior product manager."),
    ("diego.morales", "Diego Morales", "Standard", "2020-11-09T04:12:38Z",
     "Baker at my family's panadería. Up at 4am so you can have fresh bread."),
    ("fatima.alsayed", "Fatima Al-Sayed", "Standard", "2021-08-26T15:57:14Z",
     "Librarian and book club organizer. Ask me for recommendations."),
    ("noah.williams", "Noah Williams", "Standard", "2022-12-03T22:31:49Z",
     "Music producer and part-time sound engineer."),
    ("chloe.dubois", "Chloé Dubois", "Standard", "2023-07-21T09:24:06Z",
     "Translator and café hopper. Paris to everywhere."),
    ("yusuf.demir", "Yusuf Demir", "Standard", "2019-09-30T16:03:57Z",
     "Carpenter. Custom furniture, built to last."),
    ("isabella.rossi", "Isabella Rossi", "Standard", "2022-04-10T07:49:20Z",
     "Hiking every weekend. Nurse on weekdays."),
    ("ethan.park", "Ethan Park", "Standard", "2021-11-17T18:38:42Z",
     "Software engineer working remotely. Board games and ramen."),
    ("amara.okafor", "Amara Okafor", "Standard", "2025-06-02T12:14:35Z",
     "Fresh graduate in public health. First job, first apartment!"),
    # Brand-new accounts with no activity yet.
    ("lena.hoffmann", "Lena Hoffmann", "Standard", "2026-10-05T10:02:48Z",
     "Just moved to the city for a design internship."),
    ("arjun.mehta", "Arjun Mehta", "Standard", "2026-10-06T08:31:15Z",
     "Bike commuter and data analyst. New to Cymbal Bank."),
]

PEOPLE = RING + VICTIMS + REGULARS

RINGLEADER = "robin.locksley"
MULE_CUT = 0.08  # Each mule keeps 8% and forwards 92% to the ringleader.

# The scam, on 2026-10-06 (UTC). Two victims were hit twice.
# (victim, mule, amount, deposit time, memo, minutes until the mule forwards)
SCAM = [
    ("eleanor.whitfield", "will.scarlett", 12500, "2026-10-06T01:02:14Z",
     "Investment pool deposit", 6),
    ("eleanor.whitfield", "ana.little", 9800, "2026-10-06T01:09:51Z",
     "Investment pool top-up", 9),
    ("harold.chen", "alan.dale", 14200, "2026-10-06T01:17:33Z",
     "Pool contribution - Q4", 5),
    ("margaret.oconnell", "mei.miller", 11000, "2026-10-06T01:26:05Z",
     "Onboarding deposit", 11),
    ("margaret.oconnell", "arthur.bland", 8600, "2026-10-06T01:31:48Z",
     "Onboarding deposit - part 2", 7),
    ("rajesh.kapoor", "dev.doncaster", 13400, "2026-10-06T01:44:20Z",
     "Weekly returns pool", 8),
    ("sofia.marchetti", "grace.whitehand", 10900, "2026-10-06T01:58:37Z",
     "Investment club invite", 12),
    ("william.ashford", "reynold.greenleaf", 12000, "2026-10-06T02:09:12Z",
     "Pool contribution", 4),
    ("aiko.tanaka", "sam.stutely", 9500, "2026-10-06T02:21:46Z",
     "Investment pool deposit", 10),
    ("frederick.osei", "tess.tuck", 13200, "2026-10-06T02:36:09Z",
     "Guaranteed returns pool", 6),
]
PAYOUT_MEMOS = ["Pool transfer", "Club allocation", "Payout batch 1006"]

# Everyday payments. All are below 5,000, and none are forwarded within an
# hour, so they never match the "follow the money" pattern.
# (sender, receiver, amount, time, memo)
EVERYDAY = [
    # Robin's genuine contacts: small and older than the scam.
    ("marian.fitzwalter", "robin.locksley", 45.00, "2026-09-20T19:42:11Z",
     "Dinner"),
    ("robin.locksley", "marian.fitzwalter", 120.00, "2026-09-27T16:05:33Z",
     "Groceries"),
    ("owen.hughes", "robin.locksley", 950.00, "2026-10-01T08:15:27Z",
     "October rent - my half"),
    ("robin.locksley", "owen.hughes", 62.40, "2026-10-03T21:11:04Z",
     "Electric bill"),
    # Victims' normal lives.
    ("eleanor.whitfield", "elena.petrova", 85.00, "2026-09-17T14:20:00Z",
     "Birthday cake for my granddaughter"),
    ("harold.chen", "yusuf.demir", 1850.00, "2026-09-05T15:44:28Z",
     "Custom chart table"),
    ("frederick.osei", "yusuf.demir", 2400.00, "2026-09-22T11:09:51Z",
     "Custom bookshelves"),
    ("margaret.oconnell", "fatima.alsayed", 120.00, "2026-09-15T16:31:07Z",
     "Library fundraiser donation"),
    ("chloe.dubois", "sofia.marchetti", 64.00, "2026-09-13T19:55:42Z",
     "Wine tasting"),
    ("william.ashford", "marcus.johnson", 300.00, "2026-09-09T07:30:00Z",
     "Personal training - September"),
    ("aiko.tanaka", "noah.williams", 450.00, "2026-09-25T20:14:36Z",
     "Sound setup for the gallery opening"),
    ("rajesh.kapoor", "tom.becker", 2750.00, "2026-09-18T13:47:02Z",
     "Consulting workshop fee"),
    # Friends splitting bills.
    ("priya.raman", "hannah.kim", 23.50, "2026-09-06T12:41:00Z", "Brunch"),
    ("hannah.kim", "priya.raman", 18.00, "2026-09-12T09:15:22Z", "Coffee"),
    ("ethan.park", "priya.raman", 31.75, "2026-09-19T20:33:10Z",
     "Ramen night"),
    ("kwame.mensah", "ethan.park", 15.00, "2026-09-21T17:05:44Z",
     "Soccer pitch fee"),
    ("marcus.johnson", "isabella.rossi", 40.00, "2026-09-08T06:30:12Z",
     "Race entry split"),
    ("isabella.rossi", "marcus.johnson", 12.00, "2026-09-14T10:02:55Z",
     "Smoothies"),
    ("elena.petrova", "diego.morales", 65.00, "2026-09-10T05:12:33Z",
     "Flour and butter order"),
    ("diego.morales", "elena.petrova", 48.00, "2026-09-24T04:48:19Z",
     "Croissant tray"),
    ("fatima.alsayed", "chloe.dubois", 80.00, "2026-09-16T11:26:48Z",
     "Translation proofreading"),
    ("lucia.fernandez", "isabella.rossi", 27.00, "2026-09-27T09:41:05Z",
     "Trail lunch"),
    ("noah.williams", "hannah.kim", 36.00, "2026-10-02T23:58:31Z",
     "Karaoke room"),
    ("hannah.kim", "noah.williams", 22.00, "2026-10-04T00:42:17Z",
     "Late night tacos"),
    ("tom.becker", "lucia.fernandez", 54.00, "2026-09-26T18:37:22Z",
     "Boat tour tickets"),
    ("yusuf.demir", "diego.morales", 19.50, "2026-09-30T06:05:58Z",
     "Bread for the workshop"),
    ("amara.okafor", "kwame.mensah", 25.00, "2026-09-28T14:22:09Z",
     "Moving help pizza"),
    ("owen.hughes", "marcus.johnson", 35.00, "2026-09-23T19:16:44Z",
     "Climbing gym guest pass"),
    ("marian.fitzwalter", "elena.petrova", 28.00, "2026-10-04T08:50:12Z",
     "Farmers market pastries"),
    ("ethan.park", "kwame.mensah", 120.00, "2026-10-05T16:03:27Z",
     "App design tutoring"),
    ("priya.raman", "chloe.dubois", 42.00, "2026-10-05T11:18:56Z",
     "Museum tickets"),
]

# Risk signals. In production a BigQuery continuous query derives these from
# login and device telemetry and streams them into Spanner (reverse ETL).
# Signals alone are noisy: 5 of the 17 are false positives on innocent people.
# (PersonId, SignalType, Details, EventTime)
_DEVICE = "Device fingerprint dfp-7f3a9c shared by 6 accounts"
SIGNALS = [
    ("will.scarlett", "IMPOSSIBLE_TRAVEL",
     "Logins from Lisbon and Manila 9 minutes apart", "2026-10-05T23:41:12Z"),
    ("ana.little", "SHARED_DEVICE", _DEVICE, "2026-10-05T23:52:40Z"),
    ("alan.dale", "IMPOSSIBLE_TRAVEL",
     "Logins from Toronto and Jakarta 14 minutes apart", "2026-10-06T00:03:27Z"),
    ("alan.dale", "SHARED_DEVICE", _DEVICE, "2026-10-06T00:05:09Z"),
    ("mei.miller", "IMPOSSIBLE_TRAVEL",
     "Logins from Madrid and Seoul 7 minutes apart", "2026-10-06T00:18:55Z"),
    ("arthur.bland", "SHARED_DEVICE", _DEVICE, "2026-10-06T00:27:31Z"),
    ("dev.doncaster", "IMPOSSIBLE_TRAVEL",
     "Logins from Dublin and Sao Paulo 12 minutes apart",
     "2026-10-06T00:39:02Z"),
    ("grace.whitehand", "IMPOSSIBLE_TRAVEL",
     "Logins from Chicago and Nairobi 10 minutes apart", "2026-10-06T00:51:44Z"),
    ("grace.whitehand", "SHARED_DEVICE", _DEVICE, "2026-10-06T00:53:18Z"),
    ("reynold.greenleaf", "SHARED_DEVICE", _DEVICE, "2026-10-06T01:06:37Z"),
    ("sam.stutely", "IMPOSSIBLE_TRAVEL",
     "Logins from Denver and Bangkok 8 minutes apart", "2026-10-06T01:19:26Z"),
    ("tess.tuck", "SHARED_DEVICE", _DEVICE, "2026-10-06T01:33:50Z"),
    # False positives: none of these people are connected to the ring.
    ("tom.becker", "IMPOSSIBLE_TRAVEL",
     "Logins from Boston and London 6 hours apart (overnight flight)",
     "2026-10-02T06:12:45Z"),
    ("chloe.dubois", "IMPOSSIBLE_TRAVEL",
     "Logins from Paris and Montreal 3 minutes apart (VPN)",
     "2026-10-04T15:27:08Z"),
    ("hannah.kim", "NEW_DEVICE", "First login from a new phone",
     "2026-10-03T01:14:22Z"),
    ("eleanor.whitfield", "NEW_DEVICE", "First login from a new tablet",
     "2026-10-01T10:48:37Z"),
    ("arjun.mehta", "NEW_DEVICE", "First login from a new laptop",
     "2026-10-06T08:33:02Z"),
]


def _uuid(*parts):
    """Deterministic UUID so that reruns produce identical primary keys."""
    import uuid
    ns = uuid.UUID("6f1c2a52-9a0e-4d6b-8a51-2b8f4f6c9e10")
    return str(uuid.uuid5(ns, "|".join(str(p) for p in parts)))


def _plus_minutes(ts, minutes):
    from datetime import datetime, timedelta, timezone
    t = datetime.strptime(ts, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    return (t + timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")


def photo_stem(person_id):
    return person_id.replace(".", "_")


def transfer_rows():
    """Returns (TransferId, SenderId, ReceiverId, Amount, Memo, CreatedAt)."""
    rows = []
    for i, (victim, mule, amount, ts, memo, delay) in enumerate(SCAM):
        rows.append((_uuid("t", victim, mule, ts), victim, mule,
                     "%.2f" % amount, memo, ts))
        payout = round(amount * (1 - MULE_CUT))
        out_ts = _plus_minutes(ts, delay)
        rows.append((_uuid("t", mule, RINGLEADER, out_ts), mule, RINGLEADER,
                     "%.2f" % payout, PAYOUT_MEMOS[i % len(PAYOUT_MEMOS)],
                     out_ts))
    for sender, receiver, amount, ts, memo in EVERYDAY:
        rows.append((_uuid("t", sender, receiver, ts), sender, receiver,
                     "%.2f" % amount, memo, ts))
    return sorted(rows, key=lambda r: r[5])


def signal_rows():
    """Returns (SignalId, PersonId, SignalType, Details, EventTime)."""
    return [(_uuid("s", p, t, ts), p, t, d, ts) for p, t, d, ts in SIGNALS]


