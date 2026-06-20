-- Purpose: add two Suwon gyms missing from the production seed.
-- Sources:
-- - Kakao place 1025021964: http://place.map.kakao.com/1025021964
-- - Kakao place 1929275619: http://place.map.kakao.com/1929275619
-- - Climb Together grade source: https://m.blog.naver.com/dalkeeee/224145251627
-- - Kindy grade source: https://m.blog.naver.com/ccc00oo/223731373328

BEGIN;

WITH gym_seed (
    slug,
    name,
    address,
    lat,
    lng,
    phone,
    website,
    business_hours,
    region_code,
    status,
    description
) AS (
    VALUES
        (
            'climb-together-suwon',
            '클라임투게더 수원영통센터',
            '경기 수원시 영통구 덕영대로 1566 판타지움 3층',
            37.24513308021782,
            127.06179535476463,
            NULL,
            'http://place.map.kakao.com/1025021964',
            '{"mon":{"open":"10:00","close":"23:00"},"tue":{"open":"10:00","close":"23:00"},"wed":{"open":"10:00","close":"23:00"},"thu":{"open":"10:00","close":"23:00"},"fri":{"open":"10:00","close":"23:00"},"sat":{"open":"09:00","close":"21:00"},"sun":{"open":"09:00","close":"21:00"}}'::jsonb,
            'gyeonggi',
            'active',
            'Kakao place 1025021964; Instagram https://www.instagram.com/climb_together_suwon/; grade source https://m.blog.naver.com/dalkeeee/224145251627'
        ),
        (
            'kindy-climbing',
            '킨디클라이밍',
            '경기 수원시 권선구 세화로 124 1층',
            37.263613385470784,
            126.9989222515109,
            NULL,
            'http://place.map.kakao.com/1929275619',
            '{"mon":{"open":"10:00","close":"23:00"},"tue":{"open":"10:00","close":"23:00"},"wed":{"open":"10:00","close":"23:00"},"thu":{"open":"10:00","close":"23:00"},"fri":{"open":"10:00","close":"23:00"},"sat":{"open":"10:00","close":"20:00"},"sun":{"open":"10:00","close":"20:00"}}'::jsonb,
            'gyeonggi',
            'active',
            'Kakao place 1929275619; Instagram https://www.instagram.com/kind_climbing; grade source https://m.blog.naver.com/ccc00oo/223731373328'
        )
),
to_insert AS (
    SELECT *
    FROM gym_seed seed
    WHERE NOT EXISTS (
        SELECT 1
        FROM gyms g
        WHERE g.deleted_at IS NULL
          AND (g.website = seed.website OR g.name = seed.name)
    )
),
inserted_raw AS (
    INSERT INTO gyms (
        name,
        address,
        lat,
        lng,
        phone,
        website,
        business_hours,
        region_code,
        status,
        description
    )
    SELECT
        name,
        address,
        lat,
        lng,
        phone,
        website,
        business_hours,
        region_code,
        status,
        description
    FROM to_insert
    RETURNING id, name, website
),
inserted_gyms AS (
    SELECT seed.slug, inserted_raw.id
    FROM inserted_raw
    JOIN gym_seed seed ON seed.website = inserted_raw.website
),
existing_gyms AS (
    SELECT DISTINCT ON (seed.slug)
        seed.slug,
        g.id
    FROM gym_seed seed
    JOIN gyms g
      ON g.deleted_at IS NULL
     AND (g.website = seed.website OR g.name = seed.name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM inserted_gyms inserted
        WHERE inserted.slug = seed.slug
    )
    ORDER BY seed.slug, g.id
),
target_gyms AS (
    SELECT slug, id FROM inserted_gyms
    UNION ALL
    SELECT slug, id FROM existing_gyms
),
grade_seed (slug, label, difficulty_order) AS (
    VALUES
        ('climb-together-suwon', '빨강', 10),
        ('climb-together-suwon', '주황', 20),
        ('climb-together-suwon', '노랑', 30),
        ('climb-together-suwon', '초록', 40),
        ('climb-together-suwon', '파랑', 50),
        ('climb-together-suwon', '남색', 60),
        ('climb-together-suwon', '보라', 70),
        ('climb-together-suwon', '검정', 80),
        ('climb-together-suwon', '흰색', 90),
        ('kindy-climbing', '빨강', 10),
        ('kindy-climbing', '주황', 20),
        ('kindy-climbing', '노랑', 30),
        ('kindy-climbing', '초록', 40),
        ('kindy-climbing', '파랑', 50),
        ('kindy-climbing', '남색', 60),
        ('kindy-climbing', '보라', 70),
        ('kindy-climbing', '갈색', 80),
        ('kindy-climbing', '검정', 90)
),
upserted_grades AS (
    INSERT INTO gym_grades (gym_id, label, difficulty_order, is_active)
    SELECT
        target.id,
        grade.label,
        grade.difficulty_order,
        TRUE
    FROM target_gyms target
    JOIN grade_seed grade ON grade.slug = target.slug
    ON CONFLICT (gym_id, label) DO UPDATE
       SET difficulty_order = EXCLUDED.difficulty_order,
           is_active = TRUE,
           updated_at = NOW()
    RETURNING gym_id, label
)
SELECT
    (SELECT COUNT(*) FROM inserted_gyms) AS inserted_gyms,
    (SELECT COUNT(*) FROM existing_gyms) AS existing_gyms,
    (SELECT COUNT(*) FROM upserted_grades) AS upserted_grades;

COMMIT;
