-- Purpose: update active The Climb branch business hours from the official branch page.
-- Source: http://theclimb.co.kr/portfolio-item/branch/
-- Note: the official page writes midnight closing as 24:00. Store it as 00:00
-- because GymOperatingStatusResolver parses java.time.LocalTime.
-- Public holiday hours match weekend hours on the source page, but the current
-- business_hours shape stores only mon-sun keys.

BEGIN;

WITH source_hours (name, business_hours) AS (
    VALUES
        (
            '더클라임 일산점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 마곡점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 양재점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 신림점',
            '{"mon":{"open":"07:00","close":"00:00"},"tue":{"open":"07:00","close":"00:00"},"wed":{"open":"07:00","close":"00:00"},"thu":{"open":"07:00","close":"00:00"},"fri":{"open":"07:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 연남점',
            '{"mon":{"open":"07:00","close":"00:00"},"tue":{"open":"07:00","close":"00:00"},"wed":{"open":"07:00","close":"00:00"},"thu":{"open":"07:00","close":"00:00"},"fri":{"open":"07:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 강남점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 사당점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 신사점',
            '{"mon":{"open":"12:00","close":"23:00"},"tue":{"open":"12:00","close":"23:00"},"wed":{"open":"12:00","close":"23:00"},"thu":{"open":"12:00","close":"23:00"},"fri":{"open":"12:00","close":"23:00"},"sat":{"open":"10:00","close":"22:00"},"sun":{"open":"10:00","close":"20:00"}}'::jsonb
        ),
        (
            '더클라임 논현점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 문래점',
            '{"mon":{"open":"07:00","close":"00:00"},"tue":{"open":"07:00","close":"00:00"},"wed":{"open":"07:00","close":"00:00"},"thu":{"open":"07:00","close":"00:00"},"fri":{"open":"07:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 이수점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        ),
        (
            '더클라임 성수점',
            '{"mon":{"open":"08:00","close":"00:00"},"tue":{"open":"08:00","close":"00:00"},"wed":{"open":"08:00","close":"00:00"},"thu":{"open":"08:00","close":"00:00"},"fri":{"open":"08:00","close":"00:00"},"sat":{"open":"08:00","close":"22:00"},"sun":{"open":"08:00","close":"22:00"}}'::jsonb
        )
),
updated AS (
    UPDATE gyms g
       SET business_hours = source.business_hours,
           updated_at = NOW()
      FROM source_hours source
     WHERE g.deleted_at IS NULL
       AND g.status = 'active'
       AND g.name = source.name
     RETURNING g.id, g.name
)
SELECT
    (SELECT COUNT(*) FROM source_hours) AS source_rows,
    (SELECT COUNT(*) FROM updated) AS updated_rows;

COMMIT;
