-- Timelines schema.
--
-- Mirrors the Scala model in common/shared/src/main/scala/com/crianonim/timelines/Timeline.scala:
--
--   Period     = Point(TimePoint) | Closed(TimePoint, TimePoint) | Started(TimePoint)
--   TimePoint  = YearOnly(year) | YearMonth(year, month) | YearMonthDay(year, month, day)
--
-- Two design notes worth reading before editing this file:
--
-- 1. Precision is an explicit column, not something inferred from NULLs. TimePoint is a sealed
--    trait, so YearOnly(2010) and YearMonth(2010, 1) are different variants that would otherwise
--    occupy identical rows. The *_precision columns keep them distinguishable, and the
--    time_point_shape constraints enforce that the month/day NULLs agree with the declared
--    precision. This mirrors TimePoint.timePointFloorDate / timePointCeilDate on the Scala side,
--    where a YearOnly spans a whole year and a YearMonthDay spans a single day.
--
-- 2. Everything is IF NOT EXISTS / DO-block guarded, so re-running this file is a no-op rather
--    than an error. `jobs` in db.sql is left alone and stays in `public`; nothing reads it.
--
-- Applying this file:
--   - local Docker (db/docker-compose.yml): automatic, but only on first init of an empty volume.
--     `docker compose down -v && docker compose up -d` to re-apply.
--   - hosted Postgres: manual, e.g.
--     set -a && . ./.env.local && set +a
--     psql -v ON_ERROR_STOP=1 -f db/sql/timelines.sql

BEGIN;

-- ---------------------------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------------------------

-- Created first: the enum types below live inside it.
CREATE SCHEMA IF NOT EXISTS timelines;

-- ---------------------------------------------------------------------------------------------
-- Types
-- ---------------------------------------------------------------------------------------------

-- 'point'   = a single moment          (TimePoint only)
-- 'closed'  = a bounded span           (start + end)
-- 'started' = open-ended, no end yet   (start only)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                 WHERE t.typname = 'period_kind' AND n.nspname = 'timelines') THEN
    CREATE TYPE timelines.period_kind AS ENUM ('point', 'closed', 'started');
  END IF;
END
$$;

-- Which of the three TimePoint variants a start/end value represents.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                 WHERE t.typname = 'precision' AND n.nspname = 'timelines') THEN
    CREATE TYPE timelines.precision AS ENUM ('year', 'month', 'day');
  END IF;
END
$$;

-- ---------------------------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS timelines.timeline (
  id               text                 NOT NULL,
  name             text                 NOT NULL,
  period_kind      timelines.period_kind NOT NULL,

  -- The period's start, as a TimePoint triple plus its precision.
  start_year       integer              NOT NULL,
  start_month      integer,
  start_day        integer,
  start_precision  timelines.precision  NOT NULL,

  -- The period's end. Populated for 'closed'; NULL for 'point' and 'started'.
  end_year         integer,
  end_month        integer,
  end_day          integer,
  end_precision    timelines.precision,

  CONSTRAINT timeline_pkey PRIMARY KEY (id),

  -- A period's end cannot precede its start. Unbounded (point/started) periods have no end
  -- components at all, so the comparison skips them.
  CONSTRAINT period_not_backwards CHECK (
    period_kind <> 'closed' OR (end_year, end_month, end_day) >= (start_year, start_month, start_day)
  ),

  -- Component ranges. Months/days are NULL when precision says they are not part of the value.
  CONSTRAINT month_in_range CHECK (
    (start_month IS NULL OR start_month BETWEEN 1 AND 12) AND
    (end_month   IS NULL OR end_month   BETWEEN 1 AND 12)
  ),
  CONSTRAINT day_in_range CHECK (
    (start_day IS NULL OR start_day BETWEEN 1 AND 31) AND
    (end_day   IS NULL OR end_day   BETWEEN 1 AND 31)
  ),

  -- 'month' precision must actually carry a month, and must not carry a day.
  CONSTRAINT month_precision_is_explicit CHECK (
    (start_precision <> 'month' OR start_month IS NOT NULL) AND
    (start_precision <> 'day'   OR (start_month IS NOT NULL AND start_day IS NOT NULL)) AND
    (end_precision IS NULL OR end_precision <> 'month' OR end_month IS NOT NULL) AND
    (end_precision IS NULL OR end_precision <> 'day'
      OR (end_month IS NOT NULL AND end_day IS NOT NULL))
  ),

  -- The mirror image: 'year' precision must not carry a month or a day.
  CONSTRAINT year_precision_has_no_month_or_day CHECK (
    (start_precision <> 'year' OR (start_month IS NULL AND start_day IS NULL)) AND
    (end_precision IS NULL OR end_precision <> 'year'
      OR (end_month IS NULL AND end_day IS NULL))
  ),

  -- Only 'closed' has an end. 'point' is one moment, 'started' is explicitly open-ended.
  CONSTRAINT only_closed_has_an_end CHECK (
    period_kind = 'closed' OR
    (end_year IS NULL AND end_month IS NULL AND end_day IS NULL AND end_precision IS NULL)
  ),

  -- ...and conversely, 'closed' must have one.
  CONSTRAINT closed_must_have_an_end CHECK (
    period_kind <> 'closed' OR
    (end_year IS NOT NULL AND end_precision IS NOT NULL)
  )
);

-- ---------------------------------------------------------------------------------------------
-- Seed data
--
-- From db/timelines.json, which matches TimelinesFromJSON (the data the Timelines tab renders).
-- Note the two other seed sources disagree with this one on id 002 (2023-08-07 here vs
-- 2024-07-08 in Timeline.examples) and id 003 (1999-10 vs 1999-09). The id 005 gap is present
-- upstream and preserved here.
-- ---------------------------------------------------------------------------------------------

INSERT INTO timelines.timeline
  (id, name, period_kind,
   start_year, start_month, start_day, start_precision,
   end_year, end_month, end_day, end_precision)
VALUES
  -- Started: open-ended, born 1980-06-06, no end.
  ('001', 'Jan''s life',   'started',
   1980, 6,  6,  'day',
   NULL, NULL, NULL, NULL),

  -- Point: a single moment, 2023-08-07.
  ('002', 'Met Alex',       'point',
   2023, 8,  7,  'day',
   NULL, NULL, NULL, NULL),

  -- Closed with month precision on both ends.
  ('003', 'Uni time',       'closed',
   1999, 10, NULL, 'month',
   2005, 6,  NULL, 'month'),

  -- Closed with year precision on both ends.
  ('004', 'Tory''s rule',   'closed',
   2010, NULL, NULL, 'year',
   2024, NULL, NULL, 'year'),

  -- Closed with day precision on both ends.
  ('006', 'Edward III life', 'closed',
   1312, 11, 13, 'day',
   1377, 6,  20, 'day')
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------------------------
-- Verification
-- ---------------------------------------------------------------------------------------------

SELECT id, name, period_kind, start_precision AS start_p, end_precision AS end_p
  FROM timelines.timeline
 ORDER BY id;
