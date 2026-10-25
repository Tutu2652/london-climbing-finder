window.CLIMBING_DATA = {
  "_meta": {
    "currency": "GBP",
    "last_research_date": "2026-10-25",
    "sources": [
      "Offer and price re-check across all gym websites 2026-10-25 (Substation day-pass prices from the user)",
      "Gantry Climbing (new gym) from gantryclimbing.com 2026-10-25",
      "official gym websites audited 2026-08-05",
      "web research 2026-07-01",
      "user Google Sheet (incorporated 2026-07-01)"
    ],
    "notes": [
      "Prices are 'pay-as-you-go' single day-pass entries unless stated.",
      "'Standard' in the source sheet = adult peak. 'Concession' = student/senior/UC etc. peak.",
      "Off-peak / peak split: where a gym states exact times they are used; otherwise off-peak is assumed to be weekday daytime until 16:00 and is flagged 'offpeak_assumed'. Peak = weekday evenings + all weekend.",
      "'magic_slots' are special cheap windows (e.g. LCC 'Magic Time' £8, Rise super off-peak £7.50, student-saver days). Slots labelled 'student'/'concession' only apply to concession holders.",
      "Multi-site brands share one price list but off-peak windows differ per centre. City Bouldering is split into separate entries (Aldgate, White City, Stratford, Climbing Co Fulham) because each has its own off-peak schedule. 'offpeak_window' may be a single object or a list of objects (e.g. Stratford has different weekday windows).",
      "Where a value is null it was not publicly listed; run the scraper or check the website.",
      "'ac' ('full'|'partial', absent = no confirmed AC) and 'ac_note' describe air conditioning. User-curated July 2026; verify against gym websites when updating."
    ]
  },
  "student_promotions": [
    {
      "scope": "Climbing District (Tottenham Hale, Bermondsey)",
      "offer": "Student Saver £5 day pass on Mondays and Fridays (student ID or UNiDAYS). Not at London Fields.",
      "source": "https://climbingdistrict.uk/2025/12/the-best-student-climbing-offer-in-london/"
    },
    {
      "scope": "City Bouldering (Aldgate, White City, Stratford, Climbing Co Fulham)",
      "offer": "Bargain £7.50 and Student Saver £9.50 run on different days per centre (see each gym's note). Student Saver anytime: Aldgate Fri, White City Thu, Stratford Mon & Fri, Fulham Tue. Aldgate also runs BOGOF (£17 for two) Saturday eves 5-8pm. Fulham student memberships: 3 months £170, 6 months £320, 12 months £590.",
      "source": "https://www.citybouldering.co.uk/pricing"
    },
    {
      "scope": "The Font (Wandsworth, Borough, Hounslow - Hounslow is cheaper)",
      "offer": "Student Saver £10.50 Mon-Fri before 4pm, Saturday after 4pm and all day Sunday",
      "source": "https://www.the-font.co.uk/student-deals"
    },
    {
      "scope": "Yonder (Walthamstow)",
      "offer": "Student Saver £8 on Tuesdays and Fridays",
      "source": "https://www.thisisyonder.com/yonder-climbing"
    },
    {
      "scope": "London Climbing Centres",
      "offer": "3-month student membership £205; Student Fridays £9.50 at CroyWall",
      "source": "https://londonclimbingcentres.co.uk/prices/"
    }
  ],
  "gyms": [
    {
      "id": "castle",
      "name": "The Castle Climbing Centre",
      "brand": "Independent",
      "type": "ropes+bouldering",
      "area": "Manor House / Finsbury Park",
      "borough": "Hackney",
      "zone": 2,
      "postcode": "N4 2HA",
      "lat": 51.5712,
      "lng": -0.0894,
      "website": "https://www.castle-climbing.co.uk/",
      "logo": "assets/logos/castle.png",
      "logo_background": "#1f232c",
      "prices_url": "https://www.castle-climbing.co.uk/prices",
      "payg": {
        "adult_peak": 18.0,
        "adult_offpeak": null,
        "concession_peak": 12.5,
        "concession_offpeak": 10.5
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "08:00",
        "end": "17:00",
        "concession_only": true,
        "except_bank_holidays": true
      },
      "offpeak_assumed": false,
      "magic_slots": [],
      "student_note": "Concession £12.50 peak / £10.50 off-peak (Mon-Fri 8am-5pm) for students, under-18s, over-60s, eligible benefits and Blue Light holders. Show ID. Step-free entry £5 for disabilities that make stairs unsafe.",
      "registration_fee": 3.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.castle-climbing.co.uk/prices",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Ropes (230+ routes), auto-belay (40+), bouldering, outdoor garden",
      "membership": {
        "full_monthly": 87,
        "concession_monthly": 72
      },
      "address": "Green Lanes, London N4 2HA",
      "passes": "8 sessions £128 (£92 conc)",
      "hours": {
        "Mon": [
          "08:00",
          "22:00"
        ],
        "Tue": [
          "08:00",
          "22:00"
        ],
        "Wed": [
          "08:00",
          "22:00"
        ],
        "Thu": [
          "08:00",
          "22:00"
        ],
        "Fri": [
          "08:00",
          "22:00"
        ],
        "Sat": [
          "09:00",
          "20:00"
        ],
        "Sun": [
          "09:00",
          "20:00"
        ]
      },
      "hours_assumed": false,
      "instagram": "thecastleclimbingcentre",
      "events_url": "https://www.castle-climbing.co.uk/events",
      "classes_url": "https://www.castle-climbing.co.uk/climbing-courses",
      "class_info": "[Taster session](https://www.castle-climbing.co.uk/climbing-courses) £45 (90min); [Bouldering Fundamentals](https://www.castle-climbing.co.uk/climbing-courses) (4-week) and [Learn to Top Rope](https://www.castle-climbing.co.uk/climbing-courses) (1-day) courses; [Bouldering & Roped Climbing Fundamentals 6-week](https://www.castle-climbing.co.uk/climbing-courses) £235 (incl. FREE membership worth £114); [private tuition](https://www.castle-climbing.co.uk/climbing-courses) from 1hr.",
      "socials": [
        "[Top Rope Social](https://www.castle-climbing.co.uk/events/top-rop-social) — Mon & Thu 19:00-21:00, plus Sat 13:45-16:00",
        "[Mixed Bouldering Social](https://www.castle-climbing.co.uk/events/bouldering-social-with-coach) — Mondays (1st Mon coach-led)",
        "[Women's Bouldering Social](https://www.castle-climbing.co.uk/events/womens-bouldering-social-with-coach) — 1st Tue of month (coach-led)",
        "[Original Girlz Social](https://www.castle-climbing.co.uk/events/original-girlz-social) (women) — 2nd Wed of month 19:00-21:00 (free)"
      ]
    },
    {
      "id": "arch_bermondsey",
      "name": "Climbing District - Bermondsey",
      "brand": "Climbing District",
      "type": "bouldering",
      "area": "Bermondsey",
      "borough": "Southwark",
      "zone": 2,
      "postcode": "SE16 4DG",
      "lat": 51.4925,
      "lng": -0.0689,
      "website": "https://climbingdistrict.uk/",
      "logo": "assets/logos/climbing-district.png",
      "prices_url": "https://climbingdistrict.uk/pricing/",
      "payg": {
        "adult_peak": 15.0,
        "adult_offpeak": null,
        "concession_peak": 12.5,
        "concession_offpeak": null
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": true,
      "magic_slots": [
        {
          "days": [
            "Mon",
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 5.0,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £12.50. Student Saver £5 day pass on Mondays & Fridays (student ID or UNiDAYS) at Bermondsey and Tottenham Hale per Climbing District's student-deal page; Tottenham Hale's own page now says 'only available at Tottenham Hale', so confirm at Bermondsey.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://climbingdistrict.uk/pricing/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, training area, MoonBoard, spray wall, large traverse walls",
      "membership": {
        "full_monthly": 72,
        "concession_monthly": 62
      },
      "address": "Building One, Tower Bridge Business Complex, Drummond Road, London SE16 4DG",
      "passes": "10 punches £125 (£110 conc)",
      "hours": {
        "Mon": [
          "06:30",
          "22:30"
        ],
        "Tue": [
          "06:30",
          "22:30"
        ],
        "Wed": [
          "06:30",
          "22:30"
        ],
        "Thu": [
          "06:30",
          "22:30"
        ],
        "Fri": [
          "06:30",
          "22:30"
        ],
        "Sat": [
          "09:00",
          "21:00"
        ],
        "Sun": [
          "09:00",
          "21:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://climbingdistrict.uk/activities/workshops-socials/",
      "instagram": "climbingdistrictlondon",
      "classes_url": "https://climbingdistrict.uk/activities/introduction-lessons/",
      "class_info": "[Introduction Lessons](https://climbingdistrict.uk/activities/introduction-lessons/) £20 (1hr small group).",
      "ac": "partial",
      "ac_note": "AC in training room/slab wall"
    },
    {
      "id": "stronghold",
      "name": "Climbing District - Tottenham Hale",
      "brand": "Climbing District",
      "type": "ropes+bouldering",
      "area": "Tottenham Hale",
      "borough": "Haringey",
      "zone": 3,
      "postcode": "N15 4BE",
      "lat": 51.5896,
      "lng": -0.0599,
      "website": "https://climbingdistrict.uk/climbing-gym/tottenham-hale/",
      "logo": "assets/logos/climbing-district.png",
      "prices_url": "https://climbingdistrict.uk/climbing-gym/tottenham-hale/",
      "payg": {
        "adult_peak": 13.0,
        "adult_offpeak": 8.5,
        "concession_peak": 10.5,
        "concession_offpeak": null
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Fri"
        ],
        "start": "07:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": true,
      "magic_slots": [
        {
          "days": [
            "Mon",
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 5.0,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £10.50 (one price, no off-peak rate); under-18 £8.50. Adult off-peak £8.50 on Mondays & Fridays (off-peak hours not stated on the Tottenham Hale page; Climbing District's FAQ says weekday off-peak runs until 4pm). Student Saver £5 day pass on Mondays & Fridays (student ID or UNiDAYS).",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://climbingdistrict.uk/climbing-gym/tottenham-hale/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Ropes, bouldering, gym",
      "membership": {
        "full_monthly": 60,
        "concession_monthly": 50
      },
      "address": "The Archives, Unit 10 High Cross Centre, Fountayne Road, London N15 4BE",
      "passes": "10 punches £110 (£75 off-peak, £90 conc, £70 under-18); one month £67 (£47 off-peak, £57 conc); 6 months £300 (£200 off-peak, £250 conc); off-peak monthly membership £40. All Tottenham Hale only.",
      "hours": {
        "Mon": [
          "07:00",
          "22:30"
        ],
        "Tue": [
          "07:00",
          "22:30"
        ],
        "Wed": [
          "08:00",
          "22:30"
        ],
        "Thu": [
          "08:00",
          "22:30"
        ],
        "Fri": [
          "07:00",
          "22:30"
        ],
        "Sat": [
          "09:00",
          "19:00"
        ],
        "Sun": [
          "09:00",
          "21:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://climbingdistrict.uk/activities/workshops-socials/",
      "instagram": "stronghold_uk",
      "classes_url": "https://climbingdistrict.uk/activities/introduction-lessons/",
      "class_info": "[Introduction Lessons](https://climbingdistrict.uk/activities/introduction-lessons/) £20 (1hr small group).",
      "socials": [
        "[Climb With Us](https://climbingdistrict.uk/activities/workshops-socials/) — 1.5hr coached social, £5 (excl. entry), V1-3 & V3-5",
        "[Sister Sessions](https://climbingdistrict.uk/activities/sister-sessions/) (Women+)"
      ]
    },
    {
      "id": "climbingdistrict_londonfields",
      "name": "Climbing District - London Fields",
      "brand": "Climbing District",
      "type": "bouldering",
      "area": "London Fields",
      "borough": "Hackney",
      "zone": 2,
      "postcode": "E8 4FX",
      "lat": 51.541,
      "lng": -0.057,
      "website": "https://climbingdistrict.uk/",
      "logo": "assets/logos/climbing-district.png",
      "prices_url": "https://climbingdistrict.uk/pricing/",
      "payg": {
        "adult_peak": 15.0,
        "adult_offpeak": null,
        "concession_peak": 12.5,
        "concession_offpeak": null
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": true,
      "magic_slots": [],
      "student_note": "Concession £12.50. The £5 student day pass is not offered at London Fields (Tottenham Hale and Bermondsey only).",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://climbingdistrict.uk/pricing/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, Kilter board",
      "membership": {
        "full_monthly": 72,
        "concession_monthly": 62
      },
      "address": "Unit 30, Canal Place, Andrews Road, London E8 4FX",
      "passes": "10 punches £125 (£110 conc)",
      "hours": {
        "Mon": [
          "06:30",
          "22:30"
        ],
        "Tue": [
          "06:30",
          "22:30"
        ],
        "Wed": [
          "06:30",
          "22:30"
        ],
        "Thu": [
          "06:30",
          "22:30"
        ],
        "Fri": [
          "06:30",
          "22:30"
        ],
        "Sat": [
          "09:00",
          "21:00"
        ],
        "Sun": [
          "09:00",
          "21:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://climbingdistrict.uk/activities/workshops-socials/",
      "instagram": "stronghold_ldnf",
      "classes_url": "https://climbingdistrict.uk/activities/introduction-lessons/",
      "class_info": "[Introduction Lessons](https://climbingdistrict.uk/activities/introduction-lessons/) £20 (1hr small group).",
      "socials": [
        "[Climb With Us](https://climbingdistrict.uk/activities/workshops-socials/) — 1.5hr coached social, £5 (excl. entry), V1-3 & V3-5",
        "[Sister Sessions](https://climbingdistrict.uk/activities/sister-sessions/) (Women+)"
      ],
      "ac": "full",
      "ac_note": "air conditioned"
    },
    {
      "id": "cb_aldgate",
      "name": "City Bouldering Aldgate",
      "brand": "City Bouldering",
      "type": "bouldering",
      "area": "Aldgate",
      "borough": "City of London",
      "zone": 1,
      "postcode": "EC3N 1AH",
      "lat": 51.5138,
      "lng": -0.0755,
      "website": "https://www.citybouldering.co.uk/locations/city-bouidering-aldgate",
      "logo": "assets/logos/city-bouldering.png",
      "prices_url": "https://www.citybouldering.co.uk/pricing",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 14.0,
        "concession_peak": 15.0,
        "concession_offpeak": 14.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "09:00",
        "end": "15:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sun"
          ],
          "start": "16:30",
          "end": "20:00",
          "price": 7.5,
          "label": "Bargain",
          "prebook": true
        },
        {
          "days": [
            "Mon"
          ],
          "start": "08:00",
          "end": "10:00",
          "price": 7.5,
          "label": "Bargain",
          "except_bank_holidays": true,
          "prebook": true
        },
        {
          "days": [
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 9.5,
          "label": "Student Saver (concession)"
        },
        {
          "days": [
            "Sat"
          ],
          "start": "17:00",
          "end": "20:00",
          "price": 8.5,
          "label": "BOGOF (£17 for 2, students £15 for 2)",
          "prebook": true
        }
      ],
      "student_note": "Concession £15. Bargain £7.50 (pre-book); Student Saver £9.50 Fridays; BOGOF £17 for two on Saturday eves 5-8pm.",
      "registration_fee": 3.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.citybouldering.co.uk/pricing",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering (100+ problems), traverse, Kilter board, gym/training area, showers",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "33 Aldgate High Street, London EC3N 1AH",
      "passes": "10 climbs £148 (£130 conc); 20 climbs £280 (£246 conc)",
      "hours": {
        "Mon": [
          "06:30",
          "23:00"
        ],
        "Tue": [
          "06:30",
          "23:00"
        ],
        "Wed": [
          "06:30",
          "23:00"
        ],
        "Thu": [
          "06:30",
          "23:00"
        ],
        "Fri": [
          "06:30",
          "23:00"
        ],
        "Sat": [
          "09:00",
          "22:00"
        ],
        "Sun": [
          "09:00",
          "22:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.citybouldering.co.uk/climbing-events-for-adults",
      "instagram": "aldgatecitybouldering",
      "classes_url": "https://www.citybouldering.co.uk/coaching",
      "class_info": "[Intro to Bouldering](https://www.citybouldering.co.uk/events/intro-to-bouldering) £36 (1hr, incl. 2 weeks free, all 4 sites); [Fundamentals V0-V1](https://www.citybouldering.co.uk/events/fundamentals) £85 (£50 members, all 4 sites); [Intermediate V2-V4](https://www.citybouldering.co.uk/events/technical-foundations) £85 (£50 members, not Fulham); [Advanced Tactics V4-V6](https://www.citybouldering.co.uk/events/elite-tactics) £90 (£60 members); 1hr [private coaching](https://www.citybouldering.co.uk/events/private-coaching) £50 member / £60",
      "socials": [
        "[Beginner's Social](https://www.citybouldering.co.uk/events/beginner) — new boulderers (see website for time)",
        "[Monday Madness](https://www.citybouldering.co.uk/events/monday-madness-social) — Mon 19:00-20:30, coached on the new set, £17 (members free)"
      ],
      "ac": "partial",
      "ac_note": "AC in annex only"
    },
    {
      "id": "cb_whitecity",
      "name": "City Bouldering White City",
      "brand": "City Bouldering",
      "type": "ropes+bouldering",
      "area": "White City / Shepherd's Bush",
      "borough": "Hammersmith & Fulham",
      "zone": 2,
      "postcode": "W12 7HB",
      "lat": 51.5074,
      "lng": -0.2242,
      "website": "https://www.citybouldering.co.uk/locations/city-bouldering-white-city",
      "logo": "assets/logos/city-bouldering.png",
      "prices_url": "https://www.citybouldering.co.uk/pricing",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 14.0,
        "concession_peak": 15.0,
        "concession_offpeak": 14.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "10:00",
        "end": "15:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Tue"
          ],
          "start": "09:00",
          "end": "15:00",
          "price": 7.5,
          "label": "Bargain",
          "prebook": true
        },
        {
          "days": [
            "Sun"
          ],
          "start": "17:00",
          "end": "21:00",
          "price": 7.5,
          "label": "Bargain",
          "prebook": true
        },
        {
          "days": [
            "Thu"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 9.5,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £15. Bargain £7.50 (pre-book); Student Saver £9.50 Thursdays.",
      "registration_fee": 3.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.citybouldering.co.uk/pricing",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, auto-belay (9 routes)",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "Ariel Way, White City, London W12 7HB",
      "passes": "10 climbs £148 (£130 conc); 20 climbs £280 (£246 conc)",
      "hours": {
        "Mon": [
          "10:00",
          "22:30"
        ],
        "Tue": [
          "10:00",
          "22:30"
        ],
        "Wed": [
          "10:00",
          "22:30"
        ],
        "Thu": [
          "10:00",
          "22:30"
        ],
        "Fri": [
          "10:00",
          "22:30"
        ],
        "Sat": [
          "09:00",
          "21:00"
        ],
        "Sun": [
          "09:00",
          "21:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.citybouldering.co.uk/climbing-events-for-adults",
      "instagram": "cityboulderingwestfield",
      "classes_url": "https://www.citybouldering.co.uk/coaching",
      "class_info": "[Intro to Bouldering](https://www.citybouldering.co.uk/events/intro-to-bouldering) £36 (1hr, incl. 2 weeks free, all 4 sites); [Fundamentals V0-V1](https://www.citybouldering.co.uk/events/fundamentals) £85 (£50 members, all 4 sites); [Intermediate V2-V4](https://www.citybouldering.co.uk/events/technical-foundations) £85 (£50 members, not Fulham); [Advanced Tactics V4-V6](https://www.citybouldering.co.uk/events/elite-tactics) £90 (£60 members); 1hr [private coaching](https://www.citybouldering.co.uk/events/private-coaching) £50 member / £60",
      "socials": [
        "[Beginner's Social](https://www.citybouldering.co.uk/events/beginner) — new boulderers (see website for time)",
        "[Student Social](https://www.citybouldering.co.uk/events/student-social) — Thu 18:00-21:00, £9.50 entry",
        "[Members Social](https://www.citybouldering.co.uk/events/members-social) — Fri, members only (free)",
        "[Drill It!](https://www.citybouldering.co.uk/events/drill-it) — Tue 19:00-20:00, coached drills, £14.95 (members free)"
      ]
    },
    {
      "id": "cb_stratford",
      "name": "City Bouldering Stratford",
      "brand": "City Bouldering",
      "type": "bouldering",
      "area": "Stratford",
      "borough": "Newham",
      "zone": 2,
      "postcode": "E15 2JA",
      "lat": 51.5416,
      "lng": -0.0042,
      "website": "https://www.citybouldering.co.uk/locations/city-bouldering-stratford",
      "logo": "assets/logos/city-bouldering.png",
      "prices_url": "https://www.citybouldering.co.uk/pricing",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 14.0,
        "concession_peak": 15.0,
        "concession_offpeak": 14.0
      },
      "offpeak_window": [
        {
          "days": [
            "Tue",
            "Wed",
            "Thu"
          ],
          "start": "09:00",
          "end": "15:00",
          "concession_only": false
        },
        {
          "days": [
            "Mon",
            "Fri"
          ],
          "start": "10:00",
          "end": "15:00",
          "concession_only": false
        }
      ],
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Wed"
          ],
          "start": "09:00",
          "end": "15:00",
          "price": 7.5,
          "label": "Bargain",
          "prebook": true
        },
        {
          "days": [
            "Sat"
          ],
          "start": "16:00",
          "end": "21:00",
          "price": 7.5,
          "label": "Bargain",
          "prebook": true
        },
        {
          "days": [
            "Mon",
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 9.5,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £15. Bargain £7.50 (pre-book); Student Saver £9.50 Mondays & Fridays.",
      "registration_fee": 3.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.citybouldering.co.uk/pricing",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, Kilter board, fitness area",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "234 High Street, Stratford, London E15 2JA",
      "passes": "10 climbs £148 (£130 conc); 20 climbs £280 (£246 conc)",
      "hours": {
        "Mon": [
          "10:00",
          "22:30"
        ],
        "Tue": [
          "07:00",
          "22:30"
        ],
        "Wed": [
          "07:00",
          "22:30"
        ],
        "Thu": [
          "07:00",
          "22:30"
        ],
        "Fri": [
          "10:00",
          "22:30"
        ],
        "Sat": [
          "09:00",
          "21:00"
        ],
        "Sun": [
          "09:00",
          "21:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.citybouldering.co.uk/climbing-events-for-adults",
      "instagram": "cityboulderingstratford",
      "classes_url": "https://www.citybouldering.co.uk/coaching",
      "class_info": "[Intro to Bouldering](https://www.citybouldering.co.uk/events/intro-to-bouldering) £36 (1hr, incl. 2 weeks free, all 4 sites); [Fundamentals V0-V1](https://www.citybouldering.co.uk/events/fundamentals) £85 (£50 members, all 4 sites); [Intermediate V2-V4](https://www.citybouldering.co.uk/events/technical-foundations) £85 (£50 members, not Fulham); [Advanced Tactics V4-V6](https://www.citybouldering.co.uk/events/elite-tactics) £90 (£60 members); 1hr [private coaching](https://www.citybouldering.co.uk/events/private-coaching) £50 member / £60",
      "socials": [
        "[Beginner's Social](https://www.citybouldering.co.uk/events/beginner) — new boulderers (see website for time)",
        "[Routes & Roots](https://www.citybouldering.co.uk/events/routes-roots) — Sun 18:00-21:00 (Black & POC climbers), £9.50 incl shoes, members free",
        "[Send it Sisters](https://www.citybouldering.co.uk/events/stratford-send-sisters) (Womxns+) — Thu 19:00",
        "[Queer Climbers Social](https://www.citybouldering.co.uk/events/queer-climbers-social) — Fri 19:00-20:00, £9.50 incl shoes, members free",
        "[Geeks Who Climb](https://www.citybouldering.co.uk/events/geeks-who-climb) — 1st Sat monthly, £7.50, members free",
        "[Bend & Boulder](https://www.citybouldering.co.uk/events/bend-and-boulder-mobility-sessions) — 45min mobility/stretching, £20 incl entry (members free)"
      ]
    },
    {
      "id": "mileend",
      "name": "Mile End Climbing Wall",
      "brand": "Independent",
      "type": "ropes+bouldering",
      "area": "Mile End",
      "borough": "Tower Hamlets",
      "zone": 2,
      "postcode": "E3 5BN",
      "lat": 51.5265,
      "lng": -0.0333,
      "website": "https://www.mileendwall.org.uk/",
      "logo": "assets/logos/mileend.png",
      "prices_url": "https://www.mileendwall.org.uk/memberships-and-entries",
      "payg": {
        "adult_peak": 14.5,
        "adult_offpeak": null,
        "concession_peak": 10.0,
        "concession_offpeak": null
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Mon",
            "Tue",
            "Wed",
            "Thu",
            "Fri"
          ],
          "start": "07:00",
          "end": "09:30",
          "price": 5.0,
          "label": "super off-peak",
          "except_bank_holidays": true
        },
        {
          "days": [
            "Wed",
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 7.0,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Students £7 on Wed & Fri (student ID). Other concessions (over-65s, Universal Credit, PIP) £10. Super off-peak £5, Mon-Fri 7-9:30am.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.mileendwall.org.uk/memberships-and-entries",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Ropes, auto-belay, bouldering, outdoor wall",
      "membership": {
        "full_monthly": 60,
        "concession_monthly": null
      },
      "address": "Haverfield Road, London E3 5BN",
      "passes": "30-day pass £75 (£60 conc); 10-climb punchcard £120; student 20-climb £160",
      "hours": {
        "Mon": [
          "07:00",
          "22:00"
        ],
        "Tue": [
          "07:00",
          "22:00"
        ],
        "Wed": [
          "07:00",
          "22:00"
        ],
        "Thu": [
          "07:00",
          "22:00"
        ],
        "Fri": [
          "07:00",
          "22:00"
        ],
        "Sat": [
          "09:00",
          "20:00"
        ],
        "Sun": [
          "09:00",
          "20:00"
        ]
      },
      "hours_assumed": false,
      "instagram": "mileendclimbing",
      "events_url": "https://www.mileendwall.org.uk/index.php/socials-and-events",
      "classes_url": "https://www.mileendwall.org.uk/",
      "class_info": "[Bouldering Induction](https://www.mileendwall.org.uk/index.php/courses/categories/bouldering-courses) £23 (1hr); [Roped Climbing Induction](https://www.mileendwall.org.uk/index.php/courses/categories/roped-courses) £75 (5h45m); [Lead Climbing Essentials](https://www.mileendwall.org.uk/index.php/courses/categories/roped-courses) £120 (7h30m); [Beginner Setting Experience](https://www.mileendwall.org.uk/index.php/courses/categories/route-setting) £120. Ages 14+.",
      "route_setting_url": "https://www.mileendwall.org.uk/about-us",
      "socials": [
        "[Women+ social](https://www.mileendwall.org.uk/index.php/socials-and-events) — Thu 19:25–20:40 (free social + shoe hire, pay entry)",
        "[Queer climb social](https://www.mileendwall.org.uk/index.php/socials-and-events) — first Monday of the month"
      ]
    },
    {
      "id": "the_font_wandsworth",
      "name": "The Font Wandsworth",
      "brand": "The Font",
      "type": "ropes+bouldering",
      "area": "Wandsworth",
      "borough": "Wandsworth",
      "zone": 2,
      "postcode": "SW18 4TF",
      "lat": 51.4549,
      "lng": -0.1931,
      "website": "https://www.the-font.co.uk/wandsworth",
      "logo": "assets/logos/the-font.png",
      "prices_url": "https://www.the-font.co.uk/wandsworth/prices",
      "payg": {
        "adult_peak": 18.0,
        "adult_offpeak": null,
        "concession_peak": 15.5,
        "concession_offpeak": 10.5
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": true
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sat"
          ],
          "start": "16:00",
          "end": "23:59",
          "price": 10.5,
          "label": "Student Saver (concession)"
        },
        {
          "days": [
            "Sun"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 10.5,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £15.50. £10.50 off-peak pass before 4pm Mon-Fri, after 4pm Sat & all-day Sun (student ID needed). Student term-time membership from £42/mo (12-month Bronze; 3/6/9-month options; Silver +fitness classes, Gold unlimited fitness).",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://www.the-font.co.uk/wandsworth/prices",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, ropes/auto-belay, fitness, yoga",
      "membership": {
        "full_monthly": 80,
        "concession_monthly": 65
      },
      "address": "52-58 Garratt Lane, London SW18 4TF",
      "passes": "5 passes £85 (£72.50 conc); 10 passes £155 (£130 conc)",
      "hours": {
        "Mon": [
          "06:15",
          "22:00"
        ],
        "Tue": [
          "06:15",
          "22:00"
        ],
        "Wed": [
          "06:15",
          "22:00"
        ],
        "Thu": [
          "06:15",
          "22:00"
        ],
        "Fri": [
          "06:15",
          "22:00"
        ],
        "Sat": [
          "09:00",
          "18:00"
        ],
        "Sun": [
          "09:00",
          "18:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.the-font.co.uk/whats-on",
      "instagram": "thefontwandsworth",
      "classes_url": "https://www.the-font.co.uk/adult-coaching",
      "class_info": "[Intro to Bouldering](https://www.the-font.co.uk/adult-coaching) £32 (1hr); [Intro to Roped Climbing](https://www.the-font.co.uk/adult-coaching) £120 (6hr); [V0-V2 technique workshops](https://www.the-font.co.uk/adult-coaching) (footwork, handholds, route reading) £27 (90min). Coaching at Wandsworth.",
      "socials": [
        "[Women's Climbing Social](https://www.the-font.co.uk/news-and-articles/social-schedule-wandsworth) — 19:00-21:00 (Wandsworth 2.0)"
      ]
    },
    {
      "id": "the_font_borough",
      "name": "The Font Borough",
      "brand": "The Font",
      "type": "bouldering",
      "area": "Borough",
      "borough": "Southwark",
      "zone": 1,
      "postcode": "SE1",
      "lat": 51.5015,
      "lng": -0.094,
      "website": "https://www.the-font.co.uk/borough",
      "logo": "assets/logos/the-font.png",
      "prices_url": "https://www.the-font.co.uk/borough/prices",
      "payg": {
        "adult_peak": 18.0,
        "adult_offpeak": null,
        "concession_peak": 15.5,
        "concession_offpeak": 10.5
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": true
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sat"
          ],
          "start": "16:00",
          "end": "23:59",
          "price": 10.5,
          "label": "Student Saver (concession)"
        },
        {
          "days": [
            "Sun"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 10.5,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £15.50. £10.50 off-peak pass before 4pm Mon-Fri, after 4pm Sat & all-day Sun (student ID needed). Student term-time membership from £42/mo (12-month Bronze; 3/6/9-month options; Silver +fitness classes, Gold unlimited fitness).",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://www.the-font.co.uk/borough/prices",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, fitness",
      "membership": {
        "full_monthly": 80,
        "concession_monthly": 65
      },
      "address": "Unit 1B, Triptych Place, 185 Park Street, Bankside, London SE1 9BL",
      "passes": "5 passes £85 (£72.50 conc); 10 passes £155 (£130 conc)",
      "hours": {
        "Mon": [
          "07:00",
          "22:00"
        ],
        "Tue": [
          "07:00",
          "22:00"
        ],
        "Wed": [
          "07:00",
          "22:00"
        ],
        "Thu": [
          "07:00",
          "22:00"
        ],
        "Fri": [
          "07:00",
          "22:00"
        ],
        "Sat": [
          "10:00",
          "18:00"
        ],
        "Sun": [
          "10:00",
          "18:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.the-font.co.uk/whats-on",
      "instagram": "thefontborough",
      "classes_url": "https://www.the-font.co.uk/adult-coaching",
      "class_info": "Bouldering intro sessions & coaching workshops (prices at booking). Roped climbing & workshops run at Wandsworth.",
      "socials": [
        "[Women's Climbing Social](https://www.the-font.co.uk/news-and-articles/social-schedule-borough) — 19:00-21:00",
        "[Women's Tension Board Social](https://www.the-font.co.uk/news-and-articles/social-schedule-borough) — alt Tuesdays 18:00-20:00 (V3+)"
      ]
    },
    {
      "id": "the_font_hounslow",
      "name": "The Font Hounslow",
      "brand": "The Font",
      "type": "bouldering",
      "area": "Hounslow",
      "borough": "Hounslow",
      "zone": 4,
      "postcode": "TW3",
      "lat": 51.467,
      "lng": -0.361,
      "website": "https://www.the-font.co.uk/hounslow",
      "logo": "assets/logos/the-font.png",
      "prices_url": "https://www.the-font.co.uk/hounslow/prices",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": null,
        "concession_peak": 14.5,
        "concession_offpeak": 10.5
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": true
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sat"
          ],
          "start": "16:00",
          "end": "23:59",
          "price": 10.5,
          "label": "Student Saver (concession)"
        },
        {
          "days": [
            "Sun"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 10.5,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £14.50. £10.50 off-peak pass before 4pm Mon-Fri, after 4pm Sat & all-day Sun (student ID needed). Student term-time membership from £42/mo (12-month Bronze; 3/6/9-month options; Silver +fitness classes, Gold unlimited fitness).",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://www.the-font.co.uk/hounslow/prices",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, MoonBoard, Kilter board, fitness gym, café, showers, lockers, workspace",
      "membership": {
        "full_monthly": 75,
        "concession_monthly": 60
      },
      "address": "High Street Quarter, 9 Smithy Lane, Hounslow TW3 1EH",
      "passes": "5 passes £80 (£67.50 conc); 10 passes £150 (£125 conc)",
      "hours": {
        "Mon": [
          "10:00",
          "21:30"
        ],
        "Tue": [
          "10:00",
          "21:30"
        ],
        "Wed": [
          "10:00",
          "21:30"
        ],
        "Thu": [
          "10:00",
          "21:30"
        ],
        "Fri": [
          "10:00",
          "21:30"
        ],
        "Sat": [
          "10:00",
          "18:00"
        ],
        "Sun": [
          "10:00",
          "18:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.the-font.co.uk/whats-on",
      "instagram": "thefonthounslow",
      "classes_url": "https://www.the-font.co.uk/adult-coaching",
      "class_info": "Bouldering intro sessions & coaching workshops (prices at booking). Roped climbing & workshops run at Wandsworth.",
      "socials": [
        "[Board Social](https://www.the-font.co.uk/news-and-articles/social-schedule-hounslow) — Mon 18:30-20:30",
        "[Women’s Social](https://www.the-font.co.uk/news-and-articles/social-schedule-hounslow) — Wed 19:00-20:30",
        "[Open Social](https://www.the-font.co.uk/news-and-articles/social-schedule-hounslow) — Thu 18:00-20:00"
      ]
    },
    {
      "id": "yonder",
      "name": "Yonder Climbing",
      "brand": "Independent",
      "type": "bouldering",
      "area": "Walthamstow / Blackhorse Road",
      "borough": "Waltham Forest",
      "zone": 3,
      "postcode": "E17 6DP",
      "lat": 51.5868,
      "lng": -0.0413,
      "website": "https://www.thisisyonder.com/",
      "logo": "assets/logos/yonder.png",
      "prices_url": "https://www.thisisyonder.com/yonder-climbing",
      "payg": {
        "adult_peak": 15.0,
        "adult_offpeak": null,
        "concession_peak": 12.0,
        "concession_offpeak": null
      },
      "offpeak_window": null,
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Tue",
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 8.0,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £12 / under-18 £10. Student £8 on Tuesdays & Fridays.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.thisisyonder.com/yonder-climbing",
        "method": "image-board",
        "needs_js": true
      },
      "facilities": "Bouldering, yoga, coworking",
      "membership": {
        "full_monthly": 70,
        "concession_monthly": 60
      },
      "address": "4-6 Hookers Road, Walthamstow, London E17 6DP",
      "passes": "10 pass £135 (£110 conc); 5 pass £72",
      "hours": {
        "Mon": [
          "07:00",
          "22:00"
        ],
        "Tue": [
          "07:00",
          "22:00"
        ],
        "Wed": [
          "07:00",
          "22:00"
        ],
        "Thu": [
          "07:00",
          "22:00"
        ],
        "Fri": [
          "07:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "18:00"
        ],
        "Sun": [
          "08:00",
          "18:00"
        ]
      },
      "hours_assumed": false,
      "price_images": [
        "baa9bd35-8434-4687-98e5-cce5868b03ba/PASS+PACKS.jpg",
        "1286907c-514a-46a7-b648-a99ffb9a6eca/MEMBERSHIPS.jpg",
        "4f12153b-a6fa-4ede-a729-cc89a619f2de/PASS+PACKS_1.jpg"
      ],
      "price_image_note": "Prices are image boards on thisisyonder.com/yonder-climbing; auto-check watches whether these image assets are swapped (a change means prices likely changed - re-read the board manually).",
      "events_url": "https://www.thisisyonder.com/events-social-and-courses",
      "instagram": "yonder.climbing",
      "classes_url": "https://www.thisisyonder.com/coaching-courses-intro",
      "class_info": "[The Boulder Experience](https://www.thisisyonder.com/coaching-courses-intro) £25 (1hr intro, incl. kit + day entry). [Ground Up bouldering essentials](https://www.thisisyonder.com/coaching-courses-intro) £85 (3x1.5hr, incl. membership during course + 20% off shoes).",
      "socials": [
        "[Boulder Buds socials](https://www.thisisyonder.com/events-social-and-courses) — open, queer and running-club sessions (see calendar)",
        "[BSL Bouldering Social](https://www.thisisyonder.com/events-social-and-courses) — selected Mondays 18:30-21:30, £9.50 incl. shoes"
      ],
      "ac": "full",
      "ac_note": "air conditioning and fans"
    },
    {
      "id": "the_reach",
      "name": "The Reach",
      "brand": "Independent",
      "type": "ropes+bouldering",
      "area": "Woolwich",
      "borough": "Greenwich",
      "zone": 4,
      "postcode": "SE18 5NR",
      "lat": 51.49,
      "lng": 0.069,
      "website": "https://thereach.org.uk/",
      "logo": "assets/logos/the-reach.png",
      "prices_url": "https://thereach.org.uk/prices",
      "payg": {
        "adult_peak": 15.0,
        "adult_offpeak": 12.0,
        "concession_peak": 12.0,
        "concession_offpeak": 10.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false,
        "except_bank_holidays": true
      },
      "offpeak_assumed": false,
      "magic_slots": [],
      "student_note": "Concession £12 peak / £10 off-peak (students, over-65s, paraclimbers, means-tested benefits). Off-peak Mon-Fri before 4pm.",
      "registration_fee": 5.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://thereach.org.uk/prices",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Ropes (65 routes), lead, top-rope, auto-belay (12), bouldering",
      "membership": {
        "full_monthly": 59,
        "concession_monthly": null
      },
      "address": "Unit 6, Mellish Industrial Estate, Harrington Way, Woolwich, London SE18 5NR",
      "passes": "10-session pack £125 (no expiry)",
      "hours": {
        "Mon": [
          "10:00",
          "22:00"
        ],
        "Tue": [
          "10:00",
          "22:00"
        ],
        "Wed": [
          "10:00",
          "22:00"
        ],
        "Thu": [
          "10:00",
          "22:00"
        ],
        "Fri": [
          "10:00",
          "22:00"
        ],
        "Sat": [
          "10:00",
          "19:00"
        ],
        "Sun": [
          "10:00",
          "19:00"
        ]
      },
      "hours_assumed": false,
      "route_setting_url": "https://thereach.org.uk/walls",
      "events_url": "https://thereach.org.uk/events",
      "instagram": "thereachclimbingwall",
      "classes_url": "https://thereach.org.uk/courses",
      "class_info": "[Taster session](https://thereach.org.uk/courses/taster-session) £20; [Beginners Course](https://thereach.org.uk/courses/reach-beginners-course) £70; [Skills courses](https://thereach.org.uk/courses/all) £25 each; [Learn to Lead](https://thereach.org.uk/courses/learn-lead-climb) £75; [1-to-1 coaching](https://thereach.org.uk/courses/development-coaching-1-1) £120."
    },
    {
      "id": "rise",
      "name": "Rise Climbing",
      "brand": "Independent",
      "type": "bouldering",
      "area": "Canning Town",
      "borough": "Newham",
      "zone": 3,
      "postcode": "E16 1ZA",
      "lat": 51.514,
      "lng": 0.008,
      "website": "https://www.rise-climbing.com/",
      "logo": "assets/logos/rise.png",
      "prices_url": "https://www.rise-climbing.com/entry-memberships",
      "payg": {
        "adult_peak": 16.0,
        "adult_offpeak": 12.5,
        "concession_peak": 12.0,
        "concession_offpeak": 10.5
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false,
        "except_bank_holidays": true
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Tue",
            "Thu"
          ],
          "start": "06:00",
          "end": "10:00",
          "price": 7.5,
          "label": "super off-peak"
        },
        {
          "days": [
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 7.5,
          "label": "Student Friday (concession, incl. shoes)"
        },
        {
          "days": [
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 7.5,
          "label": "Board Friday (board only)"
        }
      ],
      "student_note": "Concession £12 peak / £10.50 off-peak (Mon-Fri before 4pm, excl. bank holidays). Super off-peak £7.50 Tue & Thu before 10am. Student Fridays £7.50 (incl. shoes); Board Friday £7.50 (board only). Pay-what-you-can available.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.rise-climbing.com/entry-memberships",
        "method": "regex",
        "needs_js": true
      },
      "facilities": "Bouldering, traverse, TB2, MoonBoard, set board, gym/training area, café/bar, showers",
      "membership": {
        "full_monthly": 68.5,
        "concession_monthly": 55.5
      },
      "address": "Unit 6, Caxton Works, Hoy Street, Canning Town, London E16 1ZA",
      "passes": "10 punches £135 (£105 conc, no expiry); 1-month pass £75 (£62 conc)",
      "hours": {
        "Mon": [
          "10:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "10:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "10:00",
          "22:00"
        ],
        "Sat": [
          "09:00",
          "18:00"
        ],
        "Sun": [
          "09:00",
          "18:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.rise-climbing.com/events-socials",
      "instagram": "theriseclimbingproject",
      "classes_url": "https://www.rise-climbing.com/courses-coaching",
      "class_info": "FREE [Improvers classes](https://www.rise-climbing.com/improvers) (monthly, group coaching — just pay entry); [1-to-1 coaching](https://www.rise-climbing.com/private-coaching) £40 members / £65 non (1hr, incl. entry + shoes; +£20 per extra person).",
      "socials": [
        "Free monthly community socials (chess, mahjong, life drawing, karaoke…) — free with entry — [details](https://www.rise-climbing.com/events-socials)"
      ]
    },
    {
      "id": "sen",
      "name": "Sen Climbing",
      "brand": "Independent",
      "type": "bouldering",
      "area": "Lambeth North",
      "borough": "Lambeth",
      "zone": 1,
      "postcode": "SE1 7DR",
      "lat": 51.499,
      "lng": -0.112,
      "website": "https://www.climbinggymsen.co.uk/",
      "logo": "assets/logos/sen.png",
      "prices_url": "https://www.climbinggymsen.co.uk/",
      "payg": {
        "adult_peak": 16.0,
        "adult_offpeak": null,
        "concession_peak": 14.0,
        "concession_offpeak": null
      },
      "offpeak_window": null,
      "offpeak_assumed": false,
      "magic_slots": [],
      "student_note": "Concession £14 for full-time students (physical ID) or Team GB. No off-peak rate.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.climbinggymsen.co.uk/",
        "method": "regex",
        "needs_js": true
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 72.5,
        "concession_monthly": 62.5
      },
      "address": "Railway Arch 210, Newnham Terrace, London SE1 7DR",
      "passes": "10 day passes £135 (£115 conc)",
      "hours": {
        "Mon": [
          "11:00",
          "22:00"
        ],
        "Tue": [
          "11:00",
          "22:00"
        ],
        "Wed": [
          "11:00",
          "22:00"
        ],
        "Thu": [
          "11:00",
          "22:00"
        ],
        "Fri": [
          "11:00",
          "22:00"
        ],
        "Sat": [
          "09:00",
          "20:00"
        ],
        "Sun": [
          "09:00",
          "20:00"
        ]
      },
      "hours_assumed": false,
      "instagram": "climbing_gym_sen",
      "classes_url": "https://www.climbinggymsen.co.uk/firstvisit",
      "class_info": "[Coaching](https://www.climbinggymsen.co.uk/firstvisit) 60-90min: Beginner £40, Intermediate £55, Advanced £65 (+£10-£20 per extra climber)."
    },
    {
      "id": "westway",
      "name": "Westway Climbing Centre",
      "brand": "Independent",
      "type": "ropes+bouldering",
      "area": "White City / Ladbroke Grove",
      "borough": "Hammersmith & Fulham",
      "zone": 2,
      "postcode": "W10 6RP",
      "lat": 51.521,
      "lng": -0.224,
      "website": "https://www.everyoneactive.com/centre/westway-sports-fitness-centre/climbing-centre/",
      "logo": "assets/logos/westway.jpg",
      "prices_url": "https://www.everyoneactive.com/centre/westway-sports-fitness-centre/pricelist/",
      "payg": {
        "adult_peak": 16.5,
        "adult_offpeak": 13.5,
        "concession_peak": 13.0,
        "concession_offpeak": 10.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "08:00",
        "end": "17:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [],
      "student_note": "Concession £13 peak / £10 off-peak.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://www.everyoneactive.com/centre/westway-sports-fitness-centre/pricelist/",
        "method": "regex",
        "needs_js": true
      },
      "facilities": "Ropes (350+ routes), lead, top-rope, auto-belay (8), bouldering, training rooms/boards",
      "membership": {
        "full_monthly": 82,
        "concession_monthly": 68
      },
      "address": "Westway Sports Centre, 1 Crowthorne Road, London W10 6RP",
      "passes": "5 for £50; 10 for £90; 20 for £170",
      "hours": {
        "Mon": [
          "08:00",
          "21:45"
        ],
        "Tue": [
          "08:00",
          "21:45"
        ],
        "Wed": [
          "08:00",
          "21:45"
        ],
        "Thu": [
          "08:00",
          "21:45"
        ],
        "Fri": [
          "08:00",
          "21:45"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": false,
      "instagram": "westway_climbing",
      "events_url": "https://www.everyoneactive.com/centre/westway-sports-fitness-centre/climbing-events/",
      "classes_url": "https://www.everyoneactive.com/centre/westway-sports-fitness-centre/adult-climbing/",
      "class_info": "[Adult taster](https://www.everyoneactive.com/centre/westway-sports-fitness-centre/adult-climbing/) £30 (90min, incl. shoes + harness); beginner course £120; 1-to-1 coaching from £65/hr.",
      "socials": [
        "[Adult Club Night](https://www.everyoneactive.com/centre/westway-sports-fitness-centre/adult-climbing/) — Mon & Wed 19:00-21:00 (competent rope climbers, free with membership or pay entry)",
        "[Women’s Climbing Morning](https://www.everyoneactive.com/centre/westway-sports-fitness-centre/adult-climbing/) — Wed 10:00-12:00 (instructor-led, free with membership or pay entry)"
      ]
    },
    {
      "id": "climbing_co_fulham",
      "name": "Climbing Co Fulham (City Bouldering)",
      "brand": "City Bouldering",
      "type": "bouldering",
      "area": "Parsons Green",
      "borough": "Hammersmith & Fulham",
      "zone": 2,
      "postcode": "SW6 4HH",
      "lat": 51.4753,
      "lng": -0.2008,
      "website": "https://www.citybouldering.co.uk/locations/climbing-co-fulham",
      "logo": "assets/logos/city-bouldering.png",
      "prices_url": "https://www.citybouldering.co.uk/pricing",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": null,
        "concession_peak": 14.0,
        "concession_offpeak": null
      },
      "offpeak_window": null,
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Mon"
          ],
          "start": "10:00",
          "end": "16:00",
          "price": 7.5,
          "label": "Bargain",
          "prebook": true
        },
        {
          "days": [
            "Tue"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 9.5,
          "label": "Student Saver (concession)"
        }
      ],
      "student_note": "Concession £14 (peak price at all times). Bargain £7.50 Mondays; Student Saver £9.50 Tuesdays. Fulham-only student membership: 3 months £170, 6 months £320, 12 months £590.",
      "registration_fee": 3.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://www.citybouldering.co.uk/pricing",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, training area, showers/changing, café, retail shop",
      "membership": {
        "full_monthly": 59.99,
        "concession_monthly": null
      },
      "address": "5 Parsons Green Depot, 33-35 Parsons Green Lane, London SW6 4HH",
      "passes": "10 climbs £99",
      "hours": {
        "Mon": [
          "10:00",
          "22:00"
        ],
        "Tue": [
          "10:00",
          "22:00"
        ],
        "Wed": [
          "07:00",
          "22:00"
        ],
        "Thu": [
          "10:00",
          "22:00"
        ],
        "Fri": [
          "07:00",
          "21:00"
        ],
        "Sat": [
          "09:00",
          "19:00"
        ],
        "Sun": [
          "09:00",
          "19:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://www.citybouldering.co.uk/climbing-events-for-adults",
      "classes_url": "https://www.citybouldering.co.uk/coaching",
      "class_info": "[Intro to Bouldering](https://www.citybouldering.co.uk/events/intro-to-bouldering) £36 (1hr, incl. 2 weeks free, all 4 sites); [Fundamentals V0-V1](https://www.citybouldering.co.uk/events/fundamentals) £85 (£50 members, all 4 sites); [Intermediate V2-V4](https://www.citybouldering.co.uk/events/technical-foundations) £85 (£50 members, not Fulham); [Advanced Tactics V4-V6](https://www.citybouldering.co.uk/events/elite-tactics) £90 (£60 members); 1hr [private coaching](https://www.citybouldering.co.uk/events/private-coaching) £50 member / £60",
      "socials": [
        "No regular socials listed — see [events page](https://www.citybouldering.co.uk/climbing-events-for-adults)"
      ]
    },
    {
      "id": "substation_brixton",
      "name": "Substation Brixton",
      "brand": "Independent",
      "type": "bouldering",
      "area": "Brixton Hill",
      "borough": "Lambeth",
      "zone": 2,
      "postcode": "SW2 5DZ",
      "lat": 51.4487,
      "lng": -0.1219,
      "website": "https://brixton.substation.co.uk/",
      "logo": "assets/logos/substation.png",
      "prices_url": "https://brixton.substation.co.uk/climbing/book-a-climb",
      "payg": {
        "adult_peak": 18.0,
        "adult_offpeak": 16.0,
        "concession_peak": 15.0,
        "concession_offpeak": 13.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "07:00",
        "end": "15:00",
        "concession_only": false,
        "except_bank_holidays": true
      },
      "offpeak_assumed": false,
      "magic_slots": [],
      "student_note": "Concession £15 peak / £13 off-peak (over-60s, NHS/emergency services, JSA, disabled, students; ID needed). Off-peak Mon-Fri 7am-3pm.",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://brixton.substation.co.uk/climbing/book-a-climb",
        "method": "regex",
        "needs_js": true
      },
      "facilities": "Bouldering, fitness gym, studio classes, sauna/cold plunge, café, showers/lockers, free parking",
      "membership": {
        "full_monthly": 82,
        "concession_monthly": 66
      },
      "address": "Unit 13, Ellerslie Square Industrial Estate, Brixton Hill, London SW2 5DZ",
      "passes": "10x climb pass from £130 (10 climbs for the price of 9)",
      "hours": {
        "Mon": [
          "07:00",
          "22:00"
        ],
        "Tue": [
          "07:00",
          "22:00"
        ],
        "Wed": [
          "07:00",
          "22:00"
        ],
        "Thu": [
          "07:00",
          "22:00"
        ],
        "Fri": [
          "07:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": false,
      "events_url": "https://brixton.substation.co.uk/events",
      "instagram": "substation.brixton",
      "classes_url": "https://brixton.substation.co.uk/climbing/adult-coaching",
      "class_info": "[Bouldering Basics Course](https://brixton.substation.co.uk/climbing/adult-coaching) £120 (4-week beginner group); [private coaching](https://brixton.substation.co.uk/climbing/adult-coaching) £60/session. Members get 20% off group coaching.",
      "socials": [
        "[Monday Board Social](https://brixton.substation.co.uk/events/monday-board-social) — Mon 6pm+",
        "[Thursday Climbing Social](https://brixton.substation.co.uk/events/everybody-social) — Thu 18:00-20:00 (coached)",
        "[Women's Social](https://brixton.substation.co.uk/events/weekly-womens-social) — Fri 18:30+ (free, no booking)"
      ]
    },
    {
      "id": "gantry",
      "name": "Gantry Climbing",
      "brand": "Independent",
      "type": "bouldering",
      "area": "Canada Water / Surrey Quays",
      "borough": "Southwark",
      "zone": 2,
      "postcode": "SE16 7LL",
      "lat": 51.4954,
      "lng": -0.0469,
      "website": "https://gantryclimbing.com/",
      "logo": "assets/logos/gantry.png",
      "prices_url": "https://gantryclimbing.com/",
      "payg": {
        "adult_peak": 14.0,
        "adult_offpeak": null,
        "concession_peak": 12.0,
        "concession_offpeak": null
      },
      "offpeak_window": null,
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Wed"
          ],
          "start": "17:00",
          "end": "22:00",
          "price": 6.0,
          "label": "Student Night (concession)"
        },
        {
          "days": [
            "Sun"
          ],
          "start": "15:00",
          "end": "19:00",
          "price": 8.0,
          "label": "Sunday Slayer"
        }
      ],
      "student_note": "Concession £12 (students, teachers, NHS & Blue Light, Forces, carers, Blue Badge, PIP/DLA/Attendance Allowance; bring proof); under-18 £9. Student Night £6 Wednesdays from 5pm (student ID).",
      "registration_fee": null,
      "verified": true,
      "last_checked": "2026-10-25",
      "scrape": {
        "url": "https://gantryclimbing.com/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering (120+ problems, V0-V8), training board, circuit wall, gym area, kids wall",
      "membership": {
        "full_monthly": 65,
        "concession_monthly": 55
      },
      "address": "24-26 Surrey Quays Shopping Centre, London SE16 7LL",
      "passes": "10 visits £115 (£100 conc, £75 under-18; valid 12 months); 12 months prepaid £650 (£550 conc)",
      "hours": {
        "Mon": [
          "10:00",
          "22:00"
        ],
        "Tue": [
          "10:00",
          "22:00"
        ],
        "Wed": [
          "10:00",
          "22:00"
        ],
        "Thu": [
          "10:00",
          "22:00"
        ],
        "Fri": [
          "10:00",
          "22:00"
        ],
        "Sat": [
          "09:00",
          "19:00"
        ],
        "Sun": [
          "09:00",
          "19:00"
        ]
      },
      "hours_assumed": false
    },
    {
      "id": "lcc_vauxwall_west",
      "name": "VauxWall West (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Vauxhall",
      "borough": "Lambeth",
      "zone": 1,
      "postcode": "SW8 1SR",
      "lat": 51.4851,
      "lng": -0.1229,
      "website": "https://londonclimbingcentres.co.uk/centre/vauxwest/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sun"
          ],
          "start": "08:00",
          "end": "10:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "Arches 45b-47c, South Lambeth Road, Vauxhall, London SW8 1SR",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "londonclimbingcentres",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Women+ Social"
      ],
      "ac": "full",
      "ac_note": "fully air conditioned"
    },
    {
      "id": "lcc_vauxwall_east",
      "name": "VauxWall East (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Vauxhall",
      "borough": "Lambeth",
      "zone": 1,
      "postcode": "SE11 6BD",
      "lat": 51.4924,
      "lng": -0.1145,
      "website": "https://londonclimbingcentres.co.uk/centre/vauxeast/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Mon"
          ],
          "start": "09:00",
          "end": "16:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "1 Cabanel Place, Lollard Street, London SE11 6BD",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "londonclimbingcentres",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Boulderama — coached bouldering (free)"
      ],
      "ac": "full",
      "ac_note": "air-conditioned (~20°C regulated)"
    },
    {
      "id": "lcc_harrowall",
      "name": "HarroWall (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Harrow",
      "borough": "Harrow",
      "zone": 5,
      "postcode": "HA1 4HX",
      "lat": 51.5807,
      "lng": -0.3444,
      "website": "https://londonclimbingcentres.co.uk/centre/harrowall/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Fri"
          ],
          "start": "20:00",
          "end": "23:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "Unit 2a & 3a, Neptune Trading Estate, Neptune Road, Harrow HA1 4HX",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "londonclimbingcentres",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Women+ Social"
      ]
    },
    {
      "id": "lcc_croywall",
      "name": "CroyWall (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Croydon",
      "borough": "Croydon",
      "zone": 5,
      "postcode": "CR0 4WN",
      "lat": 51.374,
      "lng": -0.1157,
      "website": "https://londonclimbingcentres.co.uk/centre/croywall/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 15.5,
        "adult_offpeak": 11.5,
        "concession_peak": 13.5,
        "concession_offpeak": 10.5
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Tue"
          ],
          "start": "06:00",
          "end": "16:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        },
        {
          "days": [
            "Fri"
          ],
          "start": "00:00",
          "end": "23:59",
          "price": 9.5,
          "label": "Student Friday (concession)"
        }
      ],
      "student_note": "Concession £13.50 peak / £10.50 off-peak. Student Fridays £9.50. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, auto-belay",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "Unit 7 New South Quarter, Whitestone Way, Croydon CR0 4WN",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "croywall",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Women+ Social"
      ]
    },
    {
      "id": "lcc_ravenswall",
      "name": "RavensWall (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Ravenscourt Park",
      "borough": "Hammersmith & Fulham",
      "zone": 2,
      "postcode": "W6 0UQ",
      "lat": 51.4942,
      "lng": -0.2363,
      "website": "https://londonclimbingcentres.co.uk/centre/ravenswall/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Fri"
          ],
          "start": "06:00",
          "end": "16:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "Arch 105, Ravenscourt Road, London W6 0UQ",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "londonclimbingcentres",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Boulderama — coached bouldering (free, pay entry)",
        "Improvers Social — Tue 19:00-20:30 (free)",
        "Women+ Social",
        "Board Social (coached)"
      ]
    },
    {
      "id": "lcc_canarywall",
      "name": "CanaryWall (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Canary Wharf",
      "borough": "Tower Hamlets",
      "zone": 2,
      "postcode": "E14 8AA",
      "lat": 51.5098,
      "lng": -0.0275,
      "website": "https://londonclimbingcentres.co.uk/centre/canarywall/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sun"
          ],
          "start": "09:00",
          "end": "11:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "21-23 Trinidad Street, Poplar, London E14 8AA",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "canarywall",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Boulderama — coached bouldering (free)",
        "Women+ Social",
        "Board Social (coached)"
      ]
    },
    {
      "id": "lcc_bethwall",
      "name": "BethWall Green (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Bethnal Green",
      "borough": "Tower Hamlets",
      "zone": 2,
      "postcode": "E2 9QX",
      "lat": 51.5276,
      "lng": -0.0565,
      "website": "https://londonclimbingcentres.co.uk/centre/bethwall/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sat"
          ],
          "start": "08:00",
          "end": "10:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "Arches 243-247, Hollybush Place, London E2 9QX",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "londonclimbingcentres",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Boulderama — coached bouldering (free)",
        "Women+ Social",
        "Board Social (Women+)"
      ],
      "ac": "full",
      "ac_note": "fully air conditioned"
    },
    {
      "id": "lcc_eustonwall",
      "name": "EustonWall (LCC)",
      "brand": "London Climbing Centres",
      "type": "bouldering",
      "area": "Euston",
      "borough": "Camden",
      "zone": 1,
      "postcode": "NW1 3AX",
      "lat": 51.5255,
      "lng": -0.14,
      "website": "https://londonclimbingcentres.co.uk/centre/eustonwall/",
      "logo": "assets/logos/lcc.png",
      "prices_url": "https://londonclimbingcentres.co.uk/prices/",
      "payg": {
        "adult_peak": 17.0,
        "adult_offpeak": 13.0,
        "concession_peak": 15.0,
        "concession_offpeak": 11.0
      },
      "offpeak_window": {
        "days": [
          "Mon",
          "Tue",
          "Wed",
          "Thu",
          "Fri"
        ],
        "start": "06:00",
        "end": "16:00",
        "concession_only": false
      },
      "offpeak_assumed": false,
      "magic_slots": [
        {
          "days": [
            "Sat"
          ],
          "start": "17:00",
          "end": "21:00",
          "price": 8.0,
          "label": "Magic Time",
          "except_bank_holidays": true
        }
      ],
      "student_note": "Concession £15 peak / £11 off-peak. 3-month student membership £205.",
      "registration_fee": 2.0,
      "verified": true,
      "last_checked": "2026-07-01",
      "scrape": {
        "url": "https://londonclimbingcentres.co.uk/prices/",
        "method": "regex",
        "needs_js": false
      },
      "facilities": "Bouldering, Kilter board",
      "membership": {
        "full_monthly": 85,
        "concession_monthly": 75
      },
      "address": "350 Euston Road, London NW1 3AX",
      "passes": "10 punches £139 (£119 conc)",
      "hours": {
        "Mon": [
          "06:00",
          "22:00"
        ],
        "Tue": [
          "06:00",
          "22:00"
        ],
        "Wed": [
          "06:00",
          "22:00"
        ],
        "Thu": [
          "06:00",
          "22:00"
        ],
        "Fri": [
          "06:00",
          "22:00"
        ],
        "Sat": [
          "08:00",
          "20:00"
        ],
        "Sun": [
          "08:00",
          "20:00"
        ]
      },
      "hours_assumed": true,
      "events_url": "https://londonclimbingcentres.co.uk/london-climbing-events/",
      "instagram": "eustonwall",
      "classes_url": "https://londonclimbingcentres.co.uk/classes-courses/",
      "class_info": "[Bouldering Basics](https://londonclimbingcentres.co.uk/classes-courses/) £35 -> £17.50 with code NEW50 (1hr, incl. shoes + registration); [Foundation Course](https://londonclimbingcentres.co.uk/classes-courses/) £80 (3x1hr); [Route Setting Course](https://londonclimbingcentres.co.uk/route-setting-courses/) £150 (full day, 8am-5pm).",
      "socials": [
        "Boulderama — coached bouldering (free)",
        "Women+ Social",
        "Board Social (Women+)"
      ],
      "ac": "full",
      "ac_note": "fully air conditioned"
    }
  ],
  "limited_promotions": [
    {
      "id": "cb-promos-adult-concession-sale-stu",
      "brand": "City Bouldering",
      "title": "Student Sale: 9 months for the price of 6",
      "details": "9 months of unlimited climbing for the price of 6, listed at £400. Student sale; page lists concession eligibility (valid ID) at top.",
      "price": "£400",
      "valid": "Student sale",
      "expires": null,
      "expires_source": null,
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-09-21",
      "status": "active"
    },
    {
      "id": "cb-10-off-student-sale-10-off-10-cl",
      "brand": "City Bouldering",
      "title": "Student Sale: 10% off 10 Climb Pass",
      "details": "10% off the 10 climb pass. Listed at £117 against a standard £130 (10% off £130 = £117). Student sale.",
      "price": "£117 (standard £130)",
      "valid": "Student sale",
      "expires": null,
      "expires_source": null,
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-09-21",
      "status": "active"
    },
    {
      "id": "cb-10-off-student-sale-10-off-20-cl",
      "brand": "City Bouldering",
      "title": "Student Sale: 10% off 20 Climb Pass",
      "details": "10% off the 20 climb pass. Listed at £221.40 against a standard £246 (10% off £246 = £221.40). Student sale.",
      "price": "£221.40 (standard £246)",
      "valid": "Student sale",
      "expires": null,
      "expires_source": null,
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-09-21",
      "status": "active"
    },
    {
      "id": "cb-two-week-taster",
      "brand": "City Bouldering",
      "title": "First-timer: 2 weeks unlimited",
      "details": "2 weeks unlimited entry to the climbing facilities and gym for new customers at Aldgate, White City, Stratford or Climbing Co Fulham",
      "price": "£36",
      "valid": "Ongoing intro offer",
      "expires": null,
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-08-05",
      "status": "active"
    },
    {
      "id": "cb-summer-10pass",
      "brand": "City Bouldering",
      "title": "10% off Summer Sale - 10 Climb Pass",
      "details": "10-climb pass, 10% off. Valid at Aldgate, White City and Stratford (not Fulham). Was £148 / £130 concession",
      "price": "£133.20 (£117 concession)",
      "valid": "Summer 2026 sale",
      "expires": "2026-08-31",
      "expires_source": "In-gym poster reported by user 2026-08-05; not stated on the website",
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-08-04",
      "status": "expired"
    },
    {
      "id": "cb-summer-20pass",
      "brand": "City Bouldering",
      "title": "10% off Summer Sale - 20 Climb Pass",
      "details": "20-climb pass, 10% off. Valid at Aldgate, White City and Stratford (not Fulham). Was £280 / £246 concession",
      "price": "£252 (£221.40 concession)",
      "valid": "Summer 2026 sale",
      "expires": "2026-08-31",
      "expires_source": "In-gym poster reported by user 2026-08-05; not stated on the website",
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-08-04",
      "status": "expired"
    },
    {
      "id": "cb-summer-6mo",
      "brand": "City Bouldering",
      "title": "10% off Summer Sale - 6 Month Membership",
      "details": "6-month membership, 10% off. Memberships are valid at all City Bouldering centres. Was £465 / £400 concession",
      "price": "£418.50 (£360 concession)",
      "valid": "Summer 2026 sale",
      "expires": "2026-08-31",
      "expires_source": "In-gym poster reported by user 2026-08-05; not stated on the website",
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-08-04",
      "status": "expired"
    },
    {
      "id": "cb-summer-12mo",
      "brand": "City Bouldering",
      "title": "10% off Summer Sale - 12 Month Membership",
      "details": "12-month membership, 10% off. Memberships are valid at all City Bouldering centres. Was £875 / £765 concession",
      "price": "£787.50 (£688.50 concession)",
      "valid": "Summer 2026 sale",
      "expires": "2026-08-31",
      "expires_source": "In-gym poster reported by user 2026-08-05; not stated on the website",
      "source": "https://www.citybouldering.co.uk/pricing",
      "first_seen": "2026-08-04",
      "status": "expired"
    },
    {
      "id": "cd-summer-10",
      "brand": "Climbing District",
      "title": "Summer Pass (10 entries)",
      "details": "10-entry pass, use anytime, valid 3 months from purchase No longer listed on the website (checked 2026-10-25).",
      "price": "£99 (£89 concession)",
      "valid": "Summer 2026 seasonal offer",
      "expires": null,
      "source": "https://climbingdistrict.uk/pricing/",
      "first_seen": "2026-07-01",
      "status": "expired"
    },
    {
      "id": "cd-summer-5",
      "brand": "Climbing District",
      "title": "Summer Pass (5 entries)",
      "details": "5-entry pass, valid 2 months from purchase No longer listed on the website (checked 2026-10-25).",
      "price": "£49 (£40 concession)",
      "valid": "Summer 2026 seasonal offer",
      "expires": null,
      "source": "https://climbingdistrict.uk/pricing/",
      "first_seen": "2026-07-01",
      "status": "expired"
    },
    {
      "id": "lcc-first-month",
      "brand": "London Climbing Centres",
      "title": "New-climber discount card",
      "details": "Claim within 3 months of your first visit: £5 off visit two, one month unlimited for £50 incl. shoes, or £20 off La Sportiva Tarantulas (RRP £85). Not valid on the first visit or Magic Time entry",
      "price": "£5 off / £50 month / £20 off shoes",
      "valid": "Ongoing intro offer",
      "expires": null,
      "source": "https://londonclimbingcentres.co.uk/prices/",
      "first_seen": "2026-07-01",
      "status": "active"
    },
    {
      "id": "westway-annual-25",
      "brand": "Independent",
      "title": "Westway: 25% off annual membership",
      "details": "Everyone Active promo on annual climbing memberships (verify current status)",
      "price": "25% off annual",
      "valid": "Check current status",
      "expires": "2024-01-31",
      "expires_source": "https://www.everyoneactive.com/promotion/25-off-annual-climbing-memberships/ (\"Limited-time offer ends January 31st 2024\")",
      "source": "https://www.everyoneactive.com/promotion/25-off-annual-climbing-memberships/",
      "first_seen": "2026-07-01",
      "status": "expired"
    },
    {
      "id": "sub-intro",
      "brand": "Independent",
      "title": "Substation: 2-week intro",
      "details": "2 weeks unlimited climbing for new customers The offer page now returns 404 and the site no longer mentions it (checked 2026-10-25).",
      "price": "£30",
      "valid": "Ongoing intro offer",
      "expires": null,
      "source": "https://brixton.substation.co.uk/climbing/climbing-intro-offer",
      "first_seen": "2026-07-01",
      "status": "expired"
    },
    {
      "id": "gantry-founding-30",
      "brand": "Independent",
      "title": "Gantry: founding member 30% off for life",
      "details": "First 50 members on a rolling monthly membership get 30% off for as long as it keeps renewing (pausing allowed). First come, first served; conditions apply.",
      "price": "£46/mo (£39 concession, £28 under-18; standard £65 / £55 / £40)",
      "valid": "First 50 members",
      "expires": null,
      "expires_source": null,
      "source": "https://gantryclimbing.com/",
      "first_seen": "2026-10-25",
      "status": "active"
    },
    {
      "id": "cd-new-member-10",
      "brand": "Climbing District",
      "title": "New members: £10 first visit",
      "details": "Homepage banner: 'NEW MEMBER ONLY £10 for your first visit!'. The banner does not say which centres or other conditions apply.",
      "price": "£10",
      "valid": "New customers, first visit",
      "expires": null,
      "expires_source": null,
      "source": "https://climbingdistrict.uk/",
      "first_seen": "2026-10-25",
      "status": "active"
    },
    {
      "id": "sub-oct-50-first-month",
      "brand": "Independent",
      "title": "Substation: 50% off first month's membership",
      "details": "Climbing membership £41 for the first month (normally £82); concession £33 (normally £66); under-18 £27.50 (normally £55). Studio £41 and Combo £52 first month too.",
      "price": "£41 first month (£33 concession)",
      "valid": "October 2026",
      "expires": "2026-10-31",
      "expires_source": "brixton.substation.co.uk site banner, seen 2026-10-25: \"Get 50% off your first month's membership in October\"",
      "source": "https://brixton.substation.co.uk/membership",
      "first_seen": "2026-10-25",
      "status": "active"
    },
    {
      "id": "font-student-prepaid-oct",
      "brand": "The Font",
      "title": "Student prepaid memberships (3 or 9 months)",
      "details": "Student 3-month and 9-month prepaid memberships 'on sale for a limited time only'. Needs student ID shown at reception (or set up in-centre). Prices are not shown on the page.",
      "price": "Not listed on the page",
      "valid": "Limited time",
      "expires": "2026-10-31",
      "expires_source": "https://www.the-font.co.uk/student-deals (\"Lock yours in before October 31st!\", seen 2026-10-25)",
      "source": "https://www.the-font.co.uk/student-deals",
      "first_seen": "2026-10-25",
      "status": "active"
    },
    {
      "id": "rise-student-8for7",
      "brand": "Independent",
      "title": "Rise: student 8 months for the price of 7",
      "details": "8-month student membership £385, unlimited climbing for the academic year. Rise's website says the gym is temporarily closed (seen 2026-10-25), so check before buying.",
      "price": "£385",
      "valid": "Students, Sep-Nov 2026",
      "expires": "2026-11-30",
      "expires_source": "https://www.rise-climbing.com/student-offers (\"Offer valid until end of November 2026\", seen 2026-10-25)",
      "source": "https://www.rise-climbing.com/student-offers",
      "first_seen": "2026-10-25",
      "status": "unverified"
    },
    {
      "id": "rise-student25",
      "brand": "Independent",
      "title": "Rise: 25% off first month of student membership",
      "details": "Code STUDENT25 takes 25% off the first month of the £55.50 student monthly membership. Rise's website says the gym is temporarily closed (seen 2026-10-25), so check before buying.",
      "price": "£41.63 first month (normally £55.50)",
      "valid": "Students, code STUDENT25",
      "expires": "2026-11-30",
      "expires_source": "https://www.rise-climbing.com/student-offers (\"Offer valid until end of November 2026\", seen 2026-10-25)",
      "source": "https://www.rise-climbing.com/student-offers",
      "first_seen": "2026-10-25",
      "status": "unverified"
    }
  ]
};
