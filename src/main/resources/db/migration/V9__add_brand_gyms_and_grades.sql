-- Purpose: add requested brand branches and upsert their grade scales.
-- Sources:
-- - Kakao Local API lookup on 2026-06-22 for address, phone, coordinates, and place URL.
-- - Existing production seed grade scales for The Climb, Climbing Park, Seoul Forest Climbing, Pickus, and Damjang.
-- Notes:
-- - The Climb and Climbing Park grades are normalized across all branches.
-- - The Climb adds pink between red and purple.
-- - Damjang Euljiro copies active grades from Shincheon Damjang when present, with a hard-coded fallback.

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
        ('theclimb-seongsu', '더클라임 성수점', '서울 성동구 아차산로17길 49', 37.546768103205, 127.065279873384, '02-499-5014', 'http://place.map.kakao.com/633255619', NULL::jsonb, 'seoul', 'active', 'Kakao place 633255619; brand grades normalized 2026-06-22'),
        ('theclimb-mullae', '더클라임 문래점', '서울 영등포구 당산로 63', 37.5205672032931, 126.895004816526, '02-3667-5014', 'http://place.map.kakao.com/1045438278', NULL::jsonb, 'seoul', 'active', 'Kakao place 1045438278; brand grades normalized 2026-06-22'),
        ('theclimb-gangnam', '더클라임 강남점', '서울 강남구 테헤란로8길 21', 37.497588868698, 127.031992024802, '02-566-8821', 'http://place.map.kakao.com/807578478', NULL::jsonb, 'seoul', 'active', 'Kakao place 807578478; brand grades normalized 2026-06-22'),
        ('theclimb-nonhyeon', '더클라임 논현점', '서울 서초구 강남대로 519', 37.50837434671929, 127.0222189375985, '02-545-5014', 'http://place.map.kakao.com/408430175', NULL::jsonb, 'seoul', 'active', 'Kakao place 408430175; brand grades normalized 2026-06-22'),
        ('theclimb-sinsa', '더클라임 신사점', '서울 강남구 압구정로2길 6', 37.5210862485391, 127.019137884972, '02-549-8821', 'http://place.map.kakao.com/330396498', NULL::jsonb, 'seoul', 'active', 'Kakao place 330396498; brand grades normalized 2026-06-22'),
        ('theclimb-yeonnam', '더클라임 연남점', '서울 마포구 양화로 186', 37.5576667697525, 126.925891892022, '02-2088-5071', 'http://place.map.kakao.com/78694546', NULL::jsonb, 'seoul', 'active', 'Kakao place 78694546; brand grades normalized 2026-06-22'),
        ('theclimb-sadang', '더클라임 사당점', '서울 관악구 과천대로 939', 37.4744790852635, 126.981479959615, '02-585-8821', 'http://place.map.kakao.com/367482090', NULL::jsonb, 'seoul', 'active', 'Kakao place 367482090; brand grades normalized 2026-06-22'),
        ('theclimb-isu', '더클라임 이수점', '서울 동작구 동작대로 59', 37.4820106242888, 126.98148714585, '02-588-5014', 'http://place.map.kakao.com/1115009396', NULL::jsonb, 'seoul', 'active', 'Kakao place 1115009396; brand grades normalized 2026-06-22'),
        ('theclimb-yangjae', '더클라임 양재점', '서울 강남구 남부순환로 2615', 37.48518538373791, 127.0358841642777, '02-576-8821', 'http://place.map.kakao.com/953399573', NULL::jsonb, 'seoul', 'active', 'Kakao place 953399573; brand grades normalized 2026-06-22'),
        ('theclimb-ilsan', '더클라임 일산점', '경기 고양시 일산동구 중앙로 1160', 37.65084597229486, 126.7788125256411, '031-905-5014', 'http://place.map.kakao.com/26842478', NULL::jsonb, 'gyeonggi', 'active', 'Kakao place 26842478; brand grades normalized 2026-06-22'),
        ('theclimb-magok', '더클라임 마곡점', '서울 강서구 마곡동로 62', 37.5605554774756, 126.833890972898, '02-2668-5014', 'http://place.map.kakao.com/504806909', NULL::jsonb, 'seoul', 'active', 'Kakao place 504806909; brand grades normalized 2026-06-22'),
        ('theclimb-sillim', '더클라임 신림점', '서울 관악구 신원로 35', 37.4823088095683, 126.929019083325, '02-877-8821', 'http://place.map.kakao.com/610038060', NULL::jsonb, 'seoul', 'active', 'Kakao place 610038060; brand grades normalized 2026-06-22'),
        ('allez-hyehwa', '알레클라이밍 혜화점', '서울 종로구 창경궁로34길 18-5', 37.5840909888299, 127.000706472254, '070-8886-2018', 'http://place.map.kakao.com/1455205552', NULL::jsonb, 'seoul', 'active', 'Kakao place 1455205552; default grade scale applied 2026-06-22'),
        ('allez-gangdong', '알레클라임 강동점', '서울 강동구 천호대로177길 39', 37.5365703598752, 127.137837516682, '02-477-2018', 'http://place.map.kakao.com/225895844', NULL::jsonb, 'seoul', 'active', 'Kakao place 225895844; default grade scale applied 2026-06-22'),
        ('allez-yeongdeungpo', '알레클라임 영등포점', '서울 영등포구 영등포로33길 14', 37.5215758359363, 126.902855210356, '070-8862-2018', 'http://place.map.kakao.com/956081122', NULL::jsonb, 'seoul', 'active', 'Kakao place 956081122; default grade scale applied 2026-06-22'),
        ('rocktree-bundang', '락트리클라이밍 분당', '경기 성남시 분당구 황새울로 224', 37.3788319443341, 127.11328565086, '070-4833-8889', 'http://place.map.kakao.com/77774339', NULL::jsonb, 'gyeonggi', 'active', 'Kakao place 77774339; default grade scale applied 2026-06-22'),
        ('stones-climbing', '스톤즈클라이밍', '서울 관악구 남부순환로 1990-3', 37.4750687274373, 126.970140694301, '010-3727-7398', 'http://place.map.kakao.com/2138916951', NULL::jsonb, 'seoul', 'active', 'Kakao place 2138916951; default grade scale applied 2026-06-22'),
        ('picnic-climbing', '피크닉클라이밍', '경기 수원시 팔달구 효원로 278', 37.2610157450168, 127.032444075122, NULL, 'http://place.map.kakao.com/1636304179', NULL::jsonb, 'gyeonggi', 'active', 'Kakao place 1636304179; default grade scale applied 2026-06-22'),
        ('climbing-park-seongsu', '클라이밍파크 성수점', '서울 성동구 연무장13길 7', 37.5423101113247, 127.058089608702, '02-462-4662', 'http://place.map.kakao.com/662485726', NULL::jsonb, 'seoul', 'active', 'Kakao place 662485726; brand grades normalized 2026-06-22'),
        ('climbing-park-jongno', '클라이밍파크 종로점', '서울 종로구 종로 199-1', 37.57110586168921, 126.99990944236127, '02-762-4662', 'http://place.map.kakao.com/1796322688', NULL::jsonb, 'seoul', 'active', 'Kakao place 1796322688; also requested as Euljiro branch; brand grades normalized 2026-06-22'),
        ('climbing-park-gangnam', '클라이밍파크 강남점', '서울 강남구 강남대로 364', 37.4955380007776, 127.029195740935, NULL, 'http://place.map.kakao.com/2029901090', NULL::jsonb, 'seoul', 'active', 'Kakao place 2029901090; brand grades normalized 2026-06-22'),
        ('climbing-park-sinnonhyeon', '클라이밍파크 신논현점', '서울 강남구 강남대로 468', 37.5041597595906, 127.025142348329, '02-555-4662', 'http://place.map.kakao.com/403186026', NULL::jsonb, 'seoul', 'active', 'Kakao place 403186026; brand grades normalized 2026-06-22'),
        ('climbing-park-hanti', '클라이밍파크 한티점', '서울 강남구 선릉로 324', 37.4985277739039, 127.052088055094, '02-556-4662', 'http://place.map.kakao.com/1719136770', NULL::jsonb, 'seoul', 'active', 'Kakao place 1719136770; brand grades normalized 2026-06-22'),
        ('off-the-wall-climbing', '오프더월클라이밍', '서울 용산구 이태원로 190', 37.5343297151136, 126.994933526337, '02-6958-7796', 'http://place.map.kakao.com/662516308', NULL::jsonb, 'seoul', 'active', 'Kakao place 662516308; default grade scale applied 2026-06-22'),
        ('seoulforest-jongno', '서울숲클라이밍 종로점', '서울 종로구 수표로 96', 37.5697052934573, 126.990024130939, NULL, 'http://place.map.kakao.com/1615143674', NULL::jsonb, 'seoul', 'active', 'Kakao place 1615143674; Seoul Forest grade scale applied 2026-06-22'),
        ('seoulforest-jamsil', '서울숲클라이밍 잠실점', '서울 송파구 백제고분로7길 49', 37.5110296497292, 127.084155573956, '010-3371-2703', 'http://place.map.kakao.com/931706576', NULL::jsonb, 'seoul', 'active', 'Kakao place 931706576; Seoul Forest grade scale applied 2026-06-22'),
        ('seoulforest-guro', '서울숲클라이밍 구로점', '서울 구로구 디지털로 300', 37.4849265878966, 126.896538150593, '010-3374-2704', 'http://place.map.kakao.com/496958523', NULL::jsonb, 'seoul', 'active', 'Kakao place 496958523; existing Seoul Forest Guro grade scale preserved 2026-06-22'),
        ('seoulforest-yeongdeungpo', '서울숲클라이밍 영등포점', '서울 영등포구 문래로 164', 37.517735386458845, 126.90024267705954, '010-6686-2700', 'http://place.map.kakao.com/850543972', NULL::jsonb, 'seoul', 'active', 'Kakao place 850543972; Seoul Forest grade scale applied 2026-06-22'),
        ('pickus-jongno', '피커스 종로점', '서울 종로구 돈화문로5가길 1', 37.57091093329, 126.991397046749, '02-526-8862', 'http://place.map.kakao.com/1330937676', NULL::jsonb, 'seoul', 'active', 'Kakao place 1330937676; Pickus grade scale applied 2026-06-22'),
        ('pickus-guro', '피커스클라이밍 구로', '서울 구로구 구로중앙로 152', 37.5011570773274, 126.882769510889, '02-526-8850', 'http://place.map.kakao.com/132573277', NULL::jsonb, 'seoul', 'active', 'Kakao place 132573277; Pickus grade scale applied 2026-06-22'),
        ('pickus-sinchon', '피커스 신촌점', '서울 서대문구 신촌로 129', 37.5565621681747, 126.940232261588, NULL, 'http://place.map.kakao.com/688426394', NULL::jsonb, 'seoul', 'active', 'Kakao place 688426394; Pickus grade scale applied 2026-06-22'),
        ('damjang-euljiro', '을지로 담장 클라이밍', '서울 중구 마른내로 63-3', 37.56449695400314, 126.99504806666535, '02-2272-2855', 'http://place.map.kakao.com/444613083', NULL::jsonb, 'seoul', 'active', 'Kakao place 444613083; grades copied from Shincheon Damjang 2026-06-22')
),
to_insert AS (
    SELECT *
    FROM gym_seed seed
    WHERE NOT EXISTS (
        SELECT 1
        FROM gyms g
        WHERE g.deleted_at IS NULL
          AND (
              (seed.website IS NOT NULL AND g.website = seed.website)
              OR g.name = seed.name
          )
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
    JOIN gym_seed seed
      ON (
          (seed.website IS NOT NULL AND seed.website = inserted_raw.website)
          OR (seed.website IS NULL AND seed.name = inserted_raw.name)
      )
),
existing_gyms AS (
    SELECT DISTINCT ON (seed.slug)
        seed.slug,
        g.id
    FROM gym_seed seed
    JOIN gyms g
      ON g.deleted_at IS NULL
     AND (
         (seed.website IS NOT NULL AND g.website = seed.website)
         OR g.name = seed.name
     )
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
grade_scheme (scheme, label, difficulty_order) AS (
    VALUES
        ('default', '흰색', 10),
        ('default', '노랑', 20),
        ('default', '주황', 30),
        ('default', '초록', 40),
        ('default', '파랑', 50),
        ('default', '빨강', 60),
        ('default', '보라', 70),
        ('default', '갈색', 80),
        ('default', '회색', 90),
        ('default', '검정', 100),
        ('theclimb', '흰색', 10),
        ('theclimb', '노랑', 20),
        ('theclimb', '주황', 30),
        ('theclimb', '초록', 40),
        ('theclimb', '파랑', 50),
        ('theclimb', '빨강', 60),
        ('theclimb', '핑크', 65),
        ('theclimb', '보라', 70),
        ('theclimb', '회색', 80),
        ('theclimb', '갈색', 90),
        ('theclimb', '검정', 100),
        ('climbing-park', '노랑', 10),
        ('climbing-park', '핑크', 20),
        ('climbing-park', '파랑', 30),
        ('climbing-park', '빨강', 40),
        ('climbing-park', '보라', 50),
        ('climbing-park', '갈색', 60),
        ('climbing-park', '회색', 70),
        ('climbing-park', '검정', 80),
        ('climbing-park', '흰색', 90),
        ('seoulforest-guro', '빨강', 10),
        ('seoulforest-guro', '주황', 20),
        ('seoulforest-guro', '노랑', 30),
        ('seoulforest-guro', '초록', 40),
        ('seoulforest-guro', '파랑', 50),
        ('seoulforest-guro', '보라', 60),
        ('seoulforest-guro', '검정', 70),
        ('seoulforest-guro', '갈색', 80),
        ('seoulforest-standard', '핑크', 10),
        ('seoulforest-standard', '빨강', 20),
        ('seoulforest-standard', '주황', 30),
        ('seoulforest-standard', '노랑', 40),
        ('seoulforest-standard', '초록', 50),
        ('seoulforest-standard', '파랑', 60),
        ('seoulforest-standard', '보라', 70),
        ('seoulforest-standard', '갈색', 80),
        ('seoulforest-standard', '검정', 90),
        ('pickus', '빨강', 10),
        ('pickus', '주황', 20),
        ('pickus', '노랑', 30),
        ('pickus', '초록', 40),
        ('pickus', '파랑', 50),
        ('pickus', '보라', 60),
        ('pickus', '회색', 70),
        ('pickus', '검정', 80),
        ('damjang-fallback', '빨강', 10),
        ('damjang-fallback', '주황', 20),
        ('damjang-fallback', '노랑', 30),
        ('damjang-fallback', '초록', 40),
        ('damjang-fallback', '보라', 50),
        ('damjang-fallback', '흰색', 60),
        ('damjang-fallback', '검정', 70)
),
gym_grade_scheme (slug, scheme) AS (
    VALUES
        ('allez-hyehwa', 'default'),
        ('allez-gangdong', 'default'),
        ('allez-yeongdeungpo', 'default'),
        ('rocktree-bundang', 'default'),
        ('stones-climbing', 'default'),
        ('picnic-climbing', 'default'),
        ('off-the-wall-climbing', 'default'),
        ('seoulforest-jongno', 'seoulforest-standard'),
        ('seoulforest-jamsil', 'seoulforest-standard'),
        ('seoulforest-guro', 'seoulforest-guro'),
        ('seoulforest-yeongdeungpo', 'seoulforest-standard'),
        ('pickus-jongno', 'pickus'),
        ('pickus-guro', 'pickus'),
        ('pickus-sinchon', 'pickus')
),
explicit_grade_seed AS (
    SELECT
        target.id AS gym_id,
        scheme.label,
        scheme.difficulty_order
    FROM target_gyms target
    JOIN gym_grade_scheme map ON map.slug = target.slug
    JOIN grade_scheme scheme ON scheme.scheme = map.scheme
),
brand_targets AS (
    SELECT DISTINCT
        target.id AS gym_id,
        'theclimb' AS scheme
    FROM target_gyms target
    WHERE target.slug LIKE 'theclimb-%'
    UNION
    SELECT DISTINCT
        g.id AS gym_id,
        'theclimb' AS scheme
    FROM gyms g
    WHERE g.deleted_at IS NULL
      AND g.name LIKE '더클라임%'
    UNION
    SELECT DISTINCT
        target.id AS gym_id,
        'climbing-park' AS scheme
    FROM target_gyms target
    WHERE target.slug LIKE 'climbing-park-%'
    UNION
    SELECT DISTINCT
        g.id AS gym_id,
        'climbing-park' AS scheme
    FROM gyms g
    WHERE g.deleted_at IS NULL
      AND g.name LIKE '클라이밍파크%'
),
brand_grade_seed AS (
    SELECT
        target.gym_id,
        scheme.label,
        scheme.difficulty_order
    FROM brand_targets target
    JOIN grade_scheme scheme ON scheme.scheme = target.scheme
),
damjang_source_grade_seed AS (
    SELECT
        target.id AS gym_id,
        grade.label,
        grade.difficulty_order
    FROM target_gyms target
    JOIN gyms source
      ON source.deleted_at IS NULL
     AND source.name = '신촌담장'
    JOIN gym_grades grade
      ON grade.gym_id = source.id
     AND grade.is_active = TRUE
    WHERE target.slug = 'damjang-euljiro'
),
damjang_fallback_grade_seed AS (
    SELECT
        target.id AS gym_id,
        scheme.label,
        scheme.difficulty_order
    FROM target_gyms target
    JOIN grade_scheme scheme ON scheme.scheme = 'damjang-fallback'
    WHERE target.slug = 'damjang-euljiro'
      AND NOT EXISTS (SELECT 1 FROM damjang_source_grade_seed)
),
all_grade_seed AS (
    SELECT gym_id, label, difficulty_order FROM explicit_grade_seed
    UNION
    SELECT gym_id, label, difficulty_order FROM brand_grade_seed
    UNION
    SELECT gym_id, label, difficulty_order FROM damjang_source_grade_seed
    UNION
    SELECT gym_id, label, difficulty_order FROM damjang_fallback_grade_seed
),
deactivated_brand_grades AS (
    UPDATE gym_grades grade
       SET is_active = FALSE,
           updated_at = NOW()
    FROM brand_targets target
    WHERE grade.gym_id = target.gym_id
      AND grade.is_active = TRUE
      AND NOT EXISTS (
          SELECT 1
          FROM grade_scheme scheme
          WHERE scheme.scheme = target.scheme
            AND scheme.label = grade.label
      )
    RETURNING grade.gym_id, grade.label
),
upserted_grades AS (
    INSERT INTO gym_grades (gym_id, label, difficulty_order, is_active)
    SELECT
        seed.gym_id,
        seed.label,
        seed.difficulty_order,
        TRUE
    FROM all_grade_seed seed
    ON CONFLICT (gym_id, label) DO UPDATE
       SET difficulty_order = EXCLUDED.difficulty_order,
           is_active = TRUE,
           updated_at = NOW()
    RETURNING gym_id, label
)
SELECT
    (SELECT COUNT(*) FROM inserted_gyms) AS inserted_gyms,
    (SELECT COUNT(*) FROM existing_gyms) AS existing_gyms,
    (SELECT COUNT(*) FROM upserted_grades) AS upserted_grades,
    (SELECT COUNT(*) FROM deactivated_brand_grades) AS deactivated_brand_grades;
